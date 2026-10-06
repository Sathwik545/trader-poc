package com.demo.aegis.api;

import com.demo.aegis.ingest.RunSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1 done-criteria: uploading the 10,000-row file stores 10,000 rows in trades with no
 * lost rows, spread across the Jul, Aug and Sep 2026 partitions. Runs against the local
 * MariaDB's aegis_test database and removes the runs it creates.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TradeUploadIntegrationTest {

    private static final Path SAMPLE_FILE = Path.of("docs", "ada_deals_synthetic_10000.csv");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> createdRuns = new ArrayList<>();

    @AfterEach
    void removeCreatedRuns() {
        for (Long runId : createdRuns) {
            jdbc.update("DELETE FROM trades_tags WHERE run_id = ?", runId);
            jdbc.update("DELETE FROM trades WHERE run_id = ?", runId);
            jdbc.update("DELETE FROM row_error WHERE run_id = ?", runId);
            jdbc.update("DELETE FROM pipeline_run WHERE id = ?", runId);
        }
    }

    @Test
    void sampleFileIsStoredInFullAcrossThreeMonthlyPartitions() {
        RunSummary summary = uploadOk(new FileSystemResource(SAMPLE_FILE));

        assertThat(summary.status()).isEqualTo("COMPLETED");
        assertThat(summary.rowsRead()).isEqualTo(10_000);
        assertThat(summary.rowsStored()).isEqualTo(10_000);
        assertThat(summary.rowsFailed()).isZero();
        assertThat(summary.errors()).isEmpty();

        long runId = summary.runId();
        assertThat(count("SELECT COUNT(*) FROM trades WHERE run_id = ?", runId)).isEqualTo(10_000);
        int july = count("SELECT COUNT(*) FROM trades PARTITION (p202607) WHERE run_id = ?", runId);
        int august = count("SELECT COUNT(*) FROM trades PARTITION (p202608) WHERE run_id = ?", runId);
        int september = count("SELECT COUNT(*) FROM trades PARTITION (p202609) WHERE run_id = ?", runId);
        assertThat(july).isPositive();
        assertThat(august).isPositive();
        assertThat(september).isPositive();
        assertThat(july + august + september).isEqualTo(10_000);

        // spot checks that values land typed, not just counted
        assertThat(count("SELECT COUNT(*) FROM trades WHERE run_id = ? AND m_tp_timsys >= '19:00:00'", runId)).isEqualTo(812);
        assertThat(count("SELECT COUNT(*) FROM trades WHERE run_id = ? AND off_premises = 'Y'", runId)).isEqualTo(528);
        assertThat(count("SELECT COUNT(*) FROM trades WHERE run_id = ? AND m_tp_trader LIKE 'UNKNOWN%'", runId)).isEqualTo(302);
        assertThat(count("SELECT COUNT(*) FROM trades WHERE run_id = ? AND m_eq_po = x'0000000000'", runId)).isEqualTo(10_000);
        assertThat(count("SELECT COUNT(*) FROM trades WHERE run_id = ? AND trade_date <> DATE_ADD('1970-01-01', INTERVAL FLOOR(m_tp_dtetrn / 86400000000) DAY)", runId)).isZero();

        ResponseEntity<RunSummary> fetched = rest.getForEntity("/api/v1/runs/" + runId, RunSummary.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().rowsStored()).isEqualTo(10_000);
    }

    @Test
    void badRowIsRecordedAndTheRestOfTheFileIsStored() throws IOException {
        String header;
        String goodRow;
        try (BufferedReader reader = Files.newBufferedReader(SAMPLE_FILE, StandardCharsets.UTF_8)) {
            header = reader.readLine();
            goodRow = reader.readLine();
        }
        String badNumber = "not-a-number" + goodRow.substring(goodRow.indexOf(','));
        String tooShort = "337000003,IRS Vanilla";
        String csv = String.join("\n", header, goodRow, badNumber, tooShort, goodRow) + "\n";

        RunSummary summary = uploadOk(named(csv, "mixed.csv"));

        assertThat(summary.rowsRead()).isEqualTo(4);
        assertThat(summary.rowsStored()).isEqualTo(2);
        assertThat(summary.rowsFailed()).isEqualTo(2);
        assertThat(summary.errors()).hasSize(2);
        assertThat(summary.errors().get(0).rowNumber()).isEqualTo(2);
        assertThat(summary.errors().get(0).message()).contains("M_NB");
        assertThat(summary.errors().get(1).rowNumber()).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM trades WHERE run_id = ?", summary.runId())).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM row_error WHERE run_id = ?", summary.runId())).isEqualTo(2);
    }

    @Test
    void rowTheDatabaseRejectsIsRecordedAndItsBatchIsStillStored() throws IOException {
        String header;
        String goodRow;
        try (BufferedReader reader = Files.newBufferedReader(SAMPLE_FILE, StandardCharsets.UTF_8)) {
            header = reader.readLine();
            goodRow = reader.readLine();
        }
        // parses fine, but is longer than the 100 characters the column holds
        String tooLong = goodRow.replaceFirst("IRS Vanilla", "X".repeat(150));
        String csv = String.join("\n", header, goodRow, tooLong, goodRow) + "\n";

        RunSummary summary = uploadOk(named(csv, "too-long.csv"));

        assertThat(summary.rowsRead()).isEqualTo(3);
        assertThat(summary.rowsStored()).isEqualTo(2);
        assertThat(summary.rowsFailed()).isEqualTo(1);
        assertThat(summary.errors()).singleElement().satisfies(error -> {
            assertThat(error.rowNumber()).isEqualTo(2);
            assertThat(error.message()).startsWith("database rejected the row");
        });
        assertThat(count("SELECT COUNT(*) FROM trades WHERE run_id = ?", summary.runId())).isEqualTo(2);
    }

    @Test
    void fileWithoutTheTradeColumnsIsRejected() {
        ResponseEntity<ApiError> response = upload(named("a,b,c\n1,2,3\n", "wrong.csv"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("missing 121 of the 121 expected columns");
        Long runId = jdbc.queryForObject("SELECT MAX(id) FROM pipeline_run WHERE file_name = 'wrong.csv'", Long.class);
        createdRuns.add(runId);
        assertThat(jdbc.queryForObject("SELECT status FROM pipeline_run WHERE id = ?", String.class, runId)).isEqualTo("FAILED");
    }

    @Test
    void emptyFileIsRejected() {
        ResponseEntity<ApiError> response = upload(named("", "empty.csv"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().path()).isEqualTo("/api/v1/trades/upload");
    }

    @Test
    void unknownRunIsNotFound() {
        ResponseEntity<ApiError> response = rest.getForEntity("/api/v1/runs/999999999", ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).contains("999999999");
    }

    private RunSummary uploadOk(Resource file) {
        ResponseEntity<RunSummary> response = upload(file, RunSummary.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        createdRuns.add(response.getBody().runId());
        return response.getBody();
    }

    private <T> ResponseEntity<T> upload(Resource file, Class<T> responseType) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.postForEntity("/api/v1/trades/upload", new HttpEntity<>(body, headers), responseType);
    }

    private static Resource named(String content, String fileName) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }

    private int count(String sql, long runId) {
        return jdbc.queryForObject(sql, Integer.class, runId);
    }
}
