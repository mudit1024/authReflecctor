package com.authreflector.oidc;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

/**
 * The safe settings every app gets when it is registered (used by the admin page, AUTH-18):
 * PKCE required, no consent screen for our own apps, 15-minute access tokens (D34),
 * 30-day refresh tokens that rotate on every use (D34, D37).
 */
public final class ClientDefaults {

    /** Custom client setting: the "aud" claim of this app's access tokens (its API, e.g. "splitmate-api"). */
    public static final String AUDIENCE_SETTING = "authreflector.audience";

    public static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    private ClientDefaults() {
    }

    /** An app with a backend (BFF): confidential client that authenticates with its secret. */
    public static RegisteredClient webApp(
            String clientId, String name, String audience,
            String redirectUri, String postLogoutRedirectUri, String encodedSecret) {
        return webApp(clientId, name, audience, List.of(redirectUri), List.of(postLogoutRedirectUri), encodedSecret);
    }

    public static RegisteredClient webApp(
            String clientId, String name, String audience,
            Collection<String> redirectUris, Collection<String> postLogoutRedirectUris, String encodedSecret) {

        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientName(name)
                .clientSecret(encodedSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUris(uris -> uris.addAll(redirectUris))
                .postLogoutRedirectUris(uris -> uris.addAll(postLogoutRedirectUris))
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .clientSettings(clientSettings(audience))
                .tokenSettings(tokenSettings())
                .build();
    }

    /**
     * A frontend-only app (JS SDK): public client. It has no secret (a browser can't keep one), so PKCE is what
     * protects its codes. No refresh tokens: the SDK silently re-checks the SSO session instead.
     */
    public static RegisteredClient singlePageApp(
            String clientId, String name, Collection<String> redirectUris, Collection<String> postLogoutRedirectUris) {

        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientName(name)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUris(uris -> uris.addAll(redirectUris))
                .postLogoutRedirectUris(uris -> uris.addAll(postLogoutRedirectUris))
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .clientSettings(clientSettings(clientId))
                .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(ACCESS_TOKEN_TTL).build())
                .build();
    }

    public static ClientSettings clientSettings(String audience) {
        return ClientSettings.builder()
                .requireProofKey(true)
                .requireAuthorizationConsent(false)
                .setting(AUDIENCE_SETTING, audience)
                .build();
    }

    public static TokenSettings tokenSettings() {
        return TokenSettings.builder()
                .accessTokenTimeToLive(ACCESS_TOKEN_TTL)
                .refreshTokenTimeToLive(REFRESH_TOKEN_TTL)
                .reuseRefreshTokens(false)
                .build();
    }

    /** The app's API audience; falls back to its client id when none was configured. */
    public static String audienceOf(RegisteredClient client) {
        Object audience = client.getClientSettings().getSetting(AUDIENCE_SETTING);
        return (audience instanceof String value && !value.isBlank()) ? value : client.getClientId();
    }
}
