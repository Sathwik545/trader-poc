package com.demo.aegis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 0 done-criteria: app starts against MariaDB, Flyway migrations run, the three
 * profile tables (trader_profile, portfolio, desk_profile) are seeded, and
 * /actuator/health is UP. Runs against the local MariaDB's aegis_test database.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AegisApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void contextLoadsAndHealthIsUp() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void profileTablesAreSeeded() {
        Integer portfolios = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM portfolio", Integer.class);
        Integer deskProfiles = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM desk_profile", Integer.class);
        Integer traderProfiles = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM trader_profile", Integer.class);

        assertThat(portfolios).isEqualTo(7);
        assertThat(deskProfiles).isEqualTo(4);
        assertThat(traderProfiles).isEqualTo(40);
    }

    @Test
    void pipelineTablesExist() {
        for (String table : new String[] {"trades", "trades_tags", "detection_rule", "pipeline_run", "row_error"}) {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
            assertThat(count).isNotNull();
        }
    }
}
