package com.authreflector.oidc;

import static com.authreflector.oidc.OidcTestSupport.authorize;
import static com.authreflector.oidc.OidcTestSupport.exchangeCode;
import static com.authreflector.oidc.OidcTestSupport.queryParam;
import static com.authreflector.oidc.OidcTestSupport.redirectUri;
import static com.authreflector.oidc.OidcTestSupport.signIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

@IntegrationTest
@Transactional
class AuthorizationServerIntegrationTest {

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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        userRepository.saveAndFlush(new User("Asha", EMAIL, passwordEncoder.encode(PASSWORD)));
        // Saved straight into the database: no restart, no config file (owner decision)
        clients.save(OidcTestSupport.webClient("splitmate-web", "SplitMate"));
        clients.save(OidcTestSupport.webClient("demo-app", "Demo App"));
    }

    // ---------- published metadata ----------

    @Test
    void shouldPublishOpenIdConfiguration() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("http://localhost:9000"))
                .andExpect(jsonPath("$.authorization_endpoint").value("http://localhost:9000/oauth2/authorize"))
                .andExpect(jsonPath("$.token_endpoint").value("http://localhost:9000/oauth2/token"))
                .andExpect(jsonPath("$.jwks_uri").value("http://localhost:9000/oauth2/jwks"))
                .andExpect(jsonPath("$.end_session_endpoint").value("http://localhost:9000/connect/logout"))
                .andExpect(jsonPath("$.code_challenge_methods_supported[0]").value("S256"));
    }

    // Apps verify token signatures with these public keys; the private part must never appear
    @Test
    void shouldPublishOnlyThePublicSigningKey() throws Exception {
        mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys.length()").value(1))
                .andExpect(jsonPath("$.keys[0].kid").value("test-key-1"))
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].n").exists())
                .andExpect(jsonPath("$.keys[0].d").doesNotExist())
                .andExpect(jsonPath("$.keys[0].p").doesNotExist());
    }

    // ---------- the sign-in flow ----------

    // Not signed in yet: the app's request is remembered and the browser goes to our React login page
    @Test
    void shouldSendAnonymousUsersToTheLoginPageAndContinueAfterSignIn() throws Exception {
        Pkce pkce = Pkce.create();
        MvcResult anonymous = mockMvc.perform(authorize("splitmate-web", pkce))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/login")))
                .andReturn();
        MockHttpSession session = (MockHttpSession) anonymous.getRequest().getSession(false);

        // The login page can show "to continue to SplitMate"
        mockMvc.perform(get("/api/v1/auth/login-context").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientName").value("SplitMate"));

        // After sign-in, the React page is told to continue the app's authorize request
        MockHttpSession signedIn = signIn(mockMvc, EMAIL, PASSWORD, session);
        MvcResult continued = mockMvc.perform(authorize("splitmate-web", pkce).session(signedIn))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith(redirectUri("splitmate-web") + "?code=")))
                .andReturn();
        assertThat(queryParam(continued.getResponse().getRedirectedUrl(), "state")).isEqualTo("state-123");
    }

    // Silent SSO check (prompt=none): never show a login page; tell the app "not signed in" instead
    @Test
    void shouldAnswerLoginRequiredToASilentCheckWhenNobodyIsSignedIn() throws Exception {
        mockMvc.perform(authorize("splitmate-web", Pkce.create()).queryParam("prompt", "none"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith(redirectUri("splitmate-web") + "?error=login_required")));
    }

    @Test
    void shouldAnswerASilentCheckWithACodeWhenAlreadySignedIn() throws Exception {
        MockHttpSession session = signIn(mockMvc, EMAIL, PASSWORD, new MockHttpSession());

        mockMvc.perform(authorize("splitmate-web", Pkce.create()).queryParam("prompt", "none").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith(redirectUri("splitmate-web") + "?code=")));
    }

    @Test
    void shouldReturnNoLoginContextWhenNoAppIsWaiting() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login-context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientName").doesNotExist());
    }

    @Test
    void shouldExchangeTheCodeForIdAccessAndRefreshTokens() throws Exception {
        MockHttpSession session = signIn(mockMvc, EMAIL, PASSWORD, new MockHttpSession());
        Pkce pkce = Pkce.create();
        String code = codeFor("splitmate-web", pkce, session);

        mockMvc.perform(exchangeCode("splitmate-web", code, pkce))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.id_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(org.hamcrest.Matchers.lessThanOrEqualTo(900)));
    }

    // SSO: one sign-in, then a second app gets its code without any login page
    @Test
    void shouldSignIntoASecondAppWithoutAskingForThePasswordAgain() throws Exception {
        MockHttpSession session = signIn(mockMvc, EMAIL, PASSWORD, new MockHttpSession());

        codeFor("splitmate-web", Pkce.create(), session);

        mockMvc.perform(authorize("demo-app", Pkce.create()).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith(redirectUri("demo-app") + "?code=")));
    }

    // ---------- what must be refused ----------

    // PKCE: without the verifier, a stolen code is useless
    @Test
    void shouldRejectCodeExchangeWithAWrongCodeVerifier() throws Exception {
        MockHttpSession session = signIn(mockMvc, EMAIL, PASSWORD, new MockHttpSession());
        Pkce pkce = Pkce.create();
        String code = codeFor("splitmate-web", pkce, session);

        mockMvc.perform(exchangeCode("splitmate-web", code, Pkce.create()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void shouldRequirePkceForEveryApp() throws Exception {
        MockHttpSession session = signIn(mockMvc, EMAIL, PASSWORD, new MockHttpSession());

        mockMvc.perform(get("/oauth2/authorize").session(session)
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "splitmate-web")
                        .queryParam("redirect_uri", redirectUri("splitmate-web"))
                        .queryParam("scope", "openid")
                        .queryParam("state", "s"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("error=invalid_request")));
    }

    // Never redirect codes to an address the app didn't register (that would hand them to an attacker)
    @Test
    void shouldRefuseAnUnregisteredRedirectUri() throws Exception {
        MockHttpSession session = signIn(mockMvc, EMAIL, PASSWORD, new MockHttpSession());

        mockMvc.perform(authorize("splitmate-web", Pkce.create()).session(session)
                        .queryParam("redirect_uri", "https://attacker.example/callback"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRefuseAnUnknownApp() throws Exception {
        MockHttpSession session = signIn(mockMvc, EMAIL, PASSWORD, new MockHttpSession());

        mockMvc.perform(get("/oauth2/authorize").session(session)
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "no-such-app")
                        .queryParam("redirect_uri", "http://127.0.0.1:8081/cb")
                        .queryParam("scope", "openid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectTheTokenEndpointWithAWrongClientSecret() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/oauth2/token")
                        .header("Authorization", "Basic " + java.util.Base64.getEncoder()
                                .encodeToString("splitmate-web:wrong".getBytes()))
                        .param("grant_type", "authorization_code")
                        .param("code", "x"))
                .andExpect(status().isUnauthorized());
    }

    private String codeFor(String clientId, Pkce pkce, MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(authorize(clientId, pkce).session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String code = queryParam(result.getResponse().getRedirectedUrl(), "code");
        assertThat(code).as("authorization code in " + result.getResponse().getRedirectedUrl()).isNotBlank();
        return code;
    }
}
