package com.demo.aegis.persist;

import com.demo.aegis.engine.EnrichedTrade;
import com.demo.aegis.engine.TradeFacts;
import com.demo.aegis.ingest.FieldConverters;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Reads a run's stored trades back for detection and writes the classification onto them. */
@Repository
public class StoredTradeRepository {

    private static final String CORE = "CORE";
    private static final String FX_FAMILY = "CURR";

    private final JdbcTemplate jdbc;

    public StoredTradeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The next {@code limit} trades of the run with an id above {@code afterId}, in id order. */
    public List<TradeFacts> readChunk(long runId, long afterId, int limit) {
        return jdbc.query("""
                SELECT id, trade_date, m_nb, m_tp_trader, m_tp_pfolio, m_tp_cntrp, m_ctp_type,
                       m_trn_fmly, m_trn_grp, m_trn_type,
                       core_trn_fmly, core_trn_grp, core_trn_type, core_trade_type, core_trade_subtype, trade_categ_type,
                       m_tp_nomcur, m_brw_nomu1, m_brw_nomu2, m_tp_timsys, m_tp_dtesys, off_premises
                  FROM trades
                 WHERE run_id = ? AND id > ?
                 ORDER BY id
                 LIMIT ?""", (rs, n) -> toFacts(rs), runId, afterId, limit);
    }

    public void saveClassification(List<EnrichedTrade> trades) {
        jdbc.batchUpdate("UPDATE trades SET deal_class = ?, in_scope = ? WHERE id = ? AND trade_date = ?", trades, trades.size(),
                (statement, enriched) -> {
                    statement.setString(1, enriched.dealClass());
                    statement.setBoolean(2, enriched.inScope());
                    statement.setLong(3, enriched.trade().id());
                    statement.setDate(4, Date.valueOf(enriched.trade().tradeDate()));
                });
    }

    private static TradeFacts toFacts(ResultSet rs) throws SQLException {
        String source = rs.getString("trade_categ_type");
        String family = rs.getString("m_trn_fmly");
        // BR-UC1-002: Murex uses family/group/type; CORE adds trade type and sub type
        String fgt = CORE.equalsIgnoreCase(source)
                ? key(rs.getString("core_trn_fmly"), rs.getString("core_trn_grp"), rs.getString("core_trn_type"),
                        rs.getString("core_trade_type"), rs.getString("core_trade_subtype"))
                : key(family, rs.getString("m_trn_grp"), rs.getString("m_trn_type"));

        String nominalCurrency = rs.getString("m_tp_nomcur");
        List<String> currencies = new ArrayList<>();
        addDistinct(currencies, nominalCurrency);
        if (FX_FAMILY.equalsIgnoreCase(family)) {
            addDistinct(currencies, rs.getString("m_brw_nomu1"));
            addDistinct(currencies, rs.getString("m_brw_nomu2"));
        }

        long insertionMicros = rs.getLong("m_tp_dtesys");
        boolean hasInsertionDate = !rs.wasNull();
        String insertionTime = rs.getString("m_tp_timsys");
        long dealNumber = rs.getLong("m_nb");
        boolean hasDealNumber = !rs.wasNull();

        return new TradeFacts(rs.getLong("id"), rs.getDate("trade_date").toLocalDate(), hasDealNumber ? dealNumber : null,
                rs.getString("m_tp_trader"), rs.getString("m_tp_pfolio"), rs.getString("m_tp_cntrp"), rs.getString("m_ctp_type"),
                fgt, List.copyOf(currencies), nominalCurrency,
                hasInsertionDate ? FieldConverters.epochMicrosToUtcDate(insertionMicros) : null,
                insertionTime == null ? null : LocalTime.parse(insertionTime), rs.getString("off_premises"),
                source);
    }

    /** Joins the non-empty parts with '/', for example IRD/IRS or CURR/FXD/SWLEG; null if all are empty. */
    private static String key(String... parts) {
        String joined = Stream.of(parts).filter(Objects::nonNull).filter(p -> !p.isBlank()).collect(Collectors.joining("/"));
        return joined.isEmpty() ? null : joined;
    }

    private static void addDistinct(List<String> values, String value) {
        if (value != null && !value.isBlank() && !values.contains(value)) {
            values.add(value);
        }
    }
}
