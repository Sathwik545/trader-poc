package com.demo.aegis.api;

import com.demo.aegis.ingest.RunSummary;
import com.demo.aegis.persist.FlaggedTrade;
import com.demo.aegis.persist.TagCount;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * Phase 2 done-criteria on the 10,000-row file: all 11 tags fire, 2,520 internal deals,
 * BR-UC1-001 is 301, and BR-UC1-010 / 011 are the after-hours and off-premises trades left
 * after the scope filter, the exclusion list and the role exemption. The exact figures were
 * cross-checked against an independent model of the rules. Runs against aegis_test and
 * removes the run it creates.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DetectionIntegrationTest {

    private static final Path SAMPLE_FILE = Path.of("docs", "ada_deals_synthetic_10000.csv");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    private RunSummary summary;

    @BeforeAll
    void uploadSampleFile() {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(SAMPLE_FILE));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<RunSummary> response = rest.postForEntity("/api/v1/trades/upload", new HttpEntity<>(body, headers), RunSummary.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        summary = response.getBody();
    }

    @AfterAll
    void removeRun() {
        if (summary != null) {
            jdbc.update("DELETE FROM trades_tags WHERE run_id = ?", summary.runId());
            jdbc.update("DELETE FROM trades WHERE run_id = ?", summary.runId());
            jdbc.update("DELETE FROM row_error WHERE run_id = ?", summary.runId());
            jdbc.update("DELETE FROM pipeline_run WHERE id = ?", summary.runId());
        }
    }

    @Test
    void everyTradeIsClassedInternalOrExternal() {
        assertThat(summary.status()).isEqualTo("COMPLETED");
        assertThat(summary.rowsStored()).isEqualTo(10_000);
        assertThat(summary.internalDeals()).isEqualTo(2_520);
        assertThat(summary.externalDeals()).isEqualTo(7_480);
        assertThat(count("SELECT COUNT(*) FROM trades WHERE run_id = ? AND deal_class = 'ID'")).isEqualTo(2_520);
        assertThat(count("SELECT COUNT(*) FROM trades WHERE run_id = ? AND deal_class = 'ED'")).isEqualTo(7_480);
    }

    @Test
    void outOfScopeTradesAreStoredButNotTagged() {
        assertThat(summary.rowsInScope()).isEqualTo(8_624);
        assertThat(count("SELECT COUNT(*) FROM trades WHERE run_id = ? AND NOT in_scope")).isEqualTo(1_376);
        assertThat(count("SELECT COUNT(*) FROM trades WHERE run_id = ? AND NOT in_scope AND m_tp_pfolio <> 'DEMOHK IN FI SY'")).isZero();
        assertThat(count("""
                SELECT COUNT(*) FROM trades_tags tt JOIN trades t ON t.id = tt.trade_id
                 WHERE tt.run_id = ? AND NOT t.in_scope""")).isZero();
    }

    @Test
    void everyRuleFiresWithTheExpectedCount() {
        assertThat(summary.tagsPerRule()).containsExactly(
                entry("DQDM001_PORTFOLIO_NOT_FOUND", 301),
                entry("DQDM002_FGT_NOT_FOUND", 187),
                entry("DQTP001_STAFF_ROLE_NOT_FOUND", 680),
                entry("DQTP002_TRADER_NOT_FOUND", 259),
                entry("TCTM001_PORTFOLIO_NOT_ALLOWED", 303),
                entry("TCTM002_FGT_NOT_ALLOWED", 21),
                entry("TCTM003_CURR_NOT_ALLOWED", 13),
                entry("TCTM004_TRADE_WO_LICENSE", 403),
                entry("TCTM005_TRADE_BEFORE_EFF_DATE", 210),
                entry("TCTM006_LATE_TRADE_INSERTION", 627),
                entry("TCTM007_REMOTE_ACCESS", 406));
        assertThat(summary.tagsPerRule().get("TCTM006_LATE_TRADE_INSERTION")).isLessThanOrEqualTo(812);
        assertThat(summary.tagsPerRule().get("TCTM007_REMOTE_ACCESS")).isLessThanOrEqualTo(528);

        int stored = count("SELECT COUNT(*) FROM trades_tags WHERE run_id = ?");
        assertThat(stored).isEqualTo(summary.tagsPerRule().values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void tagCountsEndpointListsEveryRuleInBrRefOrder() {
        TagCount[] counts = rest.getForObject("/api/v1/runs/" + summary.runId() + "/tags", TagCount[].class);

        assertThat(counts).hasSize(11);
        assertThat(Arrays.stream(counts).map(TagCount::brRef)).isSorted().startsWith("BR-UC1-001").endsWith("BR-UC1-011");
        assertThat(counts).allSatisfy(c -> assertThat(c.count()).isPositive());
        assertThat(counts[0].tag()).isEqualTo("DQDM001_PORTFOLIO_NOT_FOUND");
        assertThat(counts[0].count()).isEqualTo(301);
        assertThat(counts[0].workflowRole()).isEqualTo("DESK SUPPORT");
    }

    @Test
    void flaggedTradesCarryTheirTagsAndDetails() {
        FlaggedTrade.Page page = rest.getForObject(
                "/api/v1/runs/" + summary.runId() + "/trades?tag=DQDM001_PORTFOLIO_NOT_FOUND&size=20", FlaggedTrade.Page.class);

        assertThat(page.total()).isEqualTo(301);
        assertThat(page.trades()).hasSize(20);
        assertThat(page.trades()).allSatisfy(trade -> {
            assertThat(trade.portfolio()).isIn("DEMOHK ORPHAN 01", "DEMOSG TEST PF");
            FlaggedTrade.Tag tag = trade.tags().stream().filter(t -> t.tag().equals("DQDM001_PORTFOLIO_NOT_FOUND")).findFirst().orElseThrow();
            assertThat(tag.brRef()).isEqualTo("BR-UC1-001");
            assertThat(tag.details().get("portfolio").asText()).isEqualTo(trade.portfolio());
        });

        FlaggedTrade.Page all = rest.getForObject("/api/v1/runs/" + summary.runId() + "/trades?page=1&size=10", FlaggedTrade.Page.class);
        assertThat(all.page()).isEqualTo(1);
        assertThat(all.trades()).hasSize(10);
        assertThat(all.total()).isEqualTo(count("SELECT COUNT(DISTINCT trade_id) FROM trades_tags WHERE run_id = ?"));
    }

    @Test
    void mandateTagDetailsShowWhatFailed() {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT t.m_tp_trader, t.m_tp_pfolio, tt.details
                  FROM trades_tags tt
                  JOIN detection_rule r ON r.id = tt.tag_id
                  JOIN trades t ON t.id = tt.trade_id
                 WHERE tt.run_id = ? AND r.tag = 'TCTM001_PORTFOLIO_NOT_ALLOWED' AND t.m_tp_trader = 'TRD0005'
                 LIMIT 1""", summary.runId());

        assertThat(row.get("m_tp_pfolio")).isEqualTo("DEMOHK FXD CK");
        assertThat((String) row.get("details")).contains("\"portfolio\":\"DEMOHK FXD CK\"").contains("allowedPortfolios");
    }

    @Test
    void detectionCanBeReRunWithoutDuplicatingTags() {
        int before = count("SELECT COUNT(*) FROM trades_tags WHERE run_id = ?");

        ResponseEntity<RunSummary> again = rest.postForEntity("/api/v1/runs/" + summary.runId() + "/detect", null, RunSummary.class);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(again.getBody().tagsPerRule()).isEqualTo(summary.tagsPerRule());
        assertThat(count("SELECT COUNT(*) FROM trades_tags WHERE run_id = ?")).isEqualTo(before);
    }

    @Test
    void rulesEndpointListsTheElevenRules() {
        List<?> rules = rest.getForObject("/api/v1/rules", List.class);

        assertThat(rules).hasSize(11);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class, summary.runId());
    }
}
