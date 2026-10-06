package com.demo.aegis.api;

import com.demo.aegis.ingest.RunSummary;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 done-criterion: a rule changed in the YAML file takes effect after
 * POST /rules/reload and POST /runs/{id}/detect, with no rebuild or restart. The app is
 * pointed at a copy of rules.yaml in a temp file, which the test edits.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RuleReloadIntegrationTest {

    private static final Path SHIPPED_RULES = Path.of("src", "main", "resources", "rules.yaml");
    private static final Path SAMPLE_FILE = Path.of("docs", "ada_deals_synthetic_10000.csv");
    private static final String LATE_TAG = "TCTM006_LATE_TRADE_INSERTION";
    private static final String LATE_CONDITION = "!excluded(trade.trader) && !exempt() && afterRollover()";
    private static final Path RULES_FILE = copyOfShippedRules();

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    private Long runId;

    @DynamicPropertySource
    static void useEditableRulesFile(DynamicPropertyRegistry registry) {
        registry.add("aegis.detection.rules-locations", () -> RULES_FILE.toUri().toString());
    }

    @AfterAll
    void restoreRulesAndRemoveRun() throws IOException {
        Files.copy(SHIPPED_RULES, RULES_FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        rest.postForEntity("/api/v1/rules/reload", null, String.class);
        if (runId != null) {
            jdbc.update("DELETE FROM trades_tags WHERE run_id = ?", runId);
            jdbc.update("DELETE FROM trades WHERE run_id = ?", runId);
            jdbc.update("DELETE FROM row_error WHERE run_id = ?", runId);
            jdbc.update("DELETE FROM pipeline_run WHERE id = ?", runId);
        }
        Files.deleteIfExists(RULES_FILE);
    }

    @Test
    void changedRuleTakesEffectAfterReloadAndDetect() throws IOException {
        RunSummary first = upload(firstRowsOfSample(600));
        runId = first.runId();
        int lateBefore = first.tagsPerRule().get(LATE_TAG);
        assertThat(lateBefore).isPositive();
        int otherTagsBefore = totalTags(first) - lateBefore;

        // a bad expression is refused and the rules in force are untouched
        writeRules(shippedRules().replace(LATE_CONDITION, "afterRollover( &&"));
        ResponseEntity<ApiError> refused = rest.postForEntity("/api/v1/rules/reload", null, ApiError.class);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().message()).contains("BR-UC1-010");
        assertThat(detect().tagsPerRule().get(LATE_TAG)).isEqualTo(lateBefore);

        // tighten the rule in the file: late now means 22:00 or later
        writeRules(shippedRules().replace(LATE_CONDITION,
                "!excluded(trade.trader) && !exempt() && trade.insertionTime != null && trade.insertionTime.hour >= 22"));
        assertThat(rest.postForEntity("/api/v1/rules/reload", null, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        RunSummary second = detect();

        int lateAfter = second.tagsPerRule().get(LATE_TAG);
        assertThat(lateAfter).isPositive().isLessThan(lateBefore);
        assertThat(lateAfter).isEqualTo(jdbc.queryForObject("""
                SELECT COUNT(*) FROM trades_tags tt
                  JOIN detection_rule r ON r.id = tt.tag_id
                  JOIN trades t ON t.id = tt.trade_id
                 WHERE tt.run_id = ? AND r.tag = ? AND t.m_tp_timsys >= '22:00:00'""", Integer.class, runId, LATE_TAG));
        assertThat(totalTags(second) - lateAfter).isEqualTo(otherTagsBefore);

        // a rule removed from the file is switched off
        writeRules(withoutRule(shippedRules(), "BR-UC1-011"));
        rest.postForEntity("/api/v1/rules/reload", null, String.class);
        RunSummary third = detect();
        assertThat(third.tagsPerRule()).doesNotContainKey("TCTM007_REMOTE_ACCESS").hasSize(10);
    }

    private RunSummary upload(String csv) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "reload-test.csv";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<RunSummary> response = rest.postForEntity("/api/v1/trades/upload", new HttpEntity<>(body, headers), RunSummary.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private RunSummary detect() {
        ResponseEntity<RunSummary> response = rest.postForEntity("/api/v1/runs/" + runId + "/detect", null, RunSummary.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static int totalTags(RunSummary summary) {
        return summary.tagsPerRule().values().stream().mapToInt(Integer::intValue).sum();
    }

    private static String firstRowsOfSample(int rows) throws IOException {
        try (Stream<String> lines = Files.lines(SAMPLE_FILE, StandardCharsets.UTF_8)) {
            return lines.limit(rows + 1L).collect(Collectors.joining("\n", "", "\n"));
        }
    }

    private static String shippedRules() throws IOException {
        return Files.readString(SHIPPED_RULES, StandardCharsets.UTF_8);
    }

    private static void writeRules(String yaml) throws IOException {
        Files.writeString(RULES_FILE, yaml, StandardCharsets.UTF_8);
    }

    /** Drops one "- brRef: ..." block from the rules list. */
    private static String withoutRule(String yaml, String brRef) {
        List<String> blocks = List.of(yaml.split("(?m)^(?=  - brRef:)"));
        return blocks.stream().filter(block -> !block.startsWith("  - brRef: " + brRef)).collect(Collectors.joining());
    }

    private static Path copyOfShippedRules() {
        try {
            Path copy = Files.createTempFile("aegis-rules-", ".yaml");
            Files.copy(SHIPPED_RULES, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return copy;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
