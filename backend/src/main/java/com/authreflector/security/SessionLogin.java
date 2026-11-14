package com.authreflector.security;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Signs a user in from our own code (used right after registration). Does what Spring Security's form
 * login does for us at POST /login: check the password, protect against session fixation, store the
 * authentication in the session, and rotate the CSRF token.
 */
@Component
public class SessionLogin {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final PostLoginRedirect postLoginRedirect;
    private final SessionRegistry sessionRegistry;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public SessionLogin(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository,
            PostLoginRedirect postLoginRedirect,
            SessionRegistry sessionRegistry) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.postLoginRedirect = postLoginRedirect;
        this.sessionRegistry = sessionRegistry;
    }

    /** @return where the browser should go next */
    public String signIn(String email, String password, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, password));

        // Session fixation protection: a session id that existed before login must not become a logged-in session.
        // changeSessionId keeps the attributes (e.g. the saved request of the app that sent the user here).
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }

        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        // Same as form login: register the session so OIDC logout ("sid") can find it
        sessionRegistry.registerNewSession(request.getSession().getId(), authentication.getPrincipal());

        // New session, new CSRF token (same as Spring's CsrfAuthenticationStrategy after form login)
        CsrfToken newToken = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(newToken, request, response);

        return postLoginRedirect.resolve(request, response);
    }
}
