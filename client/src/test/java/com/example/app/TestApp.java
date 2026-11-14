package com.example.app;

import com.authreflector.client.AuthReflectorClientProperties;
import com.authreflector.client.AuthReflectorSignInEvent;
import com.authreflector.client.AuthReflectorTokenValidators;
import com.authreflector.client.AuthReflectorUser;

import java.util.Map;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Plays "an app that uses the starter" in the tests: no security code of its own. */
@SpringBootApplication
public class TestApp {

    @RestController
    static class AppEndpoints {

        @GetMapping("/api/hello")
        Map<String, Object> hello(AuthReflectorUser user) {
            return Map.of("id", user.id(), "email", user.email(), "roles", user.roles());
        }

        @PostMapping("/api/things")
        Map<String, String> create(AuthReflectorUser user) {
            return Map.of("createdBy", user.id().toString());
        }

        @GetMapping("/dashboard")
        String dashboard() {
            return "dashboard page";
        }
    }
}
