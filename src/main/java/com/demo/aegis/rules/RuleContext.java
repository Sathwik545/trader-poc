package com.demo.aegis.rules;

import com.demo.aegis.engine.DeskProfile;
import com.demo.aegis.engine.EnrichedTrade;
import com.demo.aegis.engine.Portfolio;
import com.demo.aegis.engine.TradeFacts;
import com.demo.aegis.engine.TraderProfile;

import java.time.LocalTime;
import java.util.Collection;
import java.util.Set;

/**
 * What a rule expression sees for one trade: the trade, its trader, portfolio and desk, and
 * the shared checks several rules use. rules.yaml lists the names available.
 */
public class RuleContext {

    private final EnrichedTrade enriched;
    private final ExclusionList exclusionList;
    private final LocalTime rolloverTime;
    private final boolean remoteSession;

    public RuleContext(EnrichedTrade enriched, ExclusionList exclusionList, LocalTime rolloverTime, boolean remoteSession) {
        this.enriched = enriched;
        this.exclusionList = exclusionList;
        this.rolloverTime = rolloverTime;
        this.remoteSession = remoteSession;
    }

    public TradeFacts getTrade() {
        return enriched.trade();
    }

    public TraderProfile getTrader() {
        return enriched.trader();
    }

    public Portfolio getPortfolio() {
        return enriched.portfolio();
    }

    public DeskProfile getDesk() {
        return enriched.desk();
    }

    public LocalTime getRolloverTime() {
        return rolloverTime;
    }

    public boolean excluded(String traderId) {
        return exclusionList.matches(traderId);
    }

    public boolean exempt() {
        return getTrader() != null && getTrader().exempt();
    }

    public boolean hasMandate() {
        return getTrader() != null && getTrader().hasMandate();
    }

    public boolean mandateHas(String kind, String value) {
        return value != null && mandate(kind).contains(value);
    }

    /** True when every value is in the mandate; an empty collection is trivially allowed. */
    public boolean mandateHasAll(String kind, Collection<String> values) {
        return values == null || mandate(kind).containsAll(values);
    }

    public boolean deskAllows(String fgt) {
        return getDesk() != null && fgt != null && getDesk().allowedFgts().contains(fgt);
    }

    public boolean beforeEffectiveDate() {
        TraderProfile trader = getTrader();
        return trader != null && trader.effectiveDate() != null && getTrade().insertionDate() != null
                && getTrade().insertionDate().isBefore(trader.effectiveDate());
    }

    public boolean afterRollover() {
        return getTrade().insertionTime() != null && !getTrade().insertionTime().isBefore(rolloverTime);
    }

    public boolean remoteSession() {
        return remoteSession;
    }

    private Set<String> mandate(String kind) {
        TraderProfile trader = getTrader();
        if (trader == null) {
            return Set.of();
        }
        return switch (kind) {
            case "portfolio" -> trader.allowedPortfolios();
            case "fgt" -> trader.allowedFgts();
            case "currency" -> trader.allowedCurrencies();
            default -> throw new IllegalArgumentException("Unknown mandate kind '" + kind + "'; use portfolio, fgt or currency");
        };
    }
}
