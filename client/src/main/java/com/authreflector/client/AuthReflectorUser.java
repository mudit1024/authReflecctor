package com.authreflector.client;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The signed-in user, the same whether they came through the browser (session) or with a bearer token.
 * Add it as a controller parameter: {@code @GetMapping("/api/x") Foo x(AuthReflectorUser user)}.
 *
 * @param id    stable user id (token "sub"): use it as the user key in the app's own tables
 * @param roles roles in this app (USER for everyone, more if granted on AuthReflector)
 */
public record AuthReflectorUser(UUID id, String email, String name, List<String> roles) {

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    /** null when the authentication didn't come from AuthReflector */
    public static AuthReflectorUser from(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            return fromClaims(oidcUser.getClaims());
        }
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return fromClaims(jwt.getToken().getClaims());
        }
        return null;
    }

    static AuthReflectorUser fromClaims(Map<String, Object> claims) {
        Object roles = claims.get("roles");
        List<String> roleList = roles instanceof Collection<?> values
                ? values.stream().map(String::valueOf).toList()
                : List.of();
        return new AuthReflectorUser(
                UUID.fromString(String.valueOf(claims.get("sub"))),
                (String) claims.get("email"),
                (String) claims.get("name"),
                roleList);
    }
}
