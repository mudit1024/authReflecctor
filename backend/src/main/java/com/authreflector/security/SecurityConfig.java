package com.authreflector.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Security for AuthReflector's own pages and API.
 *
 * Unlike the apps (which verify JWTs and keep no session), AuthReflector is session-based on purpose
 * (design doc 5.2): the session is what lets every app sign the user in without a password prompt.
 * Because browsers send session cookies automatically, every state-changing request needs a CSRF token.
 */
@Configuration
public class SecurityConfig {

    public static final String SESSION_COOKIE = "AR_SESSION";

    // Only our own origin may provide scripts, styles, images and API calls; nobody may frame the login page
    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self'",
            "img-src 'self' data:",
            "font-src 'self'",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    // Order 2: the OIDC endpoints have their own chain (AuthorizationServerConfig, order 1)
    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository,
            RequestCache requestCache,
            SessionRegistry sessionRegistry,
            JsonAuthenticationHandlers jsonHandlers) throws Exception {

        // Load the CSRF token on every request, so the XSRF-TOKEN cookie is always there for the React app.
        // Plain (non-XOR) handler: the app sends the raw cookie value back in the X-XSRF-TOKEN header.
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName(null);

        http
                // Answers browser preflights (OPTIONS) for frontend-only apps; see SpaOriginsCorsConfigurationSource
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .authenticationManager(authenticationManager)
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .requestCache(cache -> cache.requestCache(requestCache))
                // Track sessions: OIDC logout checks the ID token's "sid" against them (no session limit)
                .sessionManagement(session -> session.sessionConcurrency(concurrency -> concurrency
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry)))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler))
                .authorizeHttpRequests(auth -> auth
                        // The React app's HTML and assets are public; the data behind them is not
                        .requestMatchers(HttpMethod.GET,
                                "/", "/login", "/register", "/account", "/admin/apps", "/admin/apps/*",
                                "/index.html", "/assets/**", "/favicon.svg").permitAll()
                        // The JS SDK for frontend-only apps, loaded with a <script> tag
                        .requestMatchers(HttpMethod.GET, "/sdk/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf", "/api/v1/auth/login-context").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Spring forwards errors to /error; it must stay reachable or every error becomes 401
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")              // GET: our React page
                        .loginProcessingUrl("/login")     // POST: handled by Spring Security
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(jsonHandlers)
                        .failureHandler(jsonHandlers)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .deleteCookies(SESSION_COOKIE))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(jsonHandlers)
                        .accessDeniedHandler(jsonHandlers))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN)));

        return http.build();
    }

    // Email + password check against the users table, with our {bcrypt} password encoder
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    // Where the signed-in state lives: the HTTP session (shared by form login and SessionLogin)
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }

    // XSRF-TOKEN cookie readable by our React app (same origin only); SameSite=Lax like the session cookie
    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        return repository;
    }

    // Which signed-in sessions exist. Needed for OpenID Connect logout: an app's ID token carries "sid",
    // and /connect/logout ends exactly that session
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    // Tells the registry about destroyed sessions and session-id changes (login changes the id)
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    // Only remember OIDC authorization requests (to continue them after login), never API calls
    @Bean
    public RequestCache requestCache() {
        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        cache.setRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/oauth2/authorize"));
        return cache;
    }
}
