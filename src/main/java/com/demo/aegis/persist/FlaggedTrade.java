package com.demo.aegis.persist;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.List;

/** A trade that at least one rule tagged, with its tags. */
public record FlaggedTrade(long tradeId, Long dealNumber, LocalDate tradeDate, String trader, String portfolio,
                           String counterparty, String dealClass, String family, String group, String type,
                           String currency, String insertionTime, String offPremises, List<Tag> tags) {

    /** @param details the values that made the rule fire */
    public record Tag(String brRef, String tag, JsonNode details) {
    }

    /** One page of flagged trades; {@code total} counts every flagged trade matching the filter. */
    public record Page(int page, int size, long total, List<FlaggedTrade> trades) {
    }
}
