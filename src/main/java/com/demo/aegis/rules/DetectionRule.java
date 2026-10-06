package com.demo.aegis.rules;

import java.util.Map;

/**
 * A business rule as data: when {@code condition} is true for a trade, {@code tag} is added.
 *
 * @param id        detection_rule.id, the tag id used in trades_tags; null before it is saved
 * @param condition SpEL expression over a {@link RuleContext}
 * @param details   name -> SpEL expression, evaluated when the rule fires
 */
public record DetectionRule(Long id, String brRef, String tag, String category, String blotterRef, String condition,
                            String workflowRole, boolean enabled, Map<String, String> details) {
}
