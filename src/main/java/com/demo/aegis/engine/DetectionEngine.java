package com.demo.aegis.engine;

import com.demo.aegis.config.DetectionProperties;
import com.demo.aegis.ingest.RunNotFoundException;
import com.demo.aegis.persist.JsonColumns;
import com.demo.aegis.persist.PipelineRun;
import com.demo.aegis.persist.PipelineRunRepository;
import com.demo.aegis.persist.StoredTradeRepository;
import com.demo.aegis.persist.TradeTagRepository;
import com.demo.aegis.persist.TradeTagRepository.NewTag;
import com.demo.aegis.rules.DetectionRule;
import com.demo.aegis.rules.ExclusionList;
import com.demo.aegis.rules.RuleContext;
import com.demo.aegis.rules.RuleEngine;
import com.demo.aegis.rules.TagHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Stage two of the pipeline: reads a run's stored trades back, identifies each trade's trader
 * and desk, classes it ID or ED, applies the scope filter, runs the business rules on the
 * in-scope trades and writes one row to trades_tags per rule that fires.
 */
@Service
public class DetectionEngine {

    private static final Logger log = LoggerFactory.getLogger(DetectionEngine.class);
    /** Unknown trader IDs or portfolios listed in a lookup-miss warning. */
    private static final int MAX_MISSES_LISTED = 20;

    private final PipelineRunRepository runs;
    private final StoredTradeRepository storedTrades;
    private final TradeTagRepository tradeTags;
    private final TraderProfileProvider traderProfiles;
    private final PortfolioProvider portfolios;
    private final DeskProfileProvider deskProfiles;
    private final RemoteSessionProvider remoteSessions;
    private final ScopeFilter scopeFilter;
    private final RuleEngine ruleEngine;
    private final JsonColumns json;
    private final TransactionTemplate transaction;
    private final int chunkSize;
    private final ExclusionList exclusionList;
    private final LocalTime rolloverTime;

    public DetectionEngine(PipelineRunRepository runs, StoredTradeRepository storedTrades, TradeTagRepository tradeTags,
                           TraderProfileProvider traderProfiles, PortfolioProvider portfolios, DeskProfileProvider deskProfiles,
                           RemoteSessionProvider remoteSessions, ScopeFilter scopeFilter, RuleEngine ruleEngine,
                           JsonColumns json, TransactionTemplate transaction, DetectionProperties properties) {
        this.runs = runs;
        this.storedTrades = storedTrades;
        this.tradeTags = tradeTags;
        this.traderProfiles = traderProfiles;
        this.portfolios = portfolios;
        this.deskProfiles = deskProfiles;
        this.remoteSessions = remoteSessions;
        this.scopeFilter = scopeFilter;
        this.ruleEngine = ruleEngine;
        this.json = json;
        this.transaction = transaction;
        this.chunkSize = properties.chunkSize();
        this.exclusionList = new ExclusionList(properties.exclusionList());
        this.rolloverTime = properties.rolloverTime();
    }

    /**
     * Runs detection on the trades already stored for the run. Safe to repeat: the run's
     * earlier tags are removed first, so a rule change shows up without a new upload.
     */
    public void detect(long runId) {
        runs.find(runId).orElseThrow(() -> new RunNotFoundException(runId));
        log.info("Run {}: detection started", runId);
        try {
            Tally tally = run(runId);
            runs.finishDetection(runId, tally.inScope, tally.internal, tally.external, tally.tagsPerRule);
            log.info("Run {}: detection finished: {} trades, {} in scope, {} ID, {} ED, tags per rule {}",
                    runId, tally.trades, tally.inScope, tally.internal, tally.external, tally.tagsPerRule);
            tally.warnAboutMisses(runId);
            ruleEngine.drainFailures().forEach((tag, count) ->
                    log.warn("Run {}: rule {} could not be evaluated for {} trades", runId, tag, count));
        } catch (RuntimeException e) {
            runs.setStatus(runId, PipelineRun.STATUS_DETECTION_FAILED);
            log.error("Run {}: detection failed: {}", runId, e.getMessage());
            throw e;
        }
    }

