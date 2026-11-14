package com.example.app;

import com.authreflector.client.AuthReflectorClientProperties;
import com.authreflector.client.AuthReflectorSignInEvent;
import com.authreflector.client.AuthReflectorTokenValidators;
import com.authreflector.client.AuthReflectorUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest(classes = TestApp.class)
@AutoConfigureMockMvc
@Import(ClientStarterTest.TestKeys.class)
class ClientStarterTest {

    private static final KeyPair KEYS = generateKeys();
    private static final UUID USER_ID = UUID.fromString("8f14e45f-ceea-467f-a0e6-1b2c3d4e5f60");

    /** Replaces the JWKS download with a test key, but keeps the starter's validation rules. */
    @TestConfiguration
    static class TestKeys {
        @Bean
        JwtDecoder jwtDecoder(AuthReflectorClientProperties properties) {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
            decoder.setJwtValidator(AuthReflectorTokenValidators.create(properties));
            return decoder;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRegistrationRepository registrations;

    @Autowired
    private Environment environment;

    // ---------- configuration from four lines ----------

    // All endpoints derived from the issuer: no discovery call, so the app starts even if AuthReflector is down
    @Test
    void shouldRegisterAuthReflectorFromTheIssuerAlone() {
        ClientRegistration registration = registrations.findByRegistrationId("authreflector");

        assertThat(registration.getClientId()).isEqualTo("demo-app");
        assertThat(registration.getScopes()).containsExactlyInAnyOrder("openid", "profile", "email");
        assertThat(registration.getProviderDetails().getAuthorizationUri()).isEqualTo("http://localhost:9000/oauth2/authorize");
        assertThat(registration.getProviderDetails().getTokenUri()).isEqualTo("http://localhost:9000/oauth2/token");
        assertThat(registration.getProviderDetails().getJwkSetUri()).isEqualTo("http://localhost:9000/oauth2/jwks");
        assertThat(registration.getProviderDetails().getConfigurationMetadata())
                .containsEntry("end_session_endpoint", "http://localhost:9000/connect/logout");
    }

    // Each app gets its own session cookie, so two apps on localhost don't overwrite each other's
    @Test
    void shouldNameTheSessionCookieAfterTheApp() {
        assertThat(environment.getProperty("server.servlet.session.cookie.name")).isEqualTo("DEMO_APP_SESSION");
    }

    // ---------- browser sign-in (BFF) ----------

    @Test
    void shouldStartSignInAtAuthReflectorWithPkce() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/authreflector"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("http://localhost:9000/oauth2/authorize?")))
                .andExpect(header().string("Location", containsString("client_id=demo-app")))
                .andExpect(header().string("Location", containsString("code_challenge_method=S256")))
                .andExpect(header().string("Location", containsString("redirect_uri=http://localhost/login/oauth2/code/authreflector")));
    }

    @Test
    void shouldOfferAShortSignInUrl() throws Exception {
        mockMvc.perform(get("/auth/login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/oauth2/authorization/authreflector"));
    }

    @Test
    void shouldSendPageVisitorsToSignIn() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/oauth2/authorization/authreflector")));
    }

