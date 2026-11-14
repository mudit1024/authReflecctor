package com.authreflector.oidc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

/**
 * Refresh token reuse detection (SplitMate decision D37), wrapped around the normal authorization store.
 *
 * - On save: when a refresh replaced the refresh token, remember the old one (hash only).
 * - On lookup: an unknown refresh token that matches a remembered hash is a replayed copy, so the whole
 *   authorization is removed. The thief and the real user both have to sign in again.
 */
public class ReuseDetectingAuthorizationService implements OAuth2AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(ReuseDetectingAuthorizationService.class);

    private final OAuth2AuthorizationService delegate;
    private final JdbcTemplate jdbc;

    public ReuseDetectingAuthorizationService(OAuth2AuthorizationService delegate, JdbcTemplate jdbc) {
        this.delegate = delegate;
        this.jdbc = jdbc;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        OAuth2Authorization previous = delegate.findById(authorization.getId());
        String oldToken = refreshTokenValue(previous);
        String newToken = refreshTokenValue(authorization);

        delegate.save(authorization);

        if (oldToken != null && !Objects.equals(oldToken, newToken)) {
            jdbc.update("insert into rotated_refresh_tokens (token_hash, authorization_id) values (?, ?) on conflict do nothing",
                    sha256(oldToken), authorization.getId());
        }
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization); // remembered hashes go with it (on delete cascade)
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        OAuth2Authorization found = delegate.findByToken(token, tokenType);
        if (found == null && (tokenType == null || OAuth2TokenType.REFRESH_TOKEN.equals(tokenType))) {
            revokeIfReplayed(token);
        }
        return found;
    }

    private void revokeIfReplayed(String token) {
        List<String> authorizationIds = jdbc.queryForList(
                "select authorization_id from rotated_refresh_tokens where token_hash = ?", String.class, sha256(token));
        for (String authorizationId : authorizationIds) {
            OAuth2Authorization family = delegate.findById(authorizationId);
            if (family != null) {
                log.warn("Refresh token reuse detected for authorization {} (client {}, user {}): revoking it",
                        authorizationId, family.getRegisteredClientId(), family.getPrincipalName());
                delegate.remove(family);
            }
        }
    }

    private static String refreshTokenValue(OAuth2Authorization authorization) {
        if (authorization == null) {
            return null;
        }
        OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = authorization.getRefreshToken();
        return refreshToken == null ? null : refreshToken.getToken().getTokenValue();
    }

    static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", exception);
        }
    }
}
