package com.authreflector.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.authreflector.config.PasswordEncoderConfig;

// Ported from SplitMate RegistrationServiceTest (SPLIT-10), plus the hash format test (NEW)
class RegistrationServiceTest {

    private static final String NAME = "Mudit";
    private static final String EMAIL = "mudit@example.com";
    private static final String PASSWORD = "Password@123";

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private RegistrationService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = new PasswordEncoderConfig().passwordEncoder();
        service = new RegistrationService(userRepository, passwordEncoder);
    }

    @Test
    void shouldRegisterUserWithValidDetails() {

        RegistrationResponse response = service.register(new RegistrationRequest(NAME, EMAIL, PASSWORD));

        assertEquals(NAME, response.name());
        assertEquals(EMAIL, response.email());
        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    void shouldStorePasswordAsHash() {

        service.register(new RegistrationRequest(NAME, EMAIL, PASSWORD));

        User savedUser = captureSavedUser();
        assertNotEquals(PASSWORD, savedUser.getPasswordHash());
        assertTrue(passwordEncoder.matches(PASSWORD, savedUser.getPasswordHash()));
    }

    // NEW: the algorithm id prefix lets us move to a stronger algorithm later without breaking existing hashes
    @Test
    void shouldStoreHashWithAlgorithmPrefix() {

        service.register(new RegistrationRequest(NAME, EMAIL, PASSWORD));

        assertTrue(captureSavedUser().getPasswordHash().startsWith("{bcrypt}$2"));
    }

    @Test
    void shouldRejectDuplicateEmail() {

        when(userRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(true);
        RegistrationRequest request = new RegistrationRequest(NAME, EMAIL, PASSWORD);

        assertThrows(EmailAlreadyRegisteredException.class, () -> service.register(request));
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void shouldTrimNameBeforeRegistration() {

        RegistrationResponse response = service.register(new RegistrationRequest("  Mudit  ", EMAIL, PASSWORD));

        assertEquals("Mudit", response.name());
    }

    @Test
    void shouldNormalizeEmailBeforeRegistration() {

        RegistrationResponse response = service.register(
                new RegistrationRequest(NAME, "  Mudit@Example.COM ", PASSWORD));

        assertEquals(EMAIL, response.email());
        assertEquals(EMAIL, captureSavedUser().getEmail());
        verify(userRepository).existsByEmailIgnoreCase(EMAIL);
    }

    @Test
    void shouldRejectDuplicateEmailWhenConcurrentRegistrationWinsTheRace() {

        // The duplicate check passes, but another request inserts the same email first
        when(userRepository.existsByEmailIgnoreCase(EMAIL)).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));
        RegistrationRequest request = new RegistrationRequest(NAME, EMAIL, PASSWORD);

        assertThrows(EmailAlreadyRegisteredException.class, () -> service.register(request));
    }

    private User captureSavedUser() {
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        return userCaptor.getValue();
    }
}
