package com.authreflector.apps;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import jakarta.servlet.http.HttpServletRequest;

/**
 * CORS for frontend-only apps (JS SDK). Their pages call AuthReflector's token endpoint from another origin,
 * which browsers block unless we answer with Access-Control-Allow-Origin.
 *
 * Allowed: exactly the origins of redirect URIs of enabled SPA apps, read from the database on each request
 * (so a newly registered app works without a restart), and only on the endpoints the browser needs.
 * Web apps (with a backend) never get CORS: they talk to the token endpoint from their server.
 */
public class SpaOriginsCorsConfigurationSource implements CorsConfigurationSource {

    private static final Set<String> BROWSER_ENDPOINTS = Set.of(
            "/oauth2/token", "/oauth2/jwks", "/oauth2/revoke", "/userinfo", "/.well-known/openid-configuration");

    private final JdbcTemplate jdbc;

    public SpaOriginsCorsConfigurationSource(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (origin == null || !BROWSER_ENDPOINTS.contains(path) || !spaOrigins().contains(origin)) {
            return null; // no CORS headers: the browser refuses the cross-origin call
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(origin));
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Authorization"));
        configuration.setAllowCredentials(false); // public clients send no cookies to these endpoints
        configuration.setMaxAge(600L);
        return configuration;
    }

    private Set<String> spaOrigins() {
        List<String> redirectUriLists = jdbc.queryForList("""
                select c.redirect_uris from oauth2_registered_client c
                join client_apps a on a.client_id = c.client_id
                where a.app_type = 'SPA' and a.enabled
                """, String.class);
        return redirectUriLists.stream()
                .filter(Objects::nonNull)
                .flatMap(list -> Stream.of(list.split(",")))
                .map(SpaOriginsCorsConfigurationSource::originOf)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static String originOf(String uri) {
        try {
            URI parsed = URI.create(uri.trim());
            return parsed.getScheme() + "://" + parsed.getHost() + (parsed.getPort() == -1 ? "" : ":" + parsed.getPort());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
