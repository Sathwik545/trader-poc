package com.demo.aegis.engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds a trade's desk: M_TP_PFOLIO -> iWork portfolio -> desk profile. Holds the portfolios
 * and desks in memory for one run.
 */
public class DeskIdentifier {

    private final Map<String, Portfolio> portfolios;
    private final Map<String, DeskProfile> desks = new HashMap<>();

    public DeskIdentifier(Map<String, Portfolio> portfolios, List<DeskProfile> deskProfiles) {
        this.portfolios = portfolios;
        for (DeskProfile desk : deskProfiles) {
            desks.put(key(desk.desk(), desk.subDesk()), desk);
        }
    }

    /** @return the portfolio, or null if the code is empty or not in iWork */
    public Portfolio portfolio(String portfolioCode) {
        return portfolioCode == null ? null : portfolios.get(portfolioCode);
    }

    /** @return the desk the portfolio belongs to, or null if there is none */
    public DeskProfile desk(Portfolio portfolio) {
        return portfolio == null ? null : desks.get(key(portfolio.aegisDesk(), portfolio.aegisSubdesk()));
    }

    /** Every iWork portfolio code; BR-FD-002 compares counterparties against these. */
    public Set<String> portfolioCodes() {
        return portfolios.keySet();
    }

    private static String key(String desk, String subDesk) {
        return desk + '\u0000' + subDesk;
    }
}
