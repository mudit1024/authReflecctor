package com.authreflector.oidc;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Roles a user has in one specific app (table client_roles, migration V4). */
@Repository
public class ClientRoles {

    static final String DEFAULT_ROLE = "USER";

    private final JdbcTemplate jdbc;

    public ClientRoles(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * USER for everyone, plus whatever was granted for this app; sorted so tokens are deterministic.
     * A plain ArrayList on purpose: token claims are stored as JSON and only allowlisted types
     * (not List.of / List.copyOf) can be read back.
     */
    public List<String> rolesFor(UUID userId, String clientId) {
        TreeSet<String> roles = new TreeSet<>(jdbc.queryForList(
                "select role from client_roles where user_id = ? and client_id = ?", String.class, userId, clientId));
        roles.add(DEFAULT_ROLE);
        return new ArrayList<>(roles);
    }
}
