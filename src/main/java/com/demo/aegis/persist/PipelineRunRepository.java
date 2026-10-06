package com.demo.aegis.persist;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class PipelineRunRepository {

    private static final String SELECT_RUN = """
            SELECT id, file_name, status, started_at, ended_at, rows_read, rows_stored, rows_failed, rows_in_scope,
                   internal_deals, external_deals, tags_per_rule
              FROM pipeline_run""";

    private final JdbcTemplate jdbc;
    private final JsonColumns json;

    public PipelineRunRepository(JdbcTemplate jdbc, JsonColumns json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Opens a run in status RUNNING and returns its id. */
    public long create(String fileName) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO pipeline_run (file_name, status) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, fileName);
            statement.setString(2, PipelineRun.STATUS_RUNNING);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    /** Records the outcome of the store stage. */
    public void finish(long id, String status, int rowsRead, int rowsStored, int rowsFailed) {
        jdbc.update("""
                UPDATE pipeline_run
                   SET status = ?, ended_at = CURRENT_TIMESTAMP, rows_read = ?, rows_stored = ?, rows_failed = ?
                 WHERE id = ?""", status, rowsRead, rowsStored, rowsFailed, id);
    }

    /** Records the outcome of the detection stage. */
    public void finishDetection(long id, int rowsInScope, int internalDeals, int externalDeals, Map<String, Integer> tagsPerRule) {
        jdbc.update("""
                UPDATE pipeline_run
                   SET status = ?, ended_at = CURRENT_TIMESTAMP, rows_in_scope = ?, internal_deals = ?, external_deals = ?,
                       tags_per_rule = ?
                 WHERE id = ?""", PipelineRun.STATUS_COMPLETED, rowsInScope, internalDeals, externalDeals,
                json.write(tagsPerRule), id);
    }

    public void setStatus(long id, String status) {
        jdbc.update("UPDATE pipeline_run SET status = ?, ended_at = CURRENT_TIMESTAMP WHERE id = ?", status, id);
    }

    public Optional<PipelineRun> find(long id) {
        return jdbc.query(SELECT_RUN + " WHERE id = ?", this::toRun, id).stream().findFirst();
    }

    /** The most recent runs, newest first. */
    public List<PipelineRun> findRecent(int limit) {
        return jdbc.query(SELECT_RUN + " ORDER BY id DESC LIMIT ?", this::toRun, limit);
    }

    private PipelineRun toRun(ResultSet rs, int rowNumber) throws SQLException {
        Timestamp ended = rs.getTimestamp("ended_at");
        return new PipelineRun(rs.getLong("id"), rs.getString("file_name"), rs.getString("status"),
                rs.getTimestamp("started_at").toLocalDateTime(), ended == null ? null : ended.toLocalDateTime(),
                rs.getInt("rows_read"), rs.getInt("rows_stored"), rs.getInt("rows_failed"), rs.getInt("rows_in_scope"),
                rs.getInt("internal_deals"), rs.getInt("external_deals"), json.countMap(rs.getString("tags_per_rule")));
    }
}
