package com.authreflector.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.authreflector.IntegrationTest;

// NEW: the whole path against a real PostgreSQL (SplitMate covered this manually in Postman, appendix B.2)
@IntegrationTest
@Transactional // each test is rolled back, so tests don't see each other's users
class RegistrationIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void shouldStoreNormalizedUserWithHashedPassword() throws Exception {

        register("  Rahul ", "Rahul@Example.com", "Password@123")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Rahul"))
                .andExpect(jsonPath("$.email").value("rahul@example.com"));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select name, email, password_hash, role from users where email = 'rahul@example.com'");
        assertThat(row.get("name")).isEqualTo("Rahul");
        assertThat(row.get("role")).isEqualTo("USER");
        assertThat((String) row.get("password_hash")).startsWith("{bcrypt}$2").doesNotContain("Password@123");
    }

    @Test
    void shouldRejectTheSameEmailInDifferentCase() throws Exception {

        register("Rahul", "rahul@example.com", "Password@123").andExpect(status().isCreated());

        register("Rahul", "RAHUL@EXAMPLE.COM", "Password@123")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    // Defence in depth: even code that bypasses the service can't create a case-variant duplicate
    @Test
    void shouldEnforceCaseInsensitiveUniqueEmailInTheDatabase() {

        userRepository.saveAndFlush(new User("A", "same@example.com", "{bcrypt}x"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(new User("B", "SAME@example.com", "{bcrypt}x")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldFindExistingEmailIgnoringCase() {

        userRepository.saveAndFlush(new User("A", "case@example.com", "{bcrypt}x"));

        assertThat(userRepository.existsByEmailIgnoreCase("CASE@Example.com")).isTrue();
        assertThat(userRepository.existsByEmailIgnoreCase("other@example.com")).isFalse();
    }

    private org.springframework.test.web.servlet.ResultActions register(String name, String email, String password)
            throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "email": "%s", "password": "%s"}
                        """.formatted(name, email, password)));
    }
}
