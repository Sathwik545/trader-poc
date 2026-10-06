package com.demo.aegis.persist;

import com.demo.aegis.engine.TraderProfile;
import com.demo.aegis.engine.TraderProfileProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.HashMap;
import java.util.Map;

/** Demo trader profiles from the seeded trader_profile table. */
@Repository
public class JdbcTraderProfileProvider implements TraderProfileProvider {

    private final JdbcTemplate jdbc;
    private final JsonColumns json;

    public JdbcTraderProfileProvider(JdbcTemplate jdbc, JsonColumns json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Map<String, TraderProfile> loadAll() {
        Map<String, TraderProfile> profiles = new HashMap<>();
        jdbc.query("""
                SELECT one_bank_id, name, aegis_role, licensed, pre_rnf_approval, hiring_manager, allowed_portfolios,
                       allowed_fgts, allowed_currencies, effective_date, remote_allowed
                  FROM trader_profile""", rs -> {
            Date effective = rs.getDate("effective_date");
            profiles.put(rs.getString("one_bank_id"), new TraderProfile(rs.getString("one_bank_id"), rs.getString("name"),
                    rs.getString("aegis_role"), rs.getBoolean("licensed"), rs.getString("pre_rnf_approval"),
                    rs.getString("hiring_manager"), json.stringSet(rs.getString("allowed_portfolios")),
                    json.stringSet(rs.getString("allowed_fgts")), json.stringSet(rs.getString("allowed_currencies")),
                    effective == null ? null : effective.toLocalDate(), rs.getBoolean("remote_allowed")));
        });
        return profiles;
    }
}
