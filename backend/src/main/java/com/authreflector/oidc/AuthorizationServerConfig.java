package com.authreflector.oidc;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.authreflector.apps.EnabledAppsClientRepository;
import com.authreflector.launcher.UsageRecordingAuthorizationService;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.json.JsonMapper;

/**
 * The OpenID Connect provider: /oauth2/authorize, /oauth2/token, /oauth2/jwks, /oauth2/revoke,
 * /.well-known/openid-configuration, /userinfo, /connect/logout.
 *
 * It has its own filter chain (order 1) that matches only those endpoints; everything else (our pages
 * and API) is handled by SecurityConfig (order 2). Both share the same HTTP session, which is the SSO session.
 */
@Configuration
@EnableConfigurationProperties(AuthorizationServerConfig.IssuerProperties.class)
public class AuthorizationServerConfig {

    /** @param issuer the public base URL of AuthReflector; written into every token as "iss" */
    @ConfigurationProperties(prefix = "authreflector")
    @org.springframework.validation.annotation.Validated
    public record IssuerProperties(@NotBlank String issuer) {
    }

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            RequestCache requestCache) throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServer = new OAuth2AuthorizationServerConfigurer();

        http
                .securityMatcher(authorizationServer.getEndpointsMatcher())
                .with(authorizationServer, server -> server.oidc(Customizer.withDefaults()))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                // Same session storage and saved-request handling as our login page (SecurityConfig)
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .requestCache(cache -> cache.requestCache(requestCache))
                // Not signed in yet: remember the app's request and show our React login page
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/oauth2/authorize")));

        return http.build();
    }

    // Registered apps live in the database: adding one never needs a restart (read on every request)
    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbc) {
        // Disabled apps (admin page) are treated as unknown: they can't start a sign-in or get tokens
        return new EnabledAppsClientRepository(new JdbcRegisteredClientRepository(jdbc), jdbc);
    }

    // Authorization codes and tokens in flight (needed for code exchange, refresh, revoke, userinfo)
    @Bean
    public OAuth2AuthorizationService authorizationService(
            JdbcTemplate jdbc, RegisteredClientRepository clients, java.time.Clock clock) {
        JdbcOAuth2AuthorizationService service = new JdbcOAuth2AuthorizationService(jdbc, clients);
        // Our signed-in principal is stored with each authorization: allowlist it for JSON (see AuthorizationJson)
        JsonMapper json = AuthorizationJson.mapper();
        service.setAuthorizationRowMapper(new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper(clients, json));
        service.setAuthorizationParametersMapper(new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationParametersMapper(json));
        // Layers around the normal store: refresh token reuse detection (D37), then launcher usage
        return new UsageRecordingAuthorizationService(new ReuseDetectingAuthorizationService(service, jdbc), jdbc, clock);
    }

    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(
            JdbcOperations jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationConsentService(jdbc, clients);
    }

    // A fixed issuer: tokens must say the same "iss" no matter which host name a request came in on
    @Bean
    public AuthorizationServerSettings authorizationServerSettings(IssuerProperties properties) {
        return AuthorizationServerSettings.builder().issuer(properties.issuer()).build();
    }

    // Verifies our own tokens where the server needs to read them (userinfo, id_token_hint on logout)
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }
}
