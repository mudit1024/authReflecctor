package com.authreflector.oidc;

import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Tells the login page which app the user is signing in to ("to continue to SplitMate"), from the
 * authorize request that was saved in the session before the login page was shown.
 */
@RestController
public class LoginContextController {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoginContext(String clientName) {
    }

    private final RequestCache requestCache;
    private final RegisteredClientRepository clients;

    public LoginContextController(RequestCache requestCache, RegisteredClientRepository clients) {
        this.requestCache = requestCache;
        this.clients = clients;
    }

    @GetMapping("/api/v1/auth/login-context")
    public LoginContext loginContext(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest saved = requestCache.getRequest(request, response); // read only; login consumes it later
        if (saved == null) {
            return new LoginContext(null);
        }
        String[] clientIds = saved.getParameterValues("client_id");
        RegisteredClient client = (clientIds == null || clientIds.length == 0) ? null : clients.findByClientId(clientIds[0]);
        return new LoginContext(client == null ? null : client.getClientName());
    }
}
