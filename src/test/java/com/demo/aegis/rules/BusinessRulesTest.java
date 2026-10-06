package com.demo.aegis.rules;

import com.demo.aegis.engine.DeskProfile;
import com.demo.aegis.engine.EnrichedTrade;
import com.demo.aegis.engine.Portfolio;
import com.demo.aegis.engine.TradeFacts;
import com.demo.aegis.engine.TraderProfile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One firing and at least one non-firing case per BR Ref, evaluated against the shipped
 * rules.yaml, so a change to a condition in that file is checked here. No database.
 */
class BusinessRulesTest {

    private static final String PORTFOLIO_NOT_FOUND = "DQDM001_PORTFOLIO_NOT_FOUND";
    private static final String FGT_NOT_FOUND = "DQDM002_FGT_NOT_FOUND";
    private static final String STAFF_ROLE_NOT_FOUND = "DQTP001_STAFF_ROLE_NOT_FOUND";
    private static final String TRADER_NOT_FOUND = "DQTP002_TRADER_NOT_FOUND";
    private static final String PORTFOLIO_NOT_ALLOWED = "TCTM001_PORTFOLIO_NOT_ALLOWED";
    private static final String FGT_NOT_ALLOWED = "TCTM002_FGT_NOT_ALLOWED";
    private static final String CURR_NOT_ALLOWED = "TCTM003_CURR_NOT_ALLOWED";
    private static final String TRADE_WO_LICENSE = "TCTM004_TRADE_WO_LICENSE";
    private static final String TRADE_BEFORE_EFF_DATE = "TCTM005_TRADE_BEFORE_EFF_DATE";
    private static final String LATE_TRADE_INSERTION = "TCTM006_LATE_TRADE_INSERTION";
    private static final String REMOTE_ACCESS = "TCTM007_REMOTE_ACCESS";

    private static final ExclusionList EXCLUSIONS = new ExclusionList(List.of("DBSINTF", "SETL%", "BATCHUSER", "SYSTEM%"));
    private static final LocalTime ROLLOVER = LocalTime.of(19, 0);

    private static RuleEngine engine;

    @BeforeAll
    static void loadShippedRules() {
        List<DetectionRule> fromFile = RuleLoader.read(new ClassPathResource("rules.yaml"));
        List<DetectionRule> withIds = new ArrayList<>();
        for (DetectionRule rule : fromFile) {
            withIds.add(new DetectionRule((long) withIds.size() + 1, rule.brRef(), rule.tag(), rule.category(),
                    rule.blotterRef(), rule.condition(), rule.workflowRole(), rule.enabled(), rule.details()));
        }
        engine = new RuleEngine();
        engine.install(withIds);
    }

    @Test
    void shippedFileHasTheElevenRulesInBrRefOrder() {
        assertThat(engine.activeRules()).extracting(DetectionRule::brRef).containsExactly(
                "BR-UC1-001", "BR-UC1-002", "BR-UC1-003", "BR-UC1-004", "BR-UC1-005", "BR-UC1-006",
                "BR-UC1-007", "BR-UC1-008", "BR-UC1-009", "BR-UC1-010", "BR-UC1-011");
    }

    @Test
    void cleanTradeGetsNoTags() {
        assertThat(new Scenario().tags()).isEmpty();
        assertThat(engine.drainFailures()).isEmpty();
    }

    @Test
    void br001_portfolioMappedToNoDesk() {
        assertThat(new Scenario().unmappedPortfolio().tags()).contains(PORTFOLIO_NOT_FOUND);
        assertThat(new Scenario().tags()).doesNotContain(PORTFOLIO_NOT_FOUND);
    }

    @Test
    void br002_productNotInDeskProfile() {
        assertThat(new Scenario().fgt("EQD/FUT").tags()).containsExactly(FGT_NOT_FOUND);
        // with no desk there is nothing to check the product against; 001 reports that instead
        assertThat(new Scenario().fgt("EQD/FUT").unmappedPortfolio().tags()).doesNotContain(FGT_NOT_FOUND);
    }

    @Test
    void br003_traderHasNoAegisRole() {
        assertThat(new Scenario().unknownTrader("UNKNOWN01").tags()).contains(STAFF_ROLE_NOT_FOUND);
        assertThat(new Scenario().role(null).tags()).containsExactly(STAFF_ROLE_NOT_FOUND);
        assertThat(new Scenario().unknownTrader("SETL_HK01").tags()).doesNotContain(STAFF_ROLE_NOT_FOUND);
        assertThat(new Scenario().unknownTrader("BATCHUSER").tags()).doesNotContain(STAFF_ROLE_NOT_FOUND);
    }

