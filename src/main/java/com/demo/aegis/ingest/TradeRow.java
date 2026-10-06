package com.demo.aegis.ingest;

import java.time.LocalDate;

/**
 * One parsed trade, ready to store.
 *
 * @param rowNumber 1-based data row in the source (the header is not counted)
 * @param tradeDate UTC date of M_TP_DTETRN, the partition key of the trades table
 * @param values    typed values in {@link TradeSchema#columns()} order; null for an empty cell
 */
public record TradeRow(long rowNumber, LocalDate tradeDate, Object[] values) {
}
