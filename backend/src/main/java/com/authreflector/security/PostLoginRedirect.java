package com.authreflector.security;

import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Where to go after signing in. When an app sent the user here (OIDC, next ticket), Spring Security saved
 * that request in the session before showing the login page; we continue it. Otherwise: the account page.
 * The URL only ever comes from our own session, never from request input, so this can't be an open redirect.
 */
@Component
public class PostLoginRedirect {

    static final String DEFAULT_TARGET = "/account";

    private final RequestCache requestCache;

    public PostLoginRedirect(RequestCache requestCache) {
        this.requestCache = requestCache;
    }

    public String resolve(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest == null) {
            return DEFAULT_TARGET;
        }
        requestCache.removeRequest(request, response);
        return savedRequest.getRedirectUrl();
    }
}
