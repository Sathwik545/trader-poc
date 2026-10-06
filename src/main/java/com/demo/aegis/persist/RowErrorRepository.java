package com.demo.aegis.persist;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class RowErrorRepository {

    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final JdbcTemplate jdbc;

    public RowErrorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void saveAll(long runId, List<RowError> errors) {
        if (errors.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("INSERT INTO row_error (run_id, `row_number`, message) VALUES (?, ?, ?)", errors, errors.size(),
                (statement, error) -> {
                    statement.setLong(1, runId);
                    statement.setLong(2, error.rowNumber());
                    statement.setString(3, truncate(error.message()));
                });
    }

    /** The first {@code limit} errors of a run, in file order. */
    public List<RowError> findByRun(long runId, int limit) {
        return jdbc.query("SELECT `row_number`, message FROM row_error WHERE run_id = ? ORDER BY `row_number`, id LIMIT ?",
                (rs, n) -> new RowError(rs.getLong(1), rs.getString(2)), runId, limit);
    }

    private static String truncate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() <= MAX_MESSAGE_LENGTH ? message : message.substring(0, MAX_MESSAGE_LENGTH);
    }
}
