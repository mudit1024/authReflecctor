package com.authreflector.oidc;

import static com.authreflector.oidc.OidcTestSupport.authorize;
import static com.authreflector.oidc.OidcTestSupport.exchangeCode;
import static com.authreflector.oidc.OidcTestSupport.queryParam;
import static com.authreflector.oidc.OidcTestSupport.refresh;
import static com.authreflector.oidc.OidcTestSupport.signIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
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

@IntegrationTest
@Transactional
class RefreshTokenRotationIntegrationTest {

    private static final String CLIENT = "splitmate-web";

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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        userRepository.saveAndFlush(new User("Asha", "asha@example.com", passwordEncoder.encode("Password@123")));
        clients.save(OidcTestSupport.webClient(CLIENT, "SplitMate"));
    }

    // Rotation: each refresh returns a new refresh token, and the old one stops working
    @Test
    void shouldIssueANewRefreshTokenOnEveryRefresh() throws Exception {
        String first = signInAndGetRefreshToken();

        String second = refreshToken(mockMvc.perform(refresh(CLIENT, first)).andExpect(status().isOk()).andReturn());
        String third = refreshToken(mockMvc.perform(refresh(CLIENT, second)).andExpect(status().isOk()).andReturn());

        assertThat(second).isNotEqualTo(first);
        assertThat(third).isNotEqualTo(second);
    }

    // Reuse detection: replaying an old token means a copy exists somewhere, so the whole sign-in is revoked
    @Test
    void shouldRevokeTheWholeSignInWhenAnOldRefreshTokenIsReused() throws Exception {
        String stolen = signInAndGetRefreshToken();
        String current = refreshToken(mockMvc.perform(refresh(CLIENT, stolen)).andExpect(status().isOk()).andReturn());

        // The thief replays the old token
        mockMvc.perform(refresh(CLIENT, stolen))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));

        // ...which also kills the real user's current token: everyone must sign in again
        mockMvc.perform(refresh(CLIENT, current))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
        assertThat(jdbc.queryForObject("select count(*) from oauth2_authorization", Integer.class)).isZero();
    }

    @Test
    void shouldRememberReplacedTokensOnlyAsHashes() throws Exception {
        String first = signInAndGetRefreshToken();
        mockMvc.perform(refresh(CLIENT, first)).andExpect(status().isOk());

        String stored = jdbc.queryForObject("select token_hash from rotated_refresh_tokens", String.class);

        assertThat(stored).hasSize(64).doesNotContain(first).matches("[0-9a-f]{64}");
    }

    @Test
    void shouldRejectAnUnknownRefreshTokenWithoutRevokingAnything() throws Exception {
        signInAndGetRefreshToken();

        mockMvc.perform(refresh(CLIENT, "never-issued"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
        assertThat(jdbc.queryForObject("select count(*) from oauth2_authorization", Integer.class)).isOne();
    }

    private String signInAndGetRefreshToken() throws Exception {
        MockHttpSession session = signIn(mockMvc, "asha@example.com", "Password@123", new MockHttpSession());
        Pkce pkce = Pkce.create();
        MvcResult authorized = mockMvc.perform(authorize(CLIENT, pkce).session(session)).andReturn();
        String code = queryParam(authorized.getResponse().getRedirectedUrl(), "code");
        return refreshToken(mockMvc.perform(exchangeCode(CLIENT, code, pkce)).andExpect(status().isOk()).andReturn());
    }

    private static String refreshToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.refresh_token");
    }
}
