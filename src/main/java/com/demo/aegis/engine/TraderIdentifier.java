package com.demo.aegis.engine;

import java.util.Map;

/** Finds a trade's trader: M_TP_TRADER -> trader profile. Holds the profiles in memory for one run. */
public class TraderIdentifier {

    private final Map<String, TraderProfile> byOneBankId;

    public TraderIdentifier(Map<String, TraderProfile> byOneBankId) {
        this.byOneBankId = byOneBankId;
    }

    /** @return the profile, or null if the ID is empty or unknown */
    public TraderProfile identify(String traderId) {
        return traderId == null ? null : byOneBankId.get(traderId);
    }
}
