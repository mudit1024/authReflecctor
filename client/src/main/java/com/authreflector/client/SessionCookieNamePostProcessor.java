package com.authreflector.client;

import java.util.Locale;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Gives each app its own session cookie name (e.g. SPLITMATE_WEB_SESSION) unless it sets one itself.
 * Browsers share cookies across ports on localhost, so two apps both using JSESSIONID would keep
 * overwriting each other's sessions.
 */
public class SessionCookieNamePostProcessor implements EnvironmentPostProcessor {

    static final String COOKIE_NAME = "server.servlet.session.cookie.name";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String clientId = environment.getProperty("authreflector.client.client-id");
        if (clientId == null || clientId.isBlank() || environment.containsProperty(COOKIE_NAME)) {
            return;
        }
        String name = clientId.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_") + "_SESSION";
        // Lowest precedence: anything the app configures still wins
        environment.getPropertySources().addLast(new MapPropertySource("authreflectorSessionCookie", Map.of(COOKIE_NAME, name)));
    }
}