    private Tally run(long runId) {
        // reference data is read once per run and held in memory
        TraderIdentifier traders = new TraderIdentifier(traderProfiles.loadAll());
        DeskIdentifier desks = new DeskIdentifier(portfolios.loadAll(), deskProfiles.loadAll());
        Tally tally = new Tally(ruleEngine.activeRules());

        tradeTags.deleteByRun(runId);
        long afterId = 0;
        while (true) {
            List<TradeFacts> chunk = storedTrades.readChunk(runId, afterId, chunkSize);
            if (chunk.isEmpty()) {
                break;
            }
            afterId = chunk.get(chunk.size() - 1).id();

            List<Outcome> outcomes = chunk.parallelStream().map(trade -> evaluate(trade, traders, desks)).toList();

            List<EnrichedTrade> enriched = new ArrayList<>(outcomes.size());
            List<NewTag> newTags = new ArrayList<>();
            for (Outcome outcome : outcomes) {
                enriched.add(outcome.enriched);
                tally.add(outcome);
                for (TagHit hit : outcome.hits) {
                    newTags.add(new NewTag(outcome.enriched.trade().id(), hit.ruleId(), json.write(hit.details())));
                }
            }
            // one commit per chunk, not one per trade
            transaction.executeWithoutResult(status -> {
                storedTrades.saveClassification(enriched);
                tradeTags.saveAll(runId, newTags);
            });
        }
        return tally;
    }

    private Outcome evaluate(TradeFacts trade, TraderIdentifier traders, DeskIdentifier desks) {
        TraderProfile trader = traders.identify(trade.trader());
        Portfolio portfolio = desks.portfolio(trade.portfolio());
        DeskProfile desk = desks.desk(portfolio);
        String dealClass = DealClassifier.classify(trade, desks.portfolioCodes());
        boolean inScope = scopeFilter.inScope(portfolio);
        EnrichedTrade enriched = new EnrichedTrade(trade, trader, portfolio, desk, dealClass, inScope);

        List<TagHit> hits = inScope
                ? ruleEngine.evaluate(new RuleContext(enriched, exclusionList, rolloverTime, remoteSessions.isRemote(trade)))
                : List.of();
        return new Outcome(enriched, hits);
    }

    private record Outcome(EnrichedTrade enriched, List<TagHit> hits) {
    }

    /** Run totals, built on the calling thread after each chunk. */
    private final class Tally {

        private final Map<String, Integer> tagsPerRule = new LinkedHashMap<>();
        private final Set<String> unknownTraders = new TreeSet<>();
        private final Set<String> unknownPortfolios = new TreeSet<>();
        private int trades;
        private int inScope;
        private int internal;
        private int external;

        private Tally(List<DetectionRule> activeRules) {
            activeRules.forEach(rule -> tagsPerRule.put(rule.tag(), 0));
        }

        private void add(Outcome outcome) {
            EnrichedTrade enriched = outcome.enriched;
            trades++;
            if (enriched.inScope()) {
                inScope++;
            }
            if (EnrichedTrade.INTERNAL.equals(enriched.dealClass())) {
                internal++;
            } else {
                external++;
            }
            outcome.hits.forEach(hit -> tagsPerRule.merge(hit.tag(), 1, Integer::sum));

            String traderId = enriched.trade().trader();
            if (enriched.trader() == null && traderId != null && !exclusionList.matches(traderId)) {
                unknownTraders.add(traderId);
            }
            if (enriched.portfolio() == null && enriched.trade().portfolio() != null) {
                unknownPortfolios.add(enriched.trade().portfolio());
            }
        }

        private void warnAboutMisses(long runId) {
            if (!unknownTraders.isEmpty()) {
                log.warn("Run {}: {} trader IDs have no trader profile: {}", runId, unknownTraders.size(), firstFew(unknownTraders));
            }
            if (!unknownPortfolios.isEmpty()) {
                log.warn("Run {}: {} portfolios are not in iWork: {}", runId, unknownPortfolios.size(), firstFew(unknownPortfolios));
            }
        }

        private List<String> firstFew(Set<String> values) {
            return values.stream().limit(MAX_MISSES_LISTED).toList();
        }
    }
}
