package com.authreflector.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.authreflector.IntegrationTest;
import com.authreflector.identity.User;
import com.authreflector.identity.UserRepository;

import jakarta.servlet.http.Cookie;

/**
 * The browser protocol the React app uses, end to end: real CSRF cookie + header, form login,
 * session cookie, /api/v1/me, logout.
 *
 * Deliberately NOT using spring-security-test's csrf()/formLogin() helpers: they swap the CSRF
 * repository inside the shared filter for a session-based one, so later tests would no longer run
 * against our real cookie-based CSRF setup.
 */
@IntegrationTest
@Transactional
class LoginSessionIntegrationTest {

    private static final String EMAIL = "asha@example.com";
    private static final String PASSWORD = "Password@123";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        userRepository.saveAndFlush(new User("Asha", EMAIL, passwordEncoder.encode(PASSWORD)));
    }

    // ---------- login ----------

    @Test
    void shouldLogInWithEmailAndPasswordAndSayWhereToGoNext() throws Exception {
        mockMvc.perform(login(EMAIL, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectUrl").value("/account"));
    }

    @Test
    void shouldAcceptTheEmailInAnyCase() throws Exception {
        mockMvc.perform(login("ASHA@Example.com", PASSWORD))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectWrongPasswordWithGenericMessage() throws Exception {
        mockMvc.perform(login(EMAIL, "Wrong@12345"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    // Same answer as a wrong password: the login form must not reveal which emails have accounts
    @Test
    void shouldRejectUnknownEmailWithTheSameGenericMessage() throws Exception {
        mockMvc.perform(login("nobody@example.com", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void shouldRejectLoginWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("CSRF")));
    }

    // A token from another "site" (wrong value) must not work either
    @Test
    void shouldRejectLoginWithMismatchedCsrfToken() throws Exception {
        mockMvc.perform(post("/login")
                        .cookie(new Cookie("XSRF-TOKEN", "real-token"))
                        .header("X-XSRF-TOKEN", "forged-token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().isForbidden());
    }

    // Session fixation: an attacker who planted a session id before login must not end up with a logged-in session
    @Test
    void shouldIssueANewSessionIdOnLogin() throws Exception {
        MockHttpSession before = new MockHttpSession();
        String idBefore = before.getId();

        MvcResult result = mockMvc.perform(login(EMAIL, PASSWORD).session(before))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNotNull();
        assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(idBefore);
    }

    // ---------- who am I ----------

    @Test
    void shouldReturn401JsonForMeWhenNotSignedIn() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void shouldReturnTheSignedInUserFromTheSession() throws Exception {
        MockHttpSession session = loginAndGetSession();

        mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Asha"))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    // ---------- logout ----------

    @Test
    void shouldEndTheSessionOnLogout() throws Exception {
        MockHttpSession session = loginAndGetSession();

        mockMvc.perform(post("/logout").session(session).with(realCsrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    // ---------- registration signs you in (decision: logged in automatically) ----------

    @Test
    void shouldSignInAutomaticallyAfterRegistration() throws Exception {
        MvcResult result = mockMvc.perform(register("ravi@example.com").with(realCsrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.redirectUrl").value("/account"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ravi@example.com"));
    }

    @Test
    void shouldRejectRegistrationWithoutCsrfToken() throws Exception {
        mockMvc.perform(register("ravi@example.com"))
                .andExpect(status().isForbidden());
    }

    // ---------- public vs protected ----------

    @Test
    void shouldServePublicPagesWithoutSignIn() throws Exception {
        for (String path : new String[] {"/", "/login", "/register", "/account", "/actuator/health"}) {
            mockMvc.perform(get(path)).andExpect(status().isOk());
        }
    }

    @Test
    void shouldIssueAReadableCsrfCookieForTheReactApp() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    @Test
    void shouldSendSecurityHeaders() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void shouldRequireAdminRoleForAdminApi() throws Exception {
        MockHttpSession session = loginAndGetSession();

        mockMvc.perform(get("/api/v1/admin/anything").session(session))
                .andExpect(status().isForbidden());
    }

    // ---------- helpers: what the React app does ----------

    /** Fetches a real CSRF token (cookie) and sends it back in the X-XSRF-TOKEN header. */
    private RequestPostProcessor realCsrf() throws Exception {
        Cookie token = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(token).as("XSRF-TOKEN cookie").isNotNull();
        return request -> {
            request.setCookies(token);
            request.addHeader("X-XSRF-TOKEN", token.getValue());
            return request;
        };
    }

    private MockHttpServletRequestBuilder login(String email, String password) throws Exception {
        return post("/login")
                .with(realCsrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", email)
                .param("password", password);
    }

    private MockHttpServletRequestBuilder register(String email) {
        return post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Ravi", "email": "%s", "password": "Password@123"}
                        """.formatted(email));
    }

    private MockHttpSession loginAndGetSession() throws Exception {
        MvcResult result = mockMvc.perform(login(EMAIL, PASSWORD)).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
