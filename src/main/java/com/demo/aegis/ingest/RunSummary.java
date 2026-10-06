package com.demo.aegis.ingest;

import com.demo.aegis.persist.PipelineRun;
import com.demo.aegis.persist.RowError;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * What a run did. rowsRead = rowsStored + rowsFailed, and rowsStored = internalDeals +
 * externalDeals once detection has run.
 *
 * @param rowsInScope stored trades that passed the BR-FD-003 scope filter and went through the rules
 * @param tagsPerRule tag -> number of trades tagged, in BR Ref order
 * @param errors      the first skipped rows (capped); rowsFailed is the full count
 */
public record RunSummary(long runId, String fileName, String status, LocalDateTime startedAt, LocalDateTime endedAt,
                         int rowsRead, int rowsStored, int rowsFailed, int rowsInScope, int internalDeals,
                         int externalDeals, Map<String, Integer> tagsPerRule, List<RowError> errors) {

    public static RunSummary of(PipelineRun run, List<RowError> errors) {
        return new RunSummary(run.id(), run.fileName(), run.status(), run.startedAt(), run.endedAt(),
                run.rowsRead(), run.rowsStored(), run.rowsFailed(), run.rowsInScope(), run.internalDeals(),
                run.externalDeals(), run.tagsPerRule(), errors);
    }
}
