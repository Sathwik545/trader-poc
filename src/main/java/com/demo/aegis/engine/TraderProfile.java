package com.demo.aegis.engine;

import java.time.LocalDate;
import java.util.Set;

/** A trader as AEGIS knows them: staff role, licence, and the trader mandate. */
public record TraderProfile(String oneBankId, String name, String role, boolean licensed, String preRnfApproval,
                            String hiringManager, Set<String> allowedPortfolios, Set<String> allowedFgts,
                            Set<String> allowedCurrencies, LocalDate effectiveDate, boolean remoteAllowed) {

    public static final String ROLE_DESK_SUPPORT = "DESK SUPPORT";
    public static final String ROLE_SALES = "SALES";

    /** True when the trader appears in a trader mandate at all. */
    public boolean hasMandate() {
        return !allowedPortfolios.isEmpty() || !allowedFgts.isEmpty() || !allowedCurrencies.isEmpty();
    }

    /** DESK SUPPORT and SALES are exempt from the trader-mandate rules. */
    public boolean exempt() {
        return ROLE_DESK_SUPPORT.equalsIgnoreCase(role) || ROLE_SALES.equalsIgnoreCase(role);
    }
}