    // After sign-in, the browser returns to the page it asked for, never to a background API call
    // (found by the real-browser test: a page's fetch('/auth/me') was replayed after sign-in)
    @Test
    void shouldRememberOnlyPagesToReturnToAfterSignIn() throws Exception {
        MockHttpSession page = new MockHttpSession();
        mockMvc.perform(get("/dashboard").session(page)).andExpect(status().is3xxRedirection());
        assertThat(page.getAttribute("SPRING_SECURITY_SAVED_REQUEST")).isNotNull();

        MockHttpSession api = new MockHttpSession();
        mockMvc.perform(get("/api/hello").session(api)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/auth/me").session(api)).andExpect(status().isUnauthorized());
        assertThat(api.getAttribute("SPRING_SECURITY_SAVED_REQUEST")).isNull();
    }

    @Test
    void shouldAnswerApiCallsWithoutSignInWith401Json() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void shouldDescribeTheSignedInUser() throws Exception {
        mockMvc.perform(get("/auth/me").with(signedInBrowser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.email").value("asha@example.com"))
                .andExpect(jsonPath("$.name").value("Asha"))
                .andExpect(jsonPath("$.roles[1]").value("ADMIN"));
        mockMvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldGiveControllersTheCurrentUser() throws Exception {
        mockMvc.perform(get("/api/hello").with(signedInBrowser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()));
    }

    // The browser sends the session cookie automatically, so state changes need a CSRF token
    @Test
    void shouldRequireCsrfForBrowserSessions() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/things").session(session).with(signedInBrowser()))
                .andExpect(status().isForbidden());

        String csrf = mockMvc.perform(get("/auth/csrf").session(session).with(signedInBrowser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/things").session(session).with(signedInBrowser())
                        .header("X-CSRF-TOKEN", JsonPath.<String>read(csrf, "$.token")))
                .andExpect(status().isOk());
    }

    // Single sign-out: the app's session ends and the browser goes on to end the AuthReflector session
    @Test
    void shouldSignOutHereAndAtAuthReflector() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String csrf = JsonPath.read(mockMvc.perform(get("/auth/csrf").session(session).with(signedInBrowser()))
                .andReturn().getResponse().getContentAsString(), "$.token");

        mockMvc.perform(post("/auth/logout").session(session).with(signedInBrowser()).header("X-CSRF-TOKEN", csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoutUrl", startsWith("http://localhost:9000/connect/logout?id_token_hint=")))
                .andExpect(jsonPath("$.logoutUrl", containsString("post_logout_redirect_uri=http://localhost/")));
    }

    // ---------- bearer tokens (mobile apps, API clients) ----------

    @Test
    void shouldAcceptAValidBearerTokenForThisApp() throws Exception {
        mockMvc.perform(get("/api/hello").header("Authorization", "Bearer " + token("demo-api", "http://localhost:9000", 5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.email").value("asha@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    // A token for another app (e.g. CodeQuror) must not work here
    @Test
    void shouldRejectATokenForAnotherApp() throws Exception {
        mockMvc.perform(get("/api/hello").header("Authorization", "Bearer " + token("codequror-api", "http://localhost:9000", 5)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectATokenFromAnotherIssuer() throws Exception {
        mockMvc.perform(get("/api/hello").header("Authorization", "Bearer " + token("demo-api", "https://evil.example", 5)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectAnExpiredToken() throws Exception {
        mockMvc.perform(get("/api/hello").header("Authorization", "Bearer " + token("demo-api", "http://localhost:9000", -10)))
                .andExpect(status().isUnauthorized());
    }

    // No cookies are involved with bearer tokens, so CSRF doesn't apply
    @Test
    void shouldNotRequireCsrfForBearerTokens() throws Exception {
        mockMvc.perform(post("/api/things").header("Authorization", "Bearer " + token("demo-api", "http://localhost:9000", 5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdBy").value(USER_ID.toString()));
    }

    // ---------- helpers ----------

    /** A browser that signed in through AuthReflector (what oauth2Login leaves in the session). */
    private static RequestPostProcessor signedInBrowser() {
        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token-value")
                .issuer("http://localhost:9000")
                .subject(USER_ID.toString())
                .audience(List.of("demo-app"))
                .claim("email", "asha@example.com")
                .claim("name", "Asha")
                .claim("roles", List.of("USER", "ADMIN"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        DefaultOidcUser user = new DefaultOidcUser(List.of(new OidcUserAuthority(idToken)), idToken, "sub");
        return oidcLogin().clientRegistration(clientRegistration()).oidcUser(user);
    }

    private static ClientRegistration clientRegistration() {
        return ClientRegistration.withRegistrationId("authreflector")
                .clientId("demo-app")
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("http://localhost:9000/oauth2/authorize")
                .tokenUri("http://localhost:9000/oauth2/token")
                .providerConfigurationMetadata(java.util.Map.of("end_session_endpoint", "http://localhost:9000/connect/logout"))
                .build();
    }

    private static String token(String audience, String issuer, int minutesValid) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(USER_ID.toString())
                .audience(List.of(audience))
                .issuedAt(now.minus(Duration.ofMinutes(20)))
                .expiresAt(now.plus(Duration.ofMinutes(minutesValid)))
                .claim("email", "asha@example.com")
                .claim("name", "Asha")
                .claim("roles", List.of("USER"))
                .build();
        com.nimbusds.jose.jwk.RSAKey jwk = new com.nimbusds.jose.jwk.RSAKey.Builder((RSAPublicKey) KEYS.getPublic())
                .privateKey((RSAPrivateKey) KEYS.getPrivate()).keyID("test").build();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(jwk)));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("test").build(), claims)).getTokenValue();
    }

    private static KeyPair generateKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
