package com.authreflector.apps;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.authreflector.apps.AppDtos.AppRequest;
import com.authreflector.apps.AppDtos.AppResponse;
import com.authreflector.apps.AppDtos.CreatedApp;
import com.authreflector.apps.AppDtos.NewSecret;
import com.authreflector.oidc.ClientDefaults;
import com.authreflector.web.ApiException;

/**
 * Registers and manages the apps that use AuthReflector. Everything is stored in the database and read by
 * the authorization server on every request, so changes apply immediately: no restart, ever.
 */
@Service
@Transactional
public class AppRegistry {

    private static final SecureRandom RANDOM = new SecureRandom();

    // Sees all apps, including disabled ones (the authorization server's repository hides those)
    private final JdbcRegisteredClientRepository clients;
    private final ClientAppRepository apps;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    public AppRegistry(JdbcTemplate jdbc, ClientAppRepository apps, PasswordEncoder passwordEncoder) {
        this.clients = new JdbcRegisteredClientRepository(jdbc);
        this.apps = apps;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<AppResponse> list() {
        return apps.findAll().stream()
                .map(app -> toResponse(app, clients.findByClientId(app.getClientId())))
                .sorted(Comparator.comparing(AppResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public AppResponse get(String clientId) {
        ClientApp app = findApp(clientId);
        return toResponse(app, clients.findByClientId(clientId));
    }

    public CreatedApp create(AppRequest request) {
        if (clients.findByClientId(request.clientId()) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "An app with this client id already exists");
        }

        String secret = null;
        RegisteredClient client;
        if (request.type() == AppType.WEB) {
            secret = newSecret();
            client = ClientDefaults.webApp(request.clientId(), request.name().trim(), audienceOf(request),
                    request.redirectUris(), postLogoutUris(request), passwordEncoder.encode(secret));
        } else {
            client = ClientDefaults.singlePageApp(request.clientId(), request.name().trim(),
                    request.redirectUris(), postLogoutUris(request));
        }
        clients.save(client);

        ClientApp app = new ClientApp(request.clientId(), request.type());
        applyLauncherDetails(app, request);
        // Flush now: the authorization server reads client_apps with plain JDBC, which bypasses Hibernate
        apps.saveAndFlush(app);

        return new CreatedApp(toResponse(app, client), secret);
    }

    public AppResponse update(String clientId, AppRequest request) {
        ClientApp app = findApp(clientId);
        RegisteredClient existing = clients.findByClientId(clientId);
        if (!clientId.equals(request.clientId()) || app.getAppType() != request.type()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Client id and type can't be changed; register a new app instead");
        }

        String audience = request.type() == AppType.WEB ? audienceOf(request) : clientId;
        RegisteredClient updated = RegisteredClient.from(existing)
                .clientName(request.name().trim())
                .redirectUris(uris -> {
                    uris.clear();
                    uris.addAll(request.redirectUris());
                })
                .postLogoutRedirectUris(uris -> {
                    uris.clear();
                    uris.addAll(postLogoutUris(request));
                })
                .clientSettings(ClientSettings.withSettings(existing.getClientSettings().getSettings())
                        .setting(ClientDefaults.AUDIENCE_SETTING, audience)
                        .build())
                .build();
        clients.save(updated);

        boolean wasEnabled = app.isEnabled();
        applyLauncherDetails(app, request);
        // Flush now: the authorization server reads client_apps with plain JDBC, which bypasses Hibernate
        apps.saveAndFlush(app);

        if (wasEnabled && !app.isEnabled()) {
            // Disabling also ends every existing sign-in: their refresh tokens stop working now
            jdbc.update("delete from oauth2_authorization where registered_client_id = ?", existing.getId());
        }
        return toResponse(app, updated);
    }

    /** Issues a new secret (shown once) and invalidates the old one immediately. */
    public NewSecret regenerateSecret(String clientId) {
        ClientApp app = findApp(clientId);
        if (app.getAppType() != AppType.WEB) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Frontend-only apps have no secret");
        }
        String secret = newSecret();
        clients.save(RegisteredClient.from(clients.findByClientId(clientId))
                .clientSecret(passwordEncoder.encode(secret))
                .build());
        return new NewSecret(secret);
    }

    private ClientApp findApp(String clientId) {
        return apps.findById(clientId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No app with this client id"));
    }

    private static void applyLauncherDetails(ClientApp app, AppRequest request) {
        app.update(
                blankToNull(request.homeUrl()),
                blankToNull(request.description()),
                blankToNull(request.color()),
                request.showInLauncher() == null || request.showInLauncher(),
                request.enabled() == null || request.enabled());
    }

    private static AppResponse toResponse(ClientApp app, RegisteredClient client) {
        return new AppResponse(
                app.getAppType(),
                app.getClientId(),
                client.getClientName(),
                ClientDefaults.audienceOf(client),
                sorted(client.getRedirectUris()),
                sorted(client.getPostLogoutRedirectUris()),
                app.getHomeUrl(),
                app.getDescription(),
                app.getColor(),
                app.isShowInLauncher(),
                app.isEnabled());
    }

    private static String audienceOf(AppRequest request) {
        return (request.audience() == null || request.audience().isBlank()) ? request.clientId() : request.audience();
    }

    private static List<String> postLogoutUris(AppRequest request) {
        return request.postLogoutRedirectUris() == null ? List.of() : request.postLogoutRedirectUris();
    }

    private static List<String> sorted(Set<String> values) {
        List<String> list = new ArrayList<>(values);
        list.sort(null);
        return list;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    // 32 random bytes = 256 bits, URL-safe text (43 characters)
    private static String newSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
