package com.demo.aegis.rules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Evaluates the detection rules against a trade. Each rule's SpEL expressions are parsed
 * once, when the rules are installed; evaluation is thread-safe. Expressions get read-only
 * access to the {@link RuleContext} and its methods, nothing else.
 */
@Component
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);
    /** Evaluation failures logged per rule before they are only counted. */
    private static final int MAX_FAILURES_LOGGED = 5;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final Map<String, AtomicInteger> failures = new ConcurrentHashMap<>();
    private volatile List<CompiledRule> rules = List.of();

    /** Parses a rule's expressions; used to validate rules before they are saved. */
    public CompiledRule compile(DetectionRule rule) {
        try {
            Expression condition = parser.parseExpression(rule.condition());
            Map<String, Expression> details = new LinkedHashMap<>();
            for (Map.Entry<String, String> detail : rule.details().entrySet()) {
                details.put(detail.getKey(), parser.parseExpression(detail.getValue()));
            }
            return new CompiledRule(rule, condition, details);
        } catch (ParseException | IllegalArgumentException e) {
            throw new InvalidRulesException("Rule " + rule.brRef() + " (" + rule.tag() + ") has an expression that does not parse: "
                    + e.getMessage(), e);
        }
    }

    /** Replaces the active rules. Disabled rules are dropped; order is by BR Ref. */
    public void install(List<DetectionRule> saved) {
        this.rules = saved.stream().filter(DetectionRule::enabled).map(this::compile).toList();
        failures.clear();
        log.info("Rule engine loaded {} enabled rules: {}", rules.size(), rules.stream().map(r -> r.rule().brRef()).toList());
    }

    public List<DetectionRule> activeRules() {
        return rules.stream().map(CompiledRule::rule).toList();
    }

    /** Evaluates every active rule; a trade can collect several tags. */
    public List<TagHit> evaluate(RuleContext context) {
        EvaluationContext evaluation = SimpleEvaluationContext.forReadOnlyDataBinding()
                .withInstanceMethods()
                .withRootObject(context)
                .build();
        List<TagHit> hits = new ArrayList<>();
        for (CompiledRule compiled : rules) {
            DetectionRule rule = compiled.rule();
            try {
                if (Boolean.TRUE.equals(compiled.condition().getValue(evaluation, Boolean.class))) {
                    Map<String, Object> details = new LinkedHashMap<>();
                    for (Map.Entry<String, Expression> detail : compiled.details().entrySet()) {
                        details.put(detail.getKey(), detail.getValue().getValue(evaluation));
                    }
                    hits.add(new TagHit(rule.id(), rule.tag(), details));
                }
            } catch (RuntimeException e) {
                // a broken rule must not stop the run or the other rules
                int count = failures.computeIfAbsent(rule.tag(), tag -> new AtomicInteger()).incrementAndGet();
                if (count <= MAX_FAILURES_LOGGED) {
                    log.warn("Rule {} could not be evaluated for trade id {}: {}", rule.brRef(), context.getTrade().id(), e.getMessage());
                }
            }
        }
        return hits;
    }

    /** Evaluation failures per tag since the rules were last installed or this was last called. */
    public Map<String, Integer> drainFailures() {
        Map<String, Integer> drained = new LinkedHashMap<>();
        failures.forEach((tag, count) -> drained.put(tag, count.getAndSet(0)));
        drained.values().removeIf(count -> count == 0);
        return drained;
    }

    public record CompiledRule(DetectionRule rule, Expression condition, Map<String, Expression> details) {
    }
}
