package com.authreflector.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

// Ported from SplitMate RegistrationRequestValidationTest (SPLIT-10), plus length limits (NEW)
class RegistrationRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldRejectNullName() {
        assertEquals(1, validate(new RegistrationRequest(null, "mudit@example.com", "Password@123")).size());
    }

    @Test
    void shouldRejectMissingRequiredFields() {
        assertEquals(3, validate(new RegistrationRequest(null, null, null)).size());
    }

    @Test
    void shouldRejectInvalidEmailFormat() {
        assertEquals(1, validate(new RegistrationRequest("Mudit", "not-an-email", "Password@123")).size());
    }

    @Test
    void shouldRejectBlankPassword() {
        assertTrue(hasViolationOn("password", new RegistrationRequest("Mudit", "mudit@example.com", "   ")));
    }

    @Test
    void shouldRejectPasswordShorterThanEightCharacters() {
        assertEquals(1, validate(new RegistrationRequest("Mudit", "mudit@example.com", "Pa@1234")).size());
    }

    @Test
    void shouldRejectPasswordWithoutLowercaseCharacter() {
        assertTrue(hasViolationOn("password", new RegistrationRequest("Mudit", "mudit@example.com", "PASSWORD@123")));
    }

    @Test
    void shouldRejectPasswordWithoutUppercaseCharacter() {
        assertTrue(hasViolationOn("password", new RegistrationRequest("Mudit", "mudit@example.com", "password@123")));
    }

    @Test
    void shouldRejectPasswordWithoutNumericCharacter() {
        assertTrue(hasViolationOn("password", new RegistrationRequest("Mudit", "mudit@example.com", "Password@")));
    }

    @Test
    void shouldRejectPasswordWithoutSpecialCharacter() {
        assertTrue(hasViolationOn("password", new RegistrationRequest("Mudit", "mudit@example.com", "Password123")));
    }

    @Test
    void shouldAcceptPasswordMeetingAllRequirements() {
        assertTrue(validate(new RegistrationRequest("Mudit", "mudit@example.com", "Password@123")).isEmpty());
    }

    @Test
    void shouldRejectPasswordLongerThanSixtyFourCharacters() {
        // BCrypt only uses the first 72 bytes, so long passwords are capped
        String password = "Password@1" + "a".repeat(55);

        assertEquals(65, password.length());
        assertEquals(1, validate(new RegistrationRequest("Mudit", "mudit@example.com", password)).size());
    }

    @Test
    void shouldAcceptPasswordOfExactlySixtyFourCharacters() {
        String password = "Password@1" + "a".repeat(54);

        assertEquals(64, password.length());
        assertTrue(validate(new RegistrationRequest("Mudit", "mudit@example.com", password)).isEmpty());
    }

    // NEW: matches the users.name column (varchar 100), so an over-long name is a 400, not a database error
    @Test
    void shouldRejectNameLongerThanOneHundredCharacters() {
        assertTrue(hasViolationOn("name", new RegistrationRequest("a".repeat(101), "mudit@example.com", "Password@123")));
        assertTrue(validate(new RegistrationRequest("a".repeat(100), "mudit@example.com", "Password@123")).isEmpty());
    }

    // NEW: 254 characters is the practical maximum length of an email address (RFC 5321)
    @Test
    void shouldRejectEmailLongerThan254Characters() {
        String tooLong = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(60) + ".com";

        assertTrue(tooLong.length() > 254);
        assertTrue(hasViolationOn("email", new RegistrationRequest("Mudit", tooLong, "Password@123")));
    }

    private Set<ConstraintViolation<RegistrationRequest>> validate(RegistrationRequest request) {
        return validator.validate(request);
    }

    private boolean hasViolationOn(String field, RegistrationRequest request) {
        return validate(request).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals(field));
    }
}
