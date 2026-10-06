package com.demo.aegis.rules;

import java.util.Map;

/**
 * A rule that fired for a trade.
 *
 * @param ruleId  detection_rule.id, stored as trades_tags.tag_id
 * @param details the values that made the rule fire
 */
public record TagHit(long ruleId, String tag, Map<String, Object> details) {
}
