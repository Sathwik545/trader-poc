package com.demo.aegis.api;

import com.demo.aegis.ingest.RunSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 4: the demo page is served at /, and the two endpoints only it uses work. */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoScreenIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    private Long runId;

    @AfterEach
    void removeRun() {
        if (runId != null) {
            jdbc.update("DELETE FROM trades_tags WHERE run_id = ?", runId);
            jdbc.update("DELETE FROM trades WHERE run_id = ?", runId);
            jdbc.update("DELETE FROM row_error WHERE run_id = ?", runId);
            jdbc.update("DELETE FROM pipeline_run WHERE id = ?", runId);
        }
    }

    @Test
    void demoPageIsServedAtTheRoot() {
        ResponseEntity<String> page = rest.getForEntity("/", String.class);

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getHeaders().getContentType().toString()).startsWith("text/html");
        assertThat(page.getBody()).contains("<title>AEGIS detection engine demo</title>")
                .contains("/trades/sample").contains("/trades/upload").contains("/rules/reload");
    }

    @Test
    void sampleFileRunsThroughThePipelineAndShowsUpInRecentRuns() {
        ResponseEntity<RunSummary> response = rest.postForEntity("/api/v1/trades/sample", null, RunSummary.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RunSummary summary = response.getBody();
        runId = summary.runId();
        assertThat(summary.fileName()).isEqualTo("ada_deals_synthetic_10000.csv");
        assertThat(summary.status()).isEqualTo("COMPLETED");
        assertThat(summary.rowsStored()).isEqualTo(10_000);
        assertThat(summary.internalDeals()).isEqualTo(2_520);
        assertThat(summary.tagsPerRule()).hasSize(11).containsEntry("DQDM001_PORTFOLIO_NOT_FOUND", 301);

        RunSummary[] recent = rest.getForObject("/api/v1/runs?limit=5", RunSummary[].class);
        assertThat(recent).isNotEmpty();
        assertThat(recent[0].runId()).as("newest first").isEqualTo(runId);
        assertThat(recent[0].rowsStored()).isEqualTo(10_000);
    }

    @Test
    void unknownPageIsNotFoundWithTheStandardErrorBody() {
        ResponseEntity<ApiError> response = rest.getForEntity("/api/v1/nothing-here", ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo(404);
    }
}