    @Test
    void br004_traderNotInAnyMandate() {
        assertThat(new Scenario().unknownTrader("UNKNOWN01").tags()).contains(TRADER_NOT_FOUND);
        assertThat(new Scenario().noMandate().tags()).containsExactly(TRADER_NOT_FOUND);
        assertThat(new Scenario().unknownTrader("SYSTEM_EOD").tags()).doesNotContain(TRADER_NOT_FOUND);
        assertThat(new Scenario().noMandate().role("DESK SUPPORT").tags()).doesNotContain(TRADER_NOT_FOUND);
    }

    @Test
    void br005_portfolioNotInTraderMandate() {
        assertThat(new Scenario().mandatePortfolios("OTHER PF").tags()).containsExactly(PORTFOLIO_NOT_ALLOWED);
        assertThat(new Scenario().mandatePortfolios("OTHER PF").role("SALES").tags()).doesNotContain(PORTFOLIO_NOT_ALLOWED);
        // a trader with no mandate at all is reported by 004, not by the mandate rules
        assertThat(new Scenario().noMandate().tags()).doesNotContain(PORTFOLIO_NOT_ALLOWED);
    }

    @Test
    void br006_productNotInTraderMandate() {
        assertThat(new Scenario().mandateFgts("CURR/FXD/FXD").tags()).containsExactly(FGT_NOT_ALLOWED);
        assertThat(new Scenario().mandateFgts("CURR/FXD/FXD").role("DESK SUPPORT").tags()).doesNotContain(FGT_NOT_ALLOWED);
    }

    @Test
    void br007_currencyNotInTraderMandate() {
        assertThat(new Scenario().currencies("USD", "HKD").mandateCurrencies("USD").tags()).containsExactly(CURR_NOT_ALLOWED);
        assertThat(new Scenario().currencies("USD", "HKD").mandateCurrencies("USD", "HKD").tags()).doesNotContain(CURR_NOT_ALLOWED);
        assertThat(new Scenario().currencies("SGD").mandateCurrencies("USD").role("SALES").tags()).doesNotContain(CURR_NOT_ALLOWED);
    }

    @Test
    void br008_traderWithoutLicence() {
        assertThat(new Scenario().licence(false, "N").tags()).containsExactly(TRADE_WO_LICENSE);
        assertThat(new Scenario().licence(false, "Y").tags()).doesNotContain(TRADE_WO_LICENSE);
        assertThat(new Scenario().licence(true, "N").tags()).doesNotContain(TRADE_WO_LICENSE);
        assertThat(new Scenario().licence(false, "N").role("DESK SUPPORT").tags()).doesNotContain(TRADE_WO_LICENSE);
    }

    @Test
    void br009_tradeBeforeMandateEffectiveDate() {
        assertThat(new Scenario().effectiveDate(LocalDate.of(2026, 8, 15)).tags()).containsExactly(TRADE_BEFORE_EFF_DATE);
        assertThat(new Scenario().effectiveDate(Scenario.INSERTION_DATE).tags()).doesNotContain(TRADE_BEFORE_EFF_DATE);
        assertThat(new Scenario().effectiveDate(LocalDate.of(2026, 8, 15)).role("SALES").tags()).doesNotContain(TRADE_BEFORE_EFF_DATE);
    }

    @Test
    void br010_tradeInsertedAfterRollover() {
        assertThat(new Scenario().insertedAt(LocalTime.of(19, 0, 0)).tags()).containsExactly(LATE_TRADE_INSERTION);
        assertThat(new Scenario().insertedAt(LocalTime.of(23, 59, 59)).tags()).containsExactly(LATE_TRADE_INSERTION);
        assertThat(new Scenario().insertedAt(LocalTime.of(18, 59, 59)).tags()).doesNotContain(LATE_TRADE_INSERTION);
        assertThat(new Scenario().insertedAt(LocalTime.of(22, 0)).role("DESK SUPPORT").tags()).doesNotContain(LATE_TRADE_INSERTION);
        assertThat(new Scenario().insertedAt(LocalTime.of(22, 0)).unknownTrader("BATCHUSER").tags()).doesNotContain(LATE_TRADE_INSERTION);
    }

    @Test
    void br011_tradeBookedOverRemoteAccess() {
        assertThat(new Scenario().remote().tags()).containsExactly(REMOTE_ACCESS);
        assertThat(new Scenario().tags()).doesNotContain(REMOTE_ACCESS);
        assertThat(new Scenario().remote().role("SALES").tags()).doesNotContain(REMOTE_ACCESS);
    }

