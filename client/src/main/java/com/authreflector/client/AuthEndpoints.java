package com.authreflector.client;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The few calls a React frontend needs. Sign-out is POST /auth/logout (Spring Security's logout filter). */
@RestController
public class AuthEndpoints {

    /** Starts sign-in: a link or button can simply point here. */
    @GetMapping("/auth/login")
    public ResponseEntity<Void> login() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", AuthReflectorClientAutoConfiguration.AUTHORIZATION_START)
                .build();
    }

    /** Who is signed in (401 when nobody). */
    @GetMapping("/auth/me")
    public AuthReflectorUser me(Authentication authentication) {
        return AuthReflectorUser.from(authentication);
    }

    /** The CSRF token to send with POST/PUT/DELETE from the browser (header name included). */
    @GetMapping("/auth/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }
}
