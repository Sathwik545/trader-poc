package com.demo.aegis.engine;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * The fields of a stored trade that detection uses, under the names the rules refer to.
 *
 * @param trader          M_TP_TRADER, taken as the 1BankID
 * @param portfolio       M_TP_PFOLIO
 * @param counterparty    M_TP_CNTRP
 * @param fgt             family/group/type key, for example CURR/FXD/SWLEG or IRD/IRS
 * @param currencies      every currency the trade deals in (both legs for FX)
 * @param insertionDate   UTC date of M_TP_DTESYS
 * @param insertionTime   M_TP_TIMSYS
 * @param source          MUREX or CORE (TRADE_CATEG_TYPE)
 */
public record TradeFacts(long id, LocalDate tradeDate, Long dealNumber, String trader, String portfolio,
                         String counterparty, String counterpartyType, String fgt, List<String> currencies,
                         String nominalCurrency, LocalDate insertionDate, LocalTime insertionTime,
                         String offPremises, String source) {
}
