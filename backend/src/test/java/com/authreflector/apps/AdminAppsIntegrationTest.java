package com.authreflector.apps;

import static com.authreflector.oidc.OidcTestSupport.signIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.authreflector.IntegrationTest;
import com.authreflector.identity.Role;
import com.authreflector.identity.User;
import com.authreflector.identity.UserRepository;
import com.authreflector.oidc.ClientDefaults;
import com.authreflector.oidc.OidcTestSupport.Pkce;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

@IntegrationTest
@Transactional
class AdminAppsIntegrationTest {

    private static final String APPS = "/api/v1/admin/apps";
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

    private MockMvc mockMvc;
    private MockHttpSession admin;
    private MockHttpSession user;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        User adminAccount = new User("Owner", "owner@example.com", passwordEncoder.encode(PASSWORD));
        ReflectionTestUtils.setField(adminAccount, "role", Role.ADMIN); // granted by DB script in real life (D42)
        userRepository.saveAndFlush(adminAccount);
        userRepository.saveAndFlush(new User("Asha", "asha@example.com", passwordEncoder.encode(PASSWORD)));
        admin = signIn(mockMvc, "owner@example.com", PASSWORD, new MockHttpSession());
        user = signIn(mockMvc, "asha@example.com", PASSWORD, new MockHttpSession());
    }

    // ---------- who may manage apps ----------

    @Test
    void shouldAllowOnlyAdmins() throws Exception {
        mockMvc.perform(get(APPS)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(APPS).session(user)).andExpect(status().isForbidden());
        mockMvc.perform(get(APPS).session(admin)).andExpect(status().isOk());
    }

    // ---------- web apps (with a backend) ----------

    @Test
    void shouldCreateAWebAppAndShowItsSecretOnlyOnce() throws Exception {
        String body = create(splitMate()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.app.clientId").value("splitmate-web"))
                .andExpect(jsonPath("$.app.type").value("WEB"))
                .andExpect(jsonPath("$.clientSecret").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String secret = JsonPath.read(body, "$.clientSecret");

        // Never shown again, never stored readable
        mockMvc.perform(get(APPS + "/splitmate-web").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSecret").doesNotExist())
                .andExpect(jsonPath("$.homeUrl").value("http://127.0.0.1:5173/"))
                .andExpect(jsonPath("$.showInLauncher").value(true));
        RegisteredClient stored = clients.findByClientId("splitmate-web");
        assertThat(stored.getClientSecret()).startsWith("{bcrypt}").doesNotContain(secret);
        assertThat(passwordEncoder.matches(secret, stored.getClientSecret())).isTrue();
        assertThat(secret.length()).isGreaterThanOrEqualTo(43); // 32 random bytes
    }

    @Test
    void shouldGiveNewAppsTheSafeDefaults() throws Exception {
        create(splitMate()).andExpect(status().isCreated());

        RegisteredClient stored = clients.findByClientId("splitmate-web");
        assertThat(stored.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(stored.getTokenSettings().isReuseRefreshTokens()).isFalse();
        assertThat(ClientDefaults.audienceOf(stored)).isEqualTo("splitmate-api");
        assertThat(stored.getRedirectUris()).containsExactly("http://127.0.0.1:5173/login/oauth2/code/authreflector");
    }

    // The whole point: a new app works immediately, no restart
    @Test
    void shouldLetANewAppSignUsersInImmediately() throws Exception {
        create(splitMate()).andExpect(status().isCreated());

        mockMvc.perform(authorize("splitmate-web", "http://127.0.0.1:5173/login/oauth2/code/authreflector").session(user))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("http://127.0.0.1:5173/login/oauth2/code/authreflector?code=")));
    }

    @Test
    void shouldRefuseADuplicateClientId() throws Exception {
        create(splitMate()).andExpect(status().isCreated());

        create(splitMate()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An app with this client id already exists"));
    }

    @Test
    void shouldValidateTheRequest() throws Exception {
        create("""
                {"type": "WEB", "clientId": "Bad Id!", "name": "", "audience": "x",
                 "redirectUris": ["http://evil.example/callback", "javascript:alert(1)"],
                 "homeUrl": "ftp://x", "color": "red"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("clientId")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("redirectUris")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("homeUrl")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("color")));
    }

    @Test
    void shouldReplaceTheSecretOnRequest() throws Exception {
        String oldSecret = JsonPath.read(create(splitMate()).andReturn().getResponse().getContentAsString(), "$.clientSecret");

        String body = mockMvc.perform(withCsrf(post(APPS + "/splitmate-web/secret")).session(admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newSecret = JsonPath.read(body, "$.clientSecret");

        // Wrong secret -> invalid_client (401); right secret gets past client authentication (then: bad code -> 400)
        tokenEndpoint("splitmate-web", oldSecret).andExpect(status().isUnauthorized());
        tokenEndpoint("splitmate-web", newSecret).andExpect(status().isBadRequest());
    }

    @Test
    void shouldUpdateAnAppAndApplyItImmediately() throws Exception {
        create(splitMate()).andExpect(status().isCreated());

        mockMvc.perform(withCsrf(put(APPS + "/splitmate-web")).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "WEB", "clientId": "splitmate-web", "name": "SplitMate (new)", "audience": "splitmate-api",
                                 "redirectUris": ["https://splitmate.example.com/login/oauth2/code/authreflector"],
                                 "postLogoutRedirectUris": ["https://splitmate.example.com/"],
                                 "homeUrl": "https://splitmate.example.com/", "description": "Split expenses",
                                 "color": "#0EA5E9", "showInLauncher": false, "enabled": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("SplitMate (new)"))
                .andExpect(jsonPath("$.showInLauncher").value(false));

        assertThat(clients.findByClientId("splitmate-web").getRedirectUris())
                .containsExactly("https://splitmate.example.com/login/oauth2/code/authreflector");
    }

    // ---------- disabling ----------

    @Test
    void shouldStopADisabledAppFromSigningAnyoneIn() throws Exception {
        create(splitMate()).andExpect(status().isCreated());
        jdbc.update("""
                insert into oauth2_authorization (id, registered_client_id, principal_name, authorization_grant_type)
                select 'existing-sign-in', id, 'asha@example.com', 'authorization_code'
                from oauth2_registered_client where client_id = 'splitmate-web'""");

        mockMvc.perform(withCsrf(put(APPS + "/splitmate-web")).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(splitMate().replace("\"enabled\": true", "\"enabled\": false")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(authorize("splitmate-web", "http://127.0.0.1:5173/login/oauth2/code/authreflector").session(user))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from oauth2_authorization", Integer.class)).isZero();
        // Still visible to admins, so it can be enabled again
        mockMvc.perform(get(APPS).session(admin)).andExpect(jsonPath("$[0].enabled").value(false));
    }

    // ---------- frontend-only apps ----------

    @Test
    void shouldCreateAFrontendOnlyAppAsAPublicClientWithoutSecret() throws Exception {
        create("""
                {"type": "SPA", "clientId": "pincode", "name": "What is my Pincode",
                 "redirectUris": ["http://127.0.0.1:5175/"], "postLogoutRedirectUris": ["http://127.0.0.1:5175/"],
                 "homeUrl": "http://127.0.0.1:5175/", "description": "Find any Indian pincode",
                 "color": "#F97316", "showInLauncher": true, "enabled": true}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.app.type").value("SPA"))
                .andExpect(jsonPath("$.clientSecret").doesNotExist());

        RegisteredClient stored = clients.findByClientId("pincode");
        assertThat(stored.getClientAuthenticationMethods()).containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(stored.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(stored.getClientSecret()).isNull();
    }

    @Test
    void shouldListAppsWithTheirLauncherDetails() throws Exception {
        create(splitMate()).andExpect(status().isCreated());

        mockMvc.perform(get(APPS).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clientId").value("splitmate-web"))
                .andExpect(jsonPath("$[0].name").value("SplitMate"))
                .andExpect(jsonPath("$[0].description").value("Split expenses. Settle smarter."))
                .andExpect(jsonPath("$[0].color").value("#10B981"))
                .andExpect(jsonPath("$[0].redirectUris[0]").value("http://127.0.0.1:5173/login/oauth2/code/authreflector"));
    }

    // ---------- helpers ----------

    private static String splitMate() {
        return """
                {"type": "WEB", "clientId": "splitmate-web", "name": "SplitMate", "audience": "splitmate-api",
                 "redirectUris": ["http://127.0.0.1:5173/login/oauth2/code/authreflector"],
                 "postLogoutRedirectUris": ["http://127.0.0.1:5173/"],
                 "homeUrl": "http://127.0.0.1:5173/", "description": "Split expenses. Settle smarter.",
                 "color": "#10B981", "showInLauncher": true, "enabled": true}
                """;
    }

    private ResultActions create(String json) throws Exception {
        return mockMvc.perform(withCsrf(post(APPS)).session(admin).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) throws Exception {
        Cookie token = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request.cookie(token).header("X-XSRF-TOKEN", token.getValue());
    }

    private static MockHttpServletRequestBuilder authorize(String clientId, String redirectUri) throws Exception {
        Pkce pkce = Pkce.create();
        return get("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "openid profile email")
                .queryParam("state", "s")
                .queryParam("code_challenge", pkce.challenge())
                .queryParam("code_challenge_method", "S256");
    }

    private ResultActions tokenEndpoint(String clientId, String secret) throws Exception {
        String basic = Base64.getEncoder().encodeToString((clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(post("/oauth2/token")
                .header("Authorization", "Basic " + basic)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", "not-a-real-code")
                .param("redirect_uri", "http://127.0.0.1:5173/login/oauth2/code/authreflector")
                .param("code_verifier", "x".repeat(43)));
    }
}
