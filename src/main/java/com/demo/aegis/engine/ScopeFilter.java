package com.demo.aegis.engine;

import com.demo.aegis.config.DetectionProperties;
import com.demo.aegis.config.DetectionProperties.ScopeCriteria;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * BR-FD-003: decides whether a trade's portfolio is in scope for AEGIS. A trade whose
 * portfolio is not in iWork stays in scope, so BR-UC1-001 can flag it.
 */
@Component
public class ScopeFilter {

    private final List<ScopeCriteria> criteria;

    public ScopeFilter(DetectionProperties properties) {
        this.criteria = properties.scope();
    }

    public boolean inScope(Portfolio portfolio) {
        if (portfolio == null) {
            return true;
        }
        return criteria.stream().anyMatch(c -> meets(portfolio, c));
    }

    private static boolean meets(Portfolio portfolio, ScopeCriteria c) {
        return c.country() != null && c.country().equalsIgnoreCase(portfolio.country())
                && containsIgnoreCase(c.businessUnits(), portfolio.businessUnit())
                && !containsIgnoreCase(c.excludedClassifications(), portfolio.classification())
                && !containsIgnoreCase(c.excludedMxEntities(), portfolio.mxEntity());
    }

    private static boolean containsIgnoreCase(List<String> values, String value) {
        return value != null && values.stream().anyMatch(value::equalsIgnoreCase);
    }
}
