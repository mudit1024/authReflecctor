package com.authreflector.client;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Everything an app configures to use AuthReflector ("authreflector.client.*"). Usually four lines:
 * issuer-uri, client-id, client-secret, audience. The rest has sensible defaults.
 *
 * @param issuerUri              AuthReflector's base URL, e.g. http://localhost:9000
 * @param clientId               the app's client id from AuthReflector's admin page
 * @param clientSecret           the app's secret (shown once on the admin page); keep it in an env var
 * @param audience               the app's API name; bearer tokens must carry it in "aud"
 * @param apiPathPrefix          requests under this prefix get 401 JSON instead of a redirect to sign-in
 * @param publicPaths            reachable without signing in (Ant-style patterns)
 * @param postLogoutRedirectUri  where AuthReflector sends the browser after sign-out ({baseUrl} = this app)
 * @param loginSuccessUrl        where the browser lands after signing in (if no page was requested before)
 */
@ConfigurationProperties(prefix = "authreflector.client")
public record AuthReflectorClientProperties(
        String issuerUri,
        String clientId,
        String clientSecret,
        String audience,
        @DefaultValue("/api/") String apiPathPrefix,
        @DefaultValue({"/", "/index.html", "/assets/**", "/favicon.*", "/error", "/actuator/health"}) List<String> publicPaths,
        @DefaultValue("{baseUrl}/") String postLogoutRedirectUri,
        @DefaultValue("/") String loginSuccessUrl
) {

    /** issuerUri without a trailing slash */
    public String issuer() {
        return issuerUri.replaceAll("/+$", "");
    }
}
