package com.authreflector.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Ported from SplitMate (password policy D8/D31). The React sign-up form shows the same rules live
// (frontend/src/lib/passwordPolicy.ts); this server-side check is the one that counts.
public record RegistrationRequest(
        @NotBlank(message = "Name must not be blank")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be valid")
        @Size(max = 254, message = "Email must be at most 254 characters")
        String email,

        @NotBlank(message = "Password must not be blank")
        @Size(min = 8, max = 64, message = "Password must be between 8 and 64 characters")
        // Lookaheads: at least one lowercase, uppercase, digit and special (non-alphanumeric, non-space) character
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).*$",
                message = "Password must contain at least one lowercase letter, one uppercase letter, one number and one special character")
        String password
) {

    // Never print the password, e.g. if a request object ends up in a log line
    @Override
    public String toString() {
        return "RegistrationRequest[name=" + name + ", email=" + email + ", password=****]";
    }
}
