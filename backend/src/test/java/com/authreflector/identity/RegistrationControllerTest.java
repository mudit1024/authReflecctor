package com.authreflector.identity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.authreflector.config.ClockConfig;
import com.authreflector.security.SessionLogin;

// Ported from SplitMate RegistrationControllerTest (SPLIT-10).
// Web slice only: security filters are off here and covered by LoginSessionIntegrationTest.
@WebMvcTest(RegistrationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ClockConfig.class)
class RegistrationControllerTest {

    private static final String REGISTER_URL = "/api/v1/auth/register";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrationService registrationService;

    @MockitoBean
    private SessionLogin sessionLogin;

    @Test
    void shouldReturnCreatedWhenRegistrationSucceeds() throws Exception {

        when(registrationService.register(any(RegistrationRequest.class)))
                .thenReturn(new RegistrationResponse("Mudit", "mudit@example.com"));
        when(sessionLogin.signIn(any(), any(), any(), any())).thenReturn("/account");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Mudit", "email": "mudit@example.com", "password": "Password@123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Mudit"))
                .andExpect(jsonPath("$.email").value("mudit@example.com"))
                .andExpect(jsonPath("$.redirectUrl").value("/account"))
                .andExpect(jsonPath("$.password").doesNotExist());

        // NEW (AUTH-12): the new account is signed in with the password it was just created with
        verify(sessionLogin).signIn(org.mockito.ArgumentMatchers.eq("mudit@example.com"),
                org.mockito.ArgumentMatchers.eq("Password@123"), any(), any());
    }

    @Test
    void shouldReturnBadRequestWithFieldErrorsWhenRequestIsInvalid() throws Exception {

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "   ", "email": "not-an-email", "password": "weak"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.path").value(REGISTER_URL))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')]").exists());

        verify(registrationService, never()).register(any(RegistrationRequest.class));
    }

    @Test
    void shouldReturnBadRequestInStandardFormatWhenJsonIsMalformed() throws Exception {

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Mudit\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed JSON request"))
                .andExpect(jsonPath("$.path").value(REGISTER_URL));

        verify(registrationService, never()).register(any(RegistrationRequest.class));
    }

    @Test
    void shouldReturnConflictWhenEmailIsAlreadyRegistered() throws Exception {

        when(registrationService.register(any(RegistrationRequest.class)))
                .thenThrow(new EmailAlreadyRegisteredException("mudit@example.com"));

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Mudit", "email": "mudit@example.com", "password": "Password@123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Email is already registered"))
                // Generic message: the email is not echoed back
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("mudit@example.com"))));
    }
}
