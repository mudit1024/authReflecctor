package com.authreflector.apps;

import static com.authreflector.oidc.OidcTestSupport.queryParam;
import static com.authreflector.oidc.OidcTestSupport.signIn;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.authreflector.IntegrationTest;
import com.authreflector.apps.AppDtos.AppRequest;
import com.authreflector.identity.User;
import com.authreflector.identity.UserRepository;
import com.authreflector.oidc.OidcTestSupport.Pkce;

/** Frontend-only apps (JS SDK): public client in the browser, so the token endpoint is called cross-origin. */
@IntegrationTest
@Transactional
class SinglePageAppIntegrationTest {

    private static final String APP_ORIGIN = "http://127.0.0.1:5175";
    private static final String REDIRECT = APP_ORIGIN + "/";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AppRegistry registry;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        userRepository.saveAndFlush(new User("Asha", "asha@example.com", passwordEncoder.encode("Password@123")));
        registry.create(new AppRequest(AppType.SPA, "pincode", "Pincode", null, List.of(REDIRECT), List.of(REDIRECT),
                REDIRECT, null, null, true, true));
        registry.create(new AppRequest(AppType.WEB, "splitmate-web", "SplitMate", "splitmate-api",
                List.of("http://127.0.0.1:5173/login/oauth2/code/authreflector"), List.of(), null, null, null, true, true));
    }

    // The browser asks first (preflight): only registered frontend-only apps' origins are allowed
    @Test
    void shouldAllowCrossOriginTokenRequestsFromRegisteredSpaOrigins() throws Exception {
        mockMvc.perform(options("/oauth2/token")
                        .header("Origin", APP_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", APP_ORIGIN));
    }

    @Test
    void shouldRefuseCrossOriginRequestsFromUnknownOrigins() throws Exception {
        mockMvc.perform(options("/oauth2/token")
                        .header("Origin", "https://attacker.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // Web apps exchange codes from their server, never from the browser: no CORS for them
    @Test
    void shouldNotOpenCorsForWebAppOrigins() throws Exception {
        mockMvc.perform(options("/oauth2/token")
                        .header("Origin", "http://127.0.0.1:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // A new frontend-only app gets CORS immediately, no restart
    @Test
    void shouldAllowANewlyRegisteredAppsOriginImmediately() throws Exception {
        registry.create(new AppRequest(AppType.SPA, "embed-poc", "Embeddings POC", null,
                List.of("http://127.0.0.1:5500/index.html"), List.of(), null, null, null, true, true));

        mockMvc.perform(options("/oauth2/token")
                        .header("Origin", "http://127.0.0.1:5500")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5500"));
    }

    // The whole browser flow: no secret, PKCE only, tokens returned with CORS headers
    @Test
    void shouldIssueTokensToAPublicClientWithPkceOnly() throws Exception {
        MockHttpSession session = signIn(mockMvc, "asha@example.com", "Password@123", new MockHttpSession());
        Pkce pkce = Pkce.create();
        MvcResult authorized = mockMvc.perform(get("/oauth2/authorize").session(session)
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "pincode")
                        .queryParam("redirect_uri", REDIRECT)
                        .queryParam("scope", "openid profile email")
                        .queryParam("state", "s")
                        .queryParam("code_challenge", pkce.challenge())
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String code = queryParam(authorized.getResponse().getRedirectedUrl(), "code");

        mockMvc.perform(post("/oauth2/token")
                        .header("Origin", APP_ORIGIN)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", "pincode")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT)
                        .param("code_verifier", pkce.verifier()))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", APP_ORIGIN))
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.id_token").isNotEmpty())
                // No refresh tokens in the browser: the SDK silently re-checks the SSO session instead
                .andExpect(jsonPath("$.refresh_token").doesNotExist());
    }

    // The SDK file is public (apps load it with a <script> tag); it's only present in -Pui builds
    @Test
    void shouldNotRequireSignInForTheSdkFile() throws Exception {
        mockMvc.perform(get("/sdk/authreflector-web.js"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
    }
}
