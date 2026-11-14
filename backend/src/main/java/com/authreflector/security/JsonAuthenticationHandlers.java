package com.authreflector.security;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import com.authreflector.web.JsonErrorWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON answers for the React app instead of Spring Security's default HTML redirects:
 * login success → 200 {"redirectUrl": ...}; login failure → 401; not signed in → 401; not allowed → 403.
 */
@Component
public class JsonAuthenticationHandlers
        implements AuthenticationSuccessHandler, AuthenticationFailureHandler, AuthenticationEntryPoint, AccessDeniedHandler {

    // One message for unknown email and wrong password, so the form doesn't reveal who has an account
    static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final PostLoginRedirect postLoginRedirect;
    private final JsonErrorWriter errorWriter;
    private final JsonMapper jsonMapper;

    public JsonAuthenticationHandlers(PostLoginRedirect postLoginRedirect, JsonErrorWriter errorWriter, JsonMapper jsonMapper) {
        this.postLoginRedirect = postLoginRedirect;
        this.errorWriter = errorWriter;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), Map.of("redirectUrl", postLoginRedirect.resolve(request, response)));
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
    }

    // Not signed in
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, "Sign in required");
    }

    // Signed in but not allowed, or the CSRF token is missing/wrong
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        String message = exception instanceof CsrfException
                ? "Invalid or missing CSRF token. Reload the page and try again."
                : "You don't have permission to do this";
        errorWriter.write(request, response, HttpStatus.FORBIDDEN, message);
    }
}
