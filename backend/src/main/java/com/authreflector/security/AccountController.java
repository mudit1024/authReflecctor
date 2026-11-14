package com.authreflector.security;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController {

    public record MeResponse(UUID id, String name, String email, String role) {
    }

    // Who is signed in (from the session). 401 when nobody is: see SecurityConfig.
    @GetMapping("/api/v1/me")
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return new MeResponse(user.getId(), user.getName(), user.getEmail(), user.getRole().name());
    }

    // Does nothing itself: the CSRF filter sets the XSRF-TOKEN cookie on every response.
    // The React app calls this when it has no cookie yet (e.g. right after login rotated it).
    @GetMapping("/api/v1/auth/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf() {
        // intentionally empty
    }
}
