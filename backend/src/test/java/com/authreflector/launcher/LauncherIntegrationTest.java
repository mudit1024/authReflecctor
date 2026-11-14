package com.authreflector.launcher;

import static com.authreflector.oidc.OidcTestSupport.queryParam;
import static com.authreflector.oidc.OidcTestSupport.signIn;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.authreflector.IntegrationTest;
import com.authreflector.apps.AppDtos.AppRequest;
import com.authreflector.apps.AppRegistry;
import com.authreflector.apps.AppType;
import com.authreflector.identity.User;
import com.authreflector.identity.UserRepository;
import com.authreflector.oidc.OidcTestSupport.Pkce;

@IntegrationTest
@Transactional
class LauncherIntegrationTest {

    private static final String PASSWORD = "Password@123";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AppRegistry registry;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;
    private MockHttpSession session;
    private UUID userId;
    private String splitMateSecret;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        userId = userRepository.saveAndFlush(new User("Asha", "asha@example.com", passwordEncoder.encode(PASSWORD))).getId();
        splitMateSecret = registry.create(app(AppType.WEB, "splitmate-web", "SplitMate", true, true)).clientSecret();
        registry.create(app(AppType.SPA, "codequror", "CodeQuror", true, true));
        registry.create(app(AppType.SPA, "pincode", "Pincode", true, true));
        registry.create(app(AppType.SPA, "hidden-tool", "Hidden tool", false, true));
        registry.create(app(AppType.SPA, "old-app", "Old app", true, false));
        session = signIn(mockMvc, "asha@example.com", PASSWORD, new MockHttpSession());
    }

    @Test
    void shouldRequireSignIn() throws Exception {
        mockMvc.perform(get("/api/v1/launcher")).andExpect(status().isUnauthorized());
    }

    // Hidden-from-launcher and disabled apps never show up
    @Test
    void shouldListOnlyEnabledAppsMarkedForTheLauncher() throws Exception {
        mockMvc.perform(get("/api/v1/launcher").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].clientId").value(org.hamcrest.Matchers.containsInAnyOrder("splitmate-web", "codequror", "pincode")))
                .andExpect(jsonPath("$[0].homeUrl").exists())
                .andExpect(jsonPath("$[0].color").exists());
    }

    // Glowing vs faded: a real sign-in into an app (code exchanged for tokens) marks it as used
    @Test
    void shouldMarkAnAppAsUsedOnceTheUserSignsInToIt() throws Exception {
        mockMvc.perform(get("/api/v1/launcher").session(session))
                .andExpect(jsonPath("$[?(@.clientId == 'splitmate-web')].used").value(false));

        signIntoSplitMate();

        mockMvc.perform(get("/api/v1/launcher").session(session))
                .andExpect(jsonPath("$[0].clientId").value("splitmate-web"))
                .andExpect(jsonPath("$[0].used").value(true))
                .andExpect(jsonPath("$[0].signInsLast30Days").value(1))
                .andExpect(jsonPath("$[0].lastUsedAt").exists());
    }

    // Most used (last 30 days) first, then most recent, then never-used apps by name
    @Test
    void shouldSortByRecentUseAndPushUnusedAppsDown() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        usage("pincode", today, 5);
        usage("pincode", today.minusDays(3), 2);
        usage("codequror", today.minusDays(1), 3);
        usage("splitmate-web", today.minusDays(60), 40); // used, but not recently

        mockMvc.perform(get("/api/v1/launcher").session(session))
                .andExpect(jsonPath("$[*].clientId").value(org.hamcrest.Matchers.contains("pincode", "codequror", "splitmate-web")))
                .andExpect(jsonPath("$[0].signInsLast30Days").value(7))
                .andExpect(jsonPath("$[2].used").value(true))
                .andExpect(jsonPath("$[2].signInsLast30Days").value(0));
    }

    @Test
    void shouldOrderNeverUsedAppsByName() throws Exception {
        mockMvc.perform(get("/api/v1/launcher").session(session))
                .andExpect(jsonPath("$[*].clientId").value(org.hamcrest.Matchers.contains("codequror", "pincode", "splitmate-web")))
                .andExpect(jsonPath("$[*].used").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(false))));
    }

    // A refresh is the app keeping its session alive, not a new visit
    @Test
    void shouldNotCountTokenRefreshesAsSignIns() throws Exception {
        String refreshToken = signIntoSplitMate();
        String basic = Base64.getEncoder().encodeToString(("splitmate-web:" + splitMateSecret).getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(post("/oauth2/token").header("Authorization", "Basic " + basic)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token").param("refresh_token", refreshToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/launcher").session(session))
                .andExpect(jsonPath("$[0].signInsLast30Days").value(1));
    }

    private String signIntoSplitMate() throws Exception {
        Pkce pkce = Pkce.create();
        String redirectUri = "http://127.0.0.1:5173/login/oauth2/code/authreflector";
        MvcResult authorized = mockMvc.perform(get("/oauth2/authorize").session(session)
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "splitmate-web")
                        .queryParam("redirect_uri", redirectUri)
                        .queryParam("scope", "openid profile email")
                        .queryParam("state", "s")
                        .queryParam("code_challenge", pkce.challenge())
                        .queryParam("code_challenge_method", "S256"))
                .andReturn();
        String code = queryParam(authorized.getResponse().getRedirectedUrl(), "code");
        String basic = Base64.getEncoder().encodeToString(("splitmate-web:" + splitMateSecret).getBytes(StandardCharsets.UTF_8));
        String body = mockMvc.perform(post("/oauth2/token").header("Authorization", "Basic " + basic)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code").param("code", code)
                        .param("redirect_uri", redirectUri).param("code_verifier", pkce.verifier()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.refresh_token");
    }

    private void usage(String clientId, LocalDate day, int signIns) {
        jdbc.update("insert into user_app_daily_usage (user_id, client_id, day, sign_ins, last_sign_in) values (?, ?, ?, ?, ?)",
                userId, clientId, day, signIns, java.sql.Timestamp.from(day.atStartOfDay().toInstant(ZoneOffset.UTC).plusSeconds(3600)));
    }

    private static AppRequest app(AppType type, String clientId, String name, boolean showInLauncher, boolean enabled) {
        String base = "http://127.0.0.1:5173/";
        List<String> redirect = type == AppType.WEB ? List.of(base + "login/oauth2/code/authreflector") : List.of(base);
        return new AppRequest(type, clientId, name, null, redirect, List.of(base), base, name + " description",
                "#10B981", showInLauncher, enabled);
    }
}
