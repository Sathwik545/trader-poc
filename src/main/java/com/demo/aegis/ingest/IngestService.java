package com.demo.aegis.ingest;

import com.demo.aegis.config.IngestProperties;
import com.demo.aegis.ingest.TradeSource.TradeRowHandler;
import com.demo.aegis.persist.PipelineRun;
import com.demo.aegis.persist.PipelineRunRepository;
import com.demo.aegis.persist.RowError;
import com.demo.aegis.persist.RowErrorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage one of the pipeline: read a trade source and store every row in the trades table
 * under a new run. Rows that cannot be parsed or stored go to row_error; they never stop
 * the file.
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);
    /** Skipped rows logged individually per run; the rest are only counted. */
    private static final int MAX_ROW_WARNINGS = 20;

    private final PipelineRunRepository runs;
    private final RowErrorRepository rowErrors;
    private final TradeWriter writer;
    private final IngestProperties properties;

    public IngestService(PipelineRunRepository runs, RowErrorRepository rowErrors, TradeWriter writer,
                         IngestProperties properties) {
        this.runs = runs;
        this.rowErrors = rowErrors;
        this.writer = writer;
        this.properties = properties;
    }

    /**
     * @throws InvalidTradeFileException if the source as a whole is unusable; the run is then
     *                                   marked FAILED and keeps whatever was stored before the failure
     */
    public RunSummary ingest(String fileName, TradeSource source) {
        long runId = runs.create(fileName);
        log.info("Run {} started: storing trades from '{}'", runId, fileName);
        Batcher batcher = new Batcher(runId);
        try {
            source.read(batcher);
            batcher.flush();
        } catch (RuntimeException e) {
            batcher.flushErrorsQuietly();
            runs.finish(runId, PipelineRun.STATUS_FAILED, batcher.read, batcher.stored, batcher.failed);
            log.error("Run {} failed after {} rows read, {} stored: {}", runId, batcher.read, batcher.stored, e.getMessage());
            throw e;
        }
        runs.finish(runId, PipelineRun.STATUS_STORED, batcher.read, batcher.stored, batcher.failed);
        log.info("Run {} stored: {} rows read, {} stored, {} failed", runId, batcher.read, batcher.stored, batcher.failed);
        return summary(runId);
    }

    public RunSummary summary(long runId) {
        PipelineRun run = runs.find(runId).orElseThrow(() -> new RunNotFoundException(runId));
        return RunSummary.of(run, rowErrors.findByRun(runId, properties.maxErrorsShown()));
    }

    /** The most recent runs, newest first, without their row errors. */
    public List<RunSummary> recentRuns(int limit) {
        return runs.findRecent(limit).stream().map(run -> RunSummary.of(run, List.of())).toList();
    }

    /** Collects rows and row errors for one run and writes them in batches. */
    private final class Batcher implements TradeRowHandler {

        private final long runId;
        private final List<TradeRow> rows = new ArrayList<>();
        private final List<RowError> errors = new ArrayList<>();
        private int read;
        private int stored;
        private int failed;

        private Batcher(long runId) {
            this.runId = runId;
        }

        @Override
        public void onRow(TradeRow row) {
            read++;
            rows.add(row);
            if (rows.size() >= properties.batchSize()) {
                flush();
            }
        }

        @Override
        public void onRowError(long rowNumber, String message) {
            read++;
            reject(rowNumber, message);
        }

        private void reject(long rowNumber, String message) {
            failed++;
            if (failed <= MAX_ROW_WARNINGS) {
                log.warn("Run {}: row {} skipped: {}", runId, rowNumber, message);
            } else if (failed == MAX_ROW_WARNINGS + 1) {
                log.warn("Run {}: more rows skipped; see row_error for the full list", runId);
            }
            errors.add(new RowError(rowNumber, message));
            if (errors.size() >= properties.batchSize()) {
                flushErrors();
            }
        }

        private void flush() {
            stored += writer.write(runId, rows, (row, reason) -> reject(row.rowNumber(), reason));
            rows.clear();
            flushErrors();
        }

        private void flushErrors() {
            rowErrors.saveAll(runId, errors);
            errors.clear();
        }

        private void flushErrorsQuietly() {
            try {
                flushErrors();
            } catch (RuntimeException e) {
                log.warn("Run {}: could not save {} row errors: {}", runId, errors.size(), e.getMessage());
            }
        }
    }
}
