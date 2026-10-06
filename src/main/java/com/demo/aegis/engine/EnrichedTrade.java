package com.demo.aegis.engine;

/**
 * A trade with everything detection looked up for it.
 *
 * @param trader    null when the 1BankID is unknown
 * @param portfolio null when the portfolio is not in iWork
 * @param desk      null when the portfolio maps to no desk
 * @param dealClass ID or ED (BR-FD-002)
 * @param inScope   BR-FD-003 result; out-of-scope trades skip the rules
 */
public record EnrichedTrade(TradeFacts trade, TraderProfile trader, Portfolio portfolio, DeskProfile desk,
                            String dealClass, boolean inScope) {

    public static final String INTERNAL = "ID";
    public static final String EXTERNAL = "ED";
}