    @Test
    void aTradeCanCollectSeveralTagsWithDetails() {
        Scenario scenario = new Scenario().unmappedPortfolio().insertedAt(LocalTime.of(20, 15)).remote();

        assertThat(scenario.tags()).containsExactly(PORTFOLIO_NOT_FOUND, PORTFOLIO_NOT_ALLOWED, LATE_TRADE_INSERTION, REMOTE_ACCESS);
        TagHit late = scenario.hits().stream().filter(hit -> hit.tag().equals(LATE_TRADE_INSERTION)).findFirst().orElseThrow();
        assertThat(late.details()).containsEntry("trader", "TRD0001")
                .containsEntry("insertionTime", LocalTime.of(20, 15))
                .containsEntry("rolloverTime", ROLLOVER);
    }

    /** A trade that breaks no rule, with switches to break one thing at a time. */
    private static final class Scenario {

        static final LocalDate INSERTION_DATE = LocalDate.of(2026, 7, 10);
        private static final String MAPPED_PORTFOLIO = "DEMOHK IRD DY";

        private String traderId = "TRD0001";
        private boolean knownTrader = true;
        private String role = "TRADER";
        private boolean licensed = true;
        private String preRnf = "Y";
        private Set<String> mandatePortfolios = Set.of(MAPPED_PORTFOLIO);
        private Set<String> mandateFgts = Set.of("IRD/IRS", "EQD/FUT", "CURR/FXD/FXD");
        private Set<String> mandateCurrencies = Set.of("USD", "HKD", "SGD");
        private LocalDate effectiveDate = LocalDate.of(2026, 1, 1);
        private String portfolioCode = MAPPED_PORTFOLIO;
        private boolean mapped = true;
        private String fgt = "IRD/IRS";
        private List<String> currencies = List.of("USD");
        private LocalTime insertionTime = LocalTime.of(10, 0);
        private boolean remote;

        Scenario unknownTrader(String id) {
            traderId = id;
            knownTrader = false;
            return this;
        }

        Scenario role(String newRole) {
            role = newRole;
            return this;
        }

        Scenario licence(boolean isLicensed, String preRnfApproval) {
            licensed = isLicensed;
            preRnf = preRnfApproval;
            return this;
        }

        Scenario noMandate() {
            mandatePortfolios = Set.of();
            mandateFgts = Set.of();
            mandateCurrencies = Set.of();
            return this;
        }

        Scenario mandatePortfolios(String... codes) {
            mandatePortfolios = Set.of(codes);
            return this;
        }

        Scenario mandateFgts(String... fgts) {
            mandateFgts = Set.of(fgts);
            return this;
        }

        Scenario mandateCurrencies(String... codes) {
            mandateCurrencies = Set.of(codes);
            return this;
        }

        Scenario effectiveDate(LocalDate date) {
            effectiveDate = date;
            return this;
        }

        Scenario unmappedPortfolio() {
            portfolioCode = "DEMOHK ORPHAN 01";
            mapped = false;
            return this;
        }

        Scenario fgt(String key) {
            fgt = key;
            return this;
        }

        Scenario currencies(String... codes) {
            currencies = List.of(codes);
            return this;
        }

        Scenario insertedAt(LocalTime time) {
            insertionTime = time;
            return this;
        }

        Scenario remote() {
            remote = true;
            return this;
        }

        List<TagHit> hits() {
            TradeFacts trade = new TradeFacts(1L, INSERTION_DATE, 337000001L, traderId, portfolioCode, "ALPHA BANK LDN",
                    "External", fgt, currencies, currencies.get(0), INSERTION_DATE, insertionTime, remote ? "Y" : null, "MUREX");
            TraderProfile trader = knownTrader
                    ? new TraderProfile(traderId, "Trader", role, licensed, preRnf, "Manager A", mandatePortfolios,
                            mandateFgts, mandateCurrencies, effectiveDate, true)
                    : null;
            Portfolio portfolio = mapped
                    ? new Portfolio(portfolioCode, "Hong Kong", "GFM", "TRADING", "MXDEMOHK", "BOOK", "Desk", "Sub", "Owner", "Reviewer")
                    : null;
            DeskProfile desk = mapped
                    ? new DeskProfile("Desk", "Sub", "TRADING", Set.of("IRD/IRS", "CURR/FXD/FXD"), "Head", "Delegate", "Support")
                    : null;
            EnrichedTrade enriched = new EnrichedTrade(trade, trader, portfolio, desk, EnrichedTrade.EXTERNAL, true);
            return engine.evaluate(new RuleContext(enriched, EXCLUSIONS, ROLLOVER, remote));
        }

        List<String> tags() {
            return hits().stream().map(TagHit::tag).collect(Collectors.toList());
        }
    }

    @Test
    void brokenExpressionIsRejectedWhenCompiled() {
        DetectionRule broken = new DetectionRule(99L, "BR-X", "X", null, null, "desk == ", null, true, Map.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RuleEngine().compile(broken))
                .isInstanceOf(InvalidRulesException.class).hasMessageContaining("BR-X");
    }
}
