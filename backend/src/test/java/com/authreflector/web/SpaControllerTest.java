package com.authreflector.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.authreflector.IntegrationTest;

@IntegrationTest
class SpaControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // A refresh or deep link on a React route must load the React app, not a 404
    @ParameterizedTest
    @ValueSource(strings = {"/", "/login", "/register", "/account"})
    void shouldServeTheReactAppForItsRoutes(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    // No catch-all: unknown server paths stay real 404s (a mistyped API URL must not return HTML)
    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/does-not-exist", "/something-else"})
    void shouldNotServeTheReactAppForUnknownPaths(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isNotFound());
    }
}
