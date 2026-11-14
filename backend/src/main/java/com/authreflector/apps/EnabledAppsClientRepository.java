package com.authreflector.apps;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * The client repository the authorization server uses: a disabled app is simply "not found", so it can't
 * start a sign-in or get tokens. Apps without a client_apps row count as enabled.
 * The admin pages use AppRegistry, which also sees disabled apps.
 */
public class EnabledAppsClientRepository implements RegisteredClientRepository {

    private final RegisteredClientRepository delegate;
    private final JdbcTemplate jdbc;

    public EnabledAppsClientRepository(RegisteredClientRepository delegate, JdbcTemplate jdbc) {
        this.delegate = delegate;
        this.jdbc = jdbc;
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        delegate.save(registeredClient);
    }

    @Override
    public RegisteredClient findById(String id) {
        return enabledOnly(delegate.findById(id));
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return enabledOnly(delegate.findByClientId(clientId));
    }

    private RegisteredClient enabledOnly(RegisteredClient client) {
        if (client == null) {
            return null;
        }
        List<Boolean> enabled = jdbc.queryForList(
                "select enabled from client_apps where client_id = ?", Boolean.class, client.getClientId());
        return (enabled.isEmpty() || Boolean.TRUE.equals(enabled.getFirst())) ? client : null;
    }
}
