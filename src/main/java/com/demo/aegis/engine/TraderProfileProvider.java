package com.demo.aegis.engine;

import java.util.Map;

/** Source of trader profiles. Demo: the trader_profile table. Later: AEGIS staff roles and trader mandate. */
public interface TraderProfileProvider {

    /** Every trader profile, keyed by 1BankID. */
    Map<String, TraderProfile> loadAll();
}
