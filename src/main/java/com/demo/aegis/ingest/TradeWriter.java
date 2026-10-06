package com.demo.aegis.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/** Batch-inserts parsed trades into the partitioned trades table. */
@Component
public class TradeWriter {

    private static final Logger log = LoggerFactory.getLogger(TradeWriter.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final String insertSql;

    public TradeWriter(JdbcTemplate jdbc, TransactionTemplate transaction, TradeSchema schema) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        String columns = schema.columns().stream().map(c -> "`" + c.dbName() + "`").collect(Collectors.joining(", "));
        String placeholders = schema.columns().stream().map(c -> "?").collect(Collectors.joining(", "));
        this.insertSql = "INSERT INTO trades (run_id, trade_date, " + columns + ") VALUES (?, ?, " + placeholders + ")";
    }

    /**
     * Stores a batch in one transaction. If the database rejects the batch, it is rolled back
     * and retried row by row, so one bad row costs only itself.
     *
     * @param onRejected called with each row the database refused and the reason
     * @return number of rows stored
     */
    public int write(long runId, List<TradeRow> batch, BiConsumer<TradeRow, String> onRejected) {
        if (batch.isEmpty()) {
            return 0;
        }
        try {
            transaction.executeWithoutResult(status ->
                    jdbc.batchUpdate(insertSql, batch, batch.size(), (statement, row) -> bind(statement, runId, row)));
            return batch.size();
        } catch (DataAccessException batchFailure) {
            log.warn("Run {}: batch of {} rows rejected, retrying row by row: {}", runId, batch.size(),
                    batchFailure.getMostSpecificCause().getMessage());
        }
        int stored = 0;
        for (TradeRow row : batch) {
            try {
                jdbc.update(insertSql, statement -> bind(statement, runId, row));
                stored++;
            } catch (DataAccessException rowFailure) {
                onRejected.accept(row, "database rejected the row: " + rowFailure.getMostSpecificCause().getMessage());
            }
        }
        return stored;
    }

    private static void bind(PreparedStatement statement, long runId, TradeRow row) throws SQLException {
        statement.setLong(1, runId);
        statement.setDate(2, Date.valueOf(row.tradeDate()));
        Object[] values = row.values();
        for (int i = 0; i < values.length; i++) {
            statement.setObject(i + 3, values[i]);
        }
    }
}
