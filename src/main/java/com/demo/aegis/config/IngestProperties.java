package com.demo.aegis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param batchSize       trades written to the database per batch
 * @param maxErrorsShown  row errors returned in a run summary (all of them stay in row_error)
 */
@ConfigurationProperties(prefix = "aegis.ingest")
public record IngestProperties(@DefaultValue("500") int batchSize,
                               @DefaultValue("50") int maxErrorsShown) {
}
