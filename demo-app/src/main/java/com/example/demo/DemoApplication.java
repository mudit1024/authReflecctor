package com.example.demo;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.authreflector.client.AuthReflectorSignInEvent;
import com.authreflector.client.AuthReflectorUser;

// No security code here: the AuthReflector starter handles sign-in, sign-out, sessions, tokens and CSRF
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @RestController
    static class HelloController {

        // Signed-in users only (everything outside the public paths requires sign-in)
        @GetMapping("/api/hello")
        Map<String, Object> hello(AuthReflectorUser user) {
            return Map.of(
                    "message", "Hello " + user.name() + ", this came from the demo app's own API",
                    "userId", user.id(),
                    "roles", user.roles());
        }
    }

    // Where a real app would create or update its own user row (just-in-time provisioning)
    @Component
    static class SignInListener {
        private static final Logger log = LoggerFactory.getLogger(SignInListener.class);

        @EventListener
        void onSignIn(AuthReflectorSignInEvent event) {
            log.info("Signed in through AuthReflector: {} ({})", event.user().email(), event.user().id());
        }
    }
}
