package com.authreflector.oidc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.Cookie;

/** What a client app (and the browser in between) does during an OpenID Connect sign-in. */
public final class OidcTestSupport {

    /** Each app gets its own secret: the client repository rejects two apps with the same one. */
    public static String secretFor(String clientId) {
        return "test-secret-" + clientId;
    }

    private OidcTestSupport() {
    }

    /** A confidential web app (BFF) like SplitMate, registered the way the admin page will do it. */
    public static RegisteredClient webClient(String clientId, String name) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientName(name)
                .clientSecret("{noop}" + secretFor(clientId))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(redirectUri(clientId))
                .postLogoutRedirectUri("http://127.0.0.1:8081/")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(false).build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(15))
                        .refreshTokenTimeToLive(Duration.ofDays(30))
                        .reuseRefreshTokens(false)
                        .build())
                .build();
    }

    public static String redirectUri(String clientId) {
        return "http://127.0.0.1:8081/login/oauth2/code/" + clientId;
    }

    /** PKCE: the app keeps the verifier secret and sends only its SHA-256 hash (the challenge) in the browser. */
    public record Pkce(String verifier, String challenge) {

        public static Pkce create() throws Exception {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return new Pkce(verifier, Base64.getUrlEncoder().withoutPadding().encodeToString(hash));
        }
    }

    public static MockHttpServletRequestBuilder authorize(String clientId, Pkce pkce) {
        return get("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri(clientId))
                .queryParam("scope", "openid profile email")
                .queryParam("state", "state-123")
                .queryParam("nonce", "nonce-456")
                .queryParam("code_challenge", pkce.challenge())
                .queryParam("code_challenge_method", "S256");
    }

    public static MockHttpServletRequestBuilder exchangeCode(String clientId, String code, Pkce pkce) {
        return post("/oauth2/token")
                .with(basicAuth(clientId))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", code)
                .param("redirect_uri", redirectUri(clientId))
                .param("code_verifier", pkce.verifier());
    }

    public static MockHttpServletRequestBuilder refresh(String clientId, String refreshToken) {
        return post("/oauth2/token")
                .with(basicAuth(clientId))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "refresh_token")
                .param("refresh_token", refreshToken);
    }

    public static RequestPostProcessor basicAuth(String clientId) {
        String credentials = Base64.getEncoder().encodeToString(
                (clientId + ":" + secretFor(clientId)).getBytes(StandardCharsets.UTF_8));
        return request -> {
            request.addHeader("Authorization", "Basic " + credentials);
            return request;
        };
    }

    public static String queryParam(String location, String name) {
        return UriComponentsBuilder.fromUri(URI.create(location)).build().getQueryParams().getFirst(name);
    }

    /** Signs in through the real login form (with a real CSRF token) and returns the session. */
    public static MockHttpSession signIn(MockMvc mockMvc, String email, String password, MockHttpSession session)
            throws Exception {
        Cookie token = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        MvcResult result = mockMvc.perform(post("/login")
                        .session(session)
                        .cookie(token)
                        .header("X-XSRF-TOKEN", token.getValue())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", email)
                        .param("password", password))
                .andReturn();
        if (result.getResponse().getStatus() != 200) {
            throw new IllegalStateException("Login failed: " + result.getResponse().getContentAsString());
        }
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
