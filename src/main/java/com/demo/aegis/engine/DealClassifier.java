package com.demo.aegis.engine;

import java.util.Locale;
import java.util.Set;

/**
 * BR-FD-002: a trade is an internal deal (ID) when its counterparty is an iWork portfolio
 * code, or the counterparty label contains BOND(S) and the counterparty type is Internal
 * Party. Everything else is an external deal (ED).
 */
public final class DealClassifier {

    private static final String INTERNAL_PARTY = "Internal Party";

    private DealClassifier() {
    }

    public static String classify(TradeFacts trade, Set<String> portfolioCodes) {
        String counterparty = trade.counterparty();
        if (counterparty == null) {
            return EnrichedTrade.EXTERNAL;
        }
        boolean isPortfolio = portfolioCodes.contains(counterparty);
        boolean isInternalBondBook = counterparty.toUpperCase(Locale.ROOT).contains("BOND")
                && INTERNAL_PARTY.equalsIgnoreCase(trade.counterpartyType());
        return isPortfolio || isInternalBondBook ? EnrichedTrade.INTERNAL : EnrichedTrade.EXTERNAL;
    }
}
