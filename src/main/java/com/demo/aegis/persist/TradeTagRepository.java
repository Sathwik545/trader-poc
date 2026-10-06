package com.demo.aegis.persist;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class TradeTagRepository {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final JsonColumns json;

    public TradeTagRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate named, JsonColumns json) {
        this.jdbc = jdbc;
        this.named = named;
        this.json = json;
    }

    /** A tag to store: the trade, the rule that fired, and the details as JSON text. */
    public record NewTag(long tradeId, long ruleId, String detailsJson) {
    }

    public void deleteByRun(long runId) {
        jdbc.update("DELETE FROM trades_tags WHERE run_id = ?", runId);
    }

    public void saveAll(long runId, List<NewTag> tags) {
        if (tags.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("INSERT INTO trades_tags (trade_id, tag_id, run_id, details) VALUES (?, ?, ?, ?)", tags, tags.size(),
                (statement, tag) -> {
                    statement.setLong(1, tag.tradeId());
                    statement.setLong(2, tag.ruleId());
                    statement.setLong(3, runId);
                    statement.setString(4, tag.detailsJson());
                });
    }

    /** One line per enabled rule in BR Ref order, including rules that tagged nothing. */
    public List<TagCount> countsByRun(long runId) {
        return jdbc.query("""
                SELECT r.br_ref, r.tag, r.category, r.blotter_ref, r.workflow_role, COUNT(tt.id) AS tagged
                  FROM detection_rule r
                  LEFT JOIN trades_tags tt ON tt.tag_id = r.id AND tt.run_id = ?
                 WHERE r.enabled OR tt.id IS NOT NULL
                 GROUP BY r.id, r.br_ref, r.tag, r.category, r.blotter_ref, r.workflow_role
                 ORDER BY r.br_ref, r.tag""",
                (rs, n) -> new TagCount(rs.getString("br_ref"), rs.getString("tag"), rs.getString("category"),
                        rs.getString("blotter_ref"), rs.getString("workflow_role"), rs.getInt("tagged")), runId);
    }

    /**
     * Flagged trades of a run in trade order, each with all of its tags.
     *
     * @param tag  only trades carrying this tag, or null for every flagged trade
     * @param page zero-based
     */
    public FlaggedTrade.Page flaggedTrades(long runId, String tag, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("runId", runId).addValue("tag", tag, Types.VARCHAR).addValue("limit", size).addValue("offset", (long) page * size);
        String filter = """
                  FROM trades_tags tt
                  JOIN detection_rule r ON r.id = tt.tag_id
                 WHERE tt.run_id = :runId AND (:tag IS NULL OR r.tag = :tag)""";

        Long total = named.queryForObject("SELECT COUNT(DISTINCT tt.trade_id)" + filter, params, Long.class);
        List<Long> tradeIds = named.queryForList(
                "SELECT DISTINCT tt.trade_id" + filter + " ORDER BY tt.trade_id LIMIT :limit OFFSET :offset", params, Long.class);
        if (tradeIds.isEmpty()) {
            return new FlaggedTrade.Page(page, size, total == null ? 0 : total, List.of());
        }
        params.addValue("tradeIds", tradeIds);

        Map<Long, List<FlaggedTrade.Tag>> tagsByTrade = new HashMap<>();
        named.query("""
                SELECT tt.trade_id, r.br_ref, r.tag, tt.details
                  FROM trades_tags tt
                  JOIN detection_rule r ON r.id = tt.tag_id
                 WHERE tt.run_id = :runId AND tt.trade_id IN (:tradeIds)
                 ORDER BY r.br_ref, r.tag""", params, rs -> {
            tagsByTrade.computeIfAbsent(rs.getLong("trade_id"), id -> new ArrayList<>())
                    .add(new FlaggedTrade.Tag(rs.getString("br_ref"), rs.getString("tag"), json.tree(rs.getString("details"))));
        });

        List<FlaggedTrade> trades = named.query("""
                SELECT id, m_nb, trade_date, m_tp_trader, m_tp_pfolio, m_tp_cntrp, deal_class, m_trn_fmly, m_trn_grp,
                       m_trn_type, m_tp_nomcur, m_tp_timsys, off_premises
                  FROM trades
                 WHERE run_id = :runId AND id IN (:tradeIds)
                 ORDER BY id""", params, (rs, n) -> {
            long dealNumber = rs.getLong("m_nb");
            boolean hasDealNumber = !rs.wasNull();
            return new FlaggedTrade(rs.getLong("id"), hasDealNumber ? dealNumber : null, rs.getDate("trade_date").toLocalDate(),
                    rs.getString("m_tp_trader"), rs.getString("m_tp_pfolio"), rs.getString("m_tp_cntrp"),
                    rs.getString("deal_class"), rs.getString("m_trn_fmly"), rs.getString("m_trn_grp"),
                    rs.getString("m_trn_type"), rs.getString("m_tp_nomcur"), rs.getString("m_tp_timsys"),
                    rs.getString("off_premises"), tagsByTrade.getOrDefault(rs.getLong("id"), List.of()));
        });
        return new FlaggedTrade.Page(page, size, total == null ? 0 : total, trades);
    }
}
