package com.authreflector.launcher;

import java.security.Principal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import com.authreflector.security.AuthenticatedUser;

/**
 * Records "user X signed in to app Y" for the launcher, by watching the authorization store:
 * the moment an authorization-code sign-in first receives its access token, that's one sign-in
 * (explicit or silent SSO). Refreshes reuse the same authorization, so they don't count.
 */
public class UsageRecordingAuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public UsageRecordingAuthorizationService(OAuth2AuthorizationService delegate, JdbcTemplate jdbc, Clock clock) {
        this.delegate = delegate;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        boolean firstTokens = authorization.getAccessToken() != null
                && AuthorizationGrantType.AUTHORIZATION_CODE.equals(authorization.getAuthorizationGrantType())
                && hasNoAccessTokenYet(authorization.getId());

        delegate.save(authorization);

        if (firstTokens && signedInUser(authorization) instanceof AuthenticatedUser user) {
            record(user, authorization.getRegisteredClientId());
        }
    }

    private boolean hasNoAccessTokenYet(String authorizationId) {
        OAuth2Authorization previous = delegate.findById(authorizationId);
        return previous == null || previous.getAccessToken() == null;
    }

    private static Object signedInUser(OAuth2Authorization authorization) {
        Object principal = authorization.getAttribute(Principal.class.getName());
        return principal instanceof Authentication authentication ? authentication.getPrincipal() : null;
    }

    private void record(AuthenticatedUser user, String registeredClientId) {
        Instant now = clock.instant();
        jdbc.update("""
                insert into user_app_daily_usage (user_id, client_id, day, sign_ins, last_sign_in)
                select ?, client_id, ?, 1, ? from oauth2_registered_client where id = ?
                on conflict (user_id, client_id, day)
                do update set sign_ins = user_app_daily_usage.sign_ins + 1, last_sign_in = excluded.last_sign_in
                """,
                user.getId(), LocalDate.ofInstant(now, ZoneOffset.UTC), Timestamp.from(now), registeredClientId);
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        return delegate.findByToken(token, tokenType);
    }
}
