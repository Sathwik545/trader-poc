package com.demo.aegis.persist;

import com.demo.aegis.rules.DetectionRule;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public class DetectionRuleRepository {

    private final JdbcTemplate jdbc;
    private final JsonColumns json;

    public DetectionRuleRepository(JdbcTemplate jdbc, JsonColumns json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Inserts the rule, or updates the row with the same tag so its id (the tag id) is kept. */
    public void upsert(DetectionRule rule, int sortOrder) {
        jdbc.update("""
                INSERT INTO detection_rule
                    (br_ref, tag, category, blotter_ref, condition_spel, workflow_role, enabled, sort_order, details_spel)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    br_ref = VALUES(br_ref), category = VALUES(category), blotter_ref = VALUES(blotter_ref),
                    condition_spel = VALUES(condition_spel), workflow_role = VALUES(workflow_role),
                    enabled = VALUES(enabled), sort_order = VALUES(sort_order), details_spel = VALUES(details_spel)""",
                rule.brRef(), rule.tag(), rule.category(), rule.blotterRef(), rule.condition(), rule.workflowRole(),
                rule.enabled(), sortOrder, json.write(rule.details()));
    }

    public void disableAllExcept(Set<String> tags) {
        List<String> stored = jdbc.queryForList("SELECT tag FROM detection_rule WHERE enabled", String.class);
        for (String tag : stored) {
            if (!tags.contains(tag)) {
                jdbc.update("UPDATE detection_rule SET enabled = FALSE WHERE tag = ?", tag);
            }
        }
    }

    /** Every rule, in BR Ref order. */
    public List<DetectionRule> findAll() {
        return jdbc.query("""
                SELECT id, br_ref, tag, category, blotter_ref, condition_spel, workflow_role, enabled, details_spel
                  FROM detection_rule ORDER BY br_ref, tag""",
                (rs, n) -> new DetectionRule(rs.getLong("id"), rs.getString("br_ref"), rs.getString("tag"),
                        rs.getString("category"), rs.getString("blotter_ref"), rs.getString("condition_spel"),
                        rs.getString("workflow_role"), rs.getBoolean("enabled"), json.stringMap(rs.getString("details_spel"))));
    }
}
