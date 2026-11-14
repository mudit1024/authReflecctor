package com.authreflector.client;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Single sign-on with AuthReflector for a Spring Boot app, switched on by "authreflector.client.issuer-uri".
 *
 * - Browser (BFF): sign-in via OpenID Connect + PKCE; tokens stay on the server, the browser has a session cookie
 * - Bearer tokens (mobile, API clients): verified locally against AuthReflector's public keys, incl. audience
 * - One current user (AuthReflectorUser) for both, injectable into controllers; roles become ROLE_* authorities
 * - /auth/login, /auth/me, /auth/csrf, /auth/logout (single sign-out at AuthReflector)
 *
 * An app that defines its own SecurityFilterChain replaces the filter chain below (the other beans still apply).
 */
@AutoConfiguration(beforeName = {
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
        "org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration",
        "org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration",
        "org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration",
        "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration"})
@ConditionalOnProperty(prefix = "authreflector.client", name = "issuer-uri")
@EnableConfigurationProperties(AuthReflectorClientProperties.class)
public class AuthReflectorClientAutoConfiguration {

    static final String REGISTRATION_ID = "authreflector";
    static final String AUTHORIZATION_START = "/oauth2/authorization/" + REGISTRATION_ID;

    // AuthReflector's endpoints are known: no discovery call, so the app starts even if AuthReflector is down
    @Bean
    @ConditionalOnMissingBean
    public ClientRegistrationRepository clientRegistrationRepository(AuthReflectorClientProperties properties) {
        String issuer = properties.issuer();
        ClientRegistration registration = ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientName("AuthReflector")
                .clientId(properties.clientId())
                .clientSecret(properties.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .issuerUri(issuer)
                .authorizationUri(issuer + "/oauth2/authorize")
                .tokenUri(issuer + "/oauth2/token")
                .jwkSetUri(issuer + "/oauth2/jwks")
                // No userInfoUri: name, email and roles are already in the ID token (one call less per sign-in)
                .userNameAttributeName("sub")
                .providerConfigurationMetadata(Map.of("end_session_endpoint", issuer + "/connect/logout"))
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    // Bearer tokens: AuthReflector's public keys (downloaded once, cached) + issuer, expiry and audience rules
    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(AuthReflectorClientProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.issuer() + "/oauth2/jwks").build();
        decoder.setJwtValidator(AuthReflectorTokenValidators.create(properties));
        return decoder;
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain authReflectorSecurityFilterChain(
            HttpSecurity http,
            AuthReflectorClientProperties properties,
            ClientRegistrationRepository registrations,
            JwtDecoder jwtDecoder) throws Exception {

        DefaultOAuth2AuthorizationRequestResolver pkceResolver =
                new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        pkceResolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());

        JsonOidcLogoutSuccessHandler logoutSuccess = new JsonOidcLogoutSuccessHandler(registrations);
        logoutSuccess.setPostLogoutRedirectUri(properties.postLogoutRedirectUri());

        RequestMatcher bearerRequest = request -> {
            String header = request.getHeader("Authorization");
            return header != null && header.regionMatches(true, 0, "Bearer ", 0, 7);
        };

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(properties.publicPaths().toArray(String[]::new)).permitAll()
                        .requestMatchers("/auth/login", "/auth/csrf", "/login/oauth2/**", "/oauth2/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(login -> login
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(pkceResolver))
                        .userInfoEndpoint(userInfo -> userInfo.userAuthoritiesMapper(rolesFromIdToken()))
                        .defaultSuccessUrl(properties.loginSuccessUrl()))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(rolesFromAccessToken())))
                // Browser sessions need a CSRF token (session-stored; fetched via /auth/csrf).
                // Bearer requests carry no cookies, so CSRF doesn't apply to them.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(new HttpSessionCsrfTokenRepository())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(bearerRequest))
                .logout(logout -> logout
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults()
                                .matcher(org.springframework.http.HttpMethod.POST, "/auth/logout"))
                        .logoutSuccessHandler(logoutSuccess))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint(properties)));

        return http.build();
    }

    // API and /auth/* calls get 401 JSON; page visits are sent to sign in
    private static AuthenticationEntryPoint entryPoint(AuthReflectorClientProperties properties) {
        LoginUrlAuthenticationEntryPoint signIn = new LoginUrlAuthenticationEntryPoint(AUTHORIZATION_START);
        return (request, response, exception) -> {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (path.startsWith(properties.apiPathPrefix()) || path.startsWith("/auth/")) {
                writeJson(response, 401, "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Sign in required\",\"path\":\""
                        + path.replace("\"", "") + "\"}");
            } else {
                signIn.commence(request, response, exception);
            }
        };
    }

    // "roles" claim of the ID token -> ROLE_USER, ROLE_ADMIN, ... (so hasRole('ADMIN') works)
    private static GrantedAuthoritiesMapper rolesFromIdToken() {
        return authorities -> {
            Set<GrantedAuthority> mapped = new HashSet<>(authorities);
            for (GrantedAuthority authority : authorities) {
                if (authority instanceof OidcUserAuthority oidc
                        && oidc.getIdToken().getClaim("roles") instanceof List<?> roles) {
                    roles.forEach(role -> mapped.add(new SimpleGrantedAuthority("ROLE_" + role)));
                }
            }
            return mapped;
        };
    }

    // "roles" claim of a bearer token -> ROLE_* authorities
    private static JwtAuthenticationConverter rolesFromAccessToken() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public AuthEndpoints authReflectorAuthEndpoints() {
        return new AuthEndpoints();
    }

    // Lets controllers take "AuthReflectorUser user" as a parameter
    @Bean
    public WebMvcConfigurer authReflectorUserArgumentResolver() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return AuthReflectorUser.class.equals(parameter.getParameterType());
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        return AuthReflectorUser.from(SecurityContextHolder.getContext().getAuthentication());
                    }
                });
            }
        };
    }

    @Bean
    public SignInEvents authReflectorSignInEvents(ApplicationEventPublisher publisher) {
        return new SignInEvents(publisher);
    }

    /** Turns Spring Security's "interactive sign-in succeeded" into an AuthReflectorSignInEvent for the app. */
    public static class SignInEvents {
        private final ApplicationEventPublisher publisher;

        SignInEvents(ApplicationEventPublisher publisher) {
            this.publisher = publisher;
        }

        @EventListener
        public void onInteractiveSignIn(InteractiveAuthenticationSuccessEvent event) {
            if (event.getAuthentication() instanceof OAuth2AuthenticationToken token) {
                AuthReflectorUser user = AuthReflectorUser.from(token);
                if (user != null) {
                    publisher.publishEvent(new AuthReflectorSignInEvent(user));
                }
            }
        }
    }

    /**
     * Single sign-out for a JavaScript frontend: instead of an HTTP redirect (which fetch() can't follow
     * across sites), answer with the URL the browser should open to end the AuthReflector session.
     */
    static class JsonOidcLogoutSuccessHandler extends OidcClientInitiatedLogoutSuccessHandler {

        JsonOidcLogoutSuccessHandler(ClientRegistrationRepository registrations) {
            super(registrations);
        }

        @Override
        public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                org.springframework.security.core.Authentication authentication) throws IOException {
            String url = determineTargetUrl(request, response, authentication);
            writeJson(response, 200, "{\"logoutUrl\":\"" + url.replace("\"", "%22") + "\"}");
        }
    }

    private static void writeJson(HttpServletResponse response, int status, String json) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(json);
    }
}
