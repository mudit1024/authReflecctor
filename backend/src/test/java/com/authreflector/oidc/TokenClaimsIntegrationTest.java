package com.authreflector.oidc;

import static com.authreflector.oidc.OidcTestSupport.authorize;
import static com.authreflector.oidc.OidcTestSupport.exchangeCode;
import static com.authreflector.oidc.OidcTestSupport.queryParam;
import static com.authreflector.oidc.OidcTestSupport.signIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.authreflector.IntegrationTest;
import com.authreflector.identity.User;
import com.authreflector.identity.UserRepository;
import com.authreflector.oidc.OidcTestSupport.Pkce;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.RSAKey;

/**
 * What the tokens say. Includes the ports of SplitMate's AccessTokenServiceTest (SPLIT-12):
 * subject and claims, RS256 + kid, accepted until expiry and rejected after, tampered payload rejected.
 */
@IntegrationTest
@Transactional
class TokenClaimsIntegrationTest {

    private static final String EMAIL = "asha@example.com";
    private static final String PASSWORD = "Password@123";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RegisteredClientRepository clients;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RSAKey signingKey;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        userId = userRepository.saveAndFlush(new User("Asha", EMAIL, passwordEncoder.encode(PASSWORD))).getId();
        clients.save(ClientDefaults.webApp("splitmate-web", "SplitMate", "splitmate-api",
                OidcTestSupport.redirectUri("splitmate-web"), "http://127.0.0.1:8081/", "{noop}" + OidcTestSupport.secretFor("splitmate-web")));
        clients.save(ClientDefaults.webApp("demo-app", "Demo App", "demo-api",
                OidcTestSupport.redirectUri("demo-app"), "http://127.0.0.1:8081/", "{noop}" + OidcTestSupport.secretFor("demo-app")));
    }

    // ---------- access token ----------

    // Ported: shouldIssueSignedTokenWithUserClaims (sub is the user id, never the email)
    @Test
    void shouldPutTheStableUserIdAndProfileIntoTheAccessToken() throws Exception {
        Jwt accessToken = decode(tokens("splitmate-web").get("access_token"));

        assertThat(accessToken.getSubject()).isEqualTo(userId.toString());
        assertThat(accessToken.getClaimAsString("email")).isEqualTo(EMAIL);
        assertThat(accessToken.getClaimAsString("name")).isEqualTo("Asha");
        assertThat(accessToken.getClaimAsString("iss")).isEqualTo("http://localhost:9000");
    }

    // A SplitMate token must be rejected by any other app's API
    @Test
    void shouldAddressTheAccessTokenToThatAppsApiOnly() throws Exception {
        assertThat(decode(tokens("splitmate-web").get("access_token")).getAudience()).containsExactly("splitmate-api");
        assertThat(decode(tokens("demo-app").get("access_token")).getAudience()).containsExactly("demo-api");
    }

    @Test
    void shouldGiveEveryoneTheUserRoleByDefault() throws Exception {
        assertThat(decode(tokens("splitmate-web").get("access_token")).getClaimAsStringList("roles")).containsExactly("USER");
    }

    // Admin in one app is not admin in another
    @Test
    void shouldIncludeRolesGrantedForThatAppOnly() throws Exception {
        jdbc.update("insert into client_roles (user_id, client_id, role) values (?, 'splitmate-web', 'ADMIN')", userId);

        assertThat(decode(tokens("splitmate-web").get("access_token")).getClaimAsStringList("roles"))
                .containsExactlyInAnyOrder("USER", "ADMIN");
        assertThat(decode(tokens("demo-app").get("access_token")).getClaimAsStringList("roles"))
                .containsExactly("USER");
    }

    // Ported: shouldUseRs256WithKeyIdInHeader
    @Test
    void shouldSignWithRs256AndKeyId() throws Exception {
        Jwt accessToken = decode(tokens("splitmate-web").get("access_token"));

        assertThat(accessToken.getHeaders().get("alg")).hasToString("RS256");
        assertThat(accessToken.getHeaders().get("kid")).isEqualTo("test-key-1");
    }

    // D34: 15 minutes
    @Test
    void shouldExpireTheAccessTokenAfterFifteenMinutes() throws Exception {
        Jwt accessToken = decode(tokens("splitmate-web").get("access_token"));

        assertThat(Duration.between(accessToken.getIssuedAt(), accessToken.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
    }

    // Ported: shouldAcceptTokenJustBeforeExpiry / shouldRejectExpiredToken (what every app's API will do)
    @Test
    void shouldBeAcceptedUntilExpiryAndRejectedAfter() throws Exception {
        String token = tokens("splitmate-web").get("access_token");
        Instant issuedAt = decode(token).getIssuedAt();

        decoderAt(issuedAt.plus(Duration.ofMinutes(14))).decode(token);

        // 15 minutes lifetime + 60 seconds default clock skew, then one more minute
        assertThatThrownBy(() -> decoderAt(issuedAt.plus(Duration.ofMinutes(17))).decode(token))
                .isInstanceOf(JwtException.class);
    }

    // Ported: shouldRejectTokenWhosePayloadWasTampered
    @Test
    void shouldRejectATokenWhosePayloadWasTampered() throws Exception {
        String[] parts = tokens("splitmate-web").get("access_token").split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"" + userId + "\",\"roles\":[\"ADMIN\"],\"aud\":[\"splitmate-api\"]}").getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> decode(parts[0] + "." + forgedPayload + "." + parts[2])).isInstanceOf(JwtException.class);
    }

    // ---------- ID token and userinfo ----------

    // The ID token is for the app itself (aud = client id, per OpenID Connect), with the same stable subject
    @Test
    void shouldIssueAnIdTokenForTheAppWithTheSameSubject() throws Exception {
        Jwt idToken = decode(tokens("splitmate-web").get("id_token"));

        assertThat(idToken.getAudience()).containsExactly("splitmate-web");
        assertThat(idToken.getSubject()).isEqualTo(userId.toString());
        assertThat(idToken.getClaimAsString("email")).isEqualTo(EMAIL);
        assertThat(idToken.getClaimAsString("name")).isEqualTo("Asha");
        assertThat(idToken.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(idToken.getClaimAsString("nonce")).isEqualTo("nonce-456");
    }

    @Test
    void shouldAnswerUserinfoWithTheStableSubject() throws Exception {
        String accessToken = tokens("splitmate-web").get("access_token");

        mockMvc.perform(get("/userinfo").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value(userId.toString()))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.name").value("Asha"));
    }

    // ---------- defaults every new app gets ----------

    @Test
    void shouldGiveNewWebAppsSafeDefaults() {
        RegisteredClient app = clients.findByClientId("splitmate-web");

        assertThat(app.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(app.getClientSettings().isRequireAuthorizationConsent()).isFalse();
        assertThat(app.getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(Duration.ofMinutes(15));
        assertThat(app.getTokenSettings().getRefreshTokenTimeToLive()).isEqualTo(Duration.ofDays(30));
        assertThat(app.getTokenSettings().isReuseRefreshTokens()).isFalse();
        assertThat(ClientDefaults.audienceOf(app)).isEqualTo("splitmate-api");
    }

    // ---------- helpers ----------

    private Map<String, String> tokens(String clientId) throws Exception {
        MockHttpSession session = signIn(mockMvc, EMAIL, PASSWORD, new MockHttpSession());
        Pkce pkce = Pkce.create();
        MvcResult authorized = mockMvc.perform(authorize(clientId, pkce).session(session)).andReturn();
        String code = queryParam(authorized.getResponse().getRedirectedUrl(), "code");
        String body = mockMvc.perform(exchangeCode(clientId, code, pkce))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Map.of(
                "access_token", JsonPath.read(body, "$.access_token"),
                "id_token", JsonPath.read(body, "$.id_token"));
    }

    private Jwt decode(String token) throws Exception {
        return NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build().decode(token);
    }

    private NimbusJwtDecoder decoderAt(Instant instant) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
        JwtTimestampValidator validator = new JwtTimestampValidator();
        validator.setClock(Clock.fixed(instant, ZoneOffset.UTC));
        decoder.setJwtValidator(validator);
        return decoder;
    }
}
