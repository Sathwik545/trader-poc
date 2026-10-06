package com.demo.aegis.persist;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * A row of pipeline_run: one upload and what happened to it.
 *
 * @param tagsPerRule tag -> number of trades tagged, in BR Ref order; empty until detection has run
 */
public record PipelineRun(long id, String fileName, String status, LocalDateTime startedAt, LocalDateTime endedAt,
                          int rowsRead, int rowsStored, int rowsFailed, int rowsInScope, int internalDeals,
                          int externalDeals, Map<String, Integer> tagsPerRule) {

    public static final String STATUS_RUNNING = "RUNNING";
    /** Every readable row is in the trades table; detection has not run on it yet. */
    public static final String STATUS_STORED = "STORED";
    /** Trades are stored and detection has run. */
    public static final String STATUS_COMPLETED = "COMPLETED";
    /** The file could not be stored. */
    public static final String STATUS_FAILED = "FAILED";
    /** Trades are stored but detection broke; it can be re-run. */
    public static final String STATUS_DETECTION_FAILED = "DETECTION_FAILED";
}
