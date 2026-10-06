package com.demo.aegis.persist;

import com.demo.aegis.engine.DeskProfile;
import com.demo.aegis.engine.DeskProfileProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Demo desk profiles from the seeded desk_profile table. */
@Repository
public class JdbcDeskProfileProvider implements DeskProfileProvider {

    private final JdbcTemplate jdbc;
    private final JsonColumns json;

    public JdbcDeskProfileProvider(JdbcTemplate jdbc, JsonColumns json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<DeskProfile> loadAll() {
        return jdbc.query("SELECT desk, sub_desk, book, allowed_fgts, desk_head, desk_delegate, desk_support FROM desk_profile",
                (rs, n) -> new DeskProfile(rs.getString("desk"), rs.getString("sub_desk"), rs.getString("book"),
                        json.stringSet(rs.getString("allowed_fgts")), rs.getString("desk_head"),
                        rs.getString("desk_delegate"), rs.getString("desk_support")));
    }
}
