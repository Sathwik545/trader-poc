package com.demo.aegis.persist;

import com.demo.aegis.engine.Portfolio;
import com.demo.aegis.engine.PortfolioProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

/** Demo iWork portfolios from the seeded portfolio table. */
@Repository
public class JdbcPortfolioProvider implements PortfolioProvider {

    private final JdbcTemplate jdbc;

    public JdbcPortfolioProvider(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, Portfolio> loadAll() {
        Map<String, Portfolio> portfolios = new HashMap<>();
        jdbc.query("""
                SELECT portfolio_code, country, business_unit, classification, mx_entity, risk_book, aegis_desk,
                       aegis_subdesk, owner, reviewer
                  FROM portfolio""", rs -> {
            portfolios.put(rs.getString("portfolio_code"), new Portfolio(rs.getString("portfolio_code"),
                    rs.getString("country"), rs.getString("business_unit"), rs.getString("classification"),
                    rs.getString("mx_entity"), rs.getString("risk_book"), rs.getString("aegis_desk"),
                    rs.getString("aegis_subdesk"), rs.getString("owner"), rs.getString("reviewer")));
        });
        return portfolios;
    }
}
