package com.authreflector.oidc;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

import com.authreflector.security.AuthenticatedUser;

/**
 * What every token says about the user. Replaces SplitMate's hand-written AccessTokenService:
 * the authorization server creates and signs the tokens, we only decide the claims.
 *
 * - sub:   the user's UUID (Spring's default would be the email, which can change; apps store sub as their key)
 * - email, name: so apps can show who is signed in without another call
 * - roles: roles in THIS app only (USER + grants from client_roles)
 * - aud (access token only): the app's API, so its tokens are useless at any other app's API.
 *   The ID token keeps aud = client id, as OpenID Connect requires.
 */
@Component
public class TokenClaimsCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final ClientRoles clientRoles;

    public TokenClaimsCustomizer(ClientRoles clientRoles) {
        this.clientRoles = clientRoles;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        if (!(context.getPrincipal().getPrincipal() instanceof AuthenticatedUser user)) {
            return; // not a user sign-in (e.g. a future machine-to-machine client)
        }
        String clientId = context.getRegisteredClient().getClientId();

        JwtClaimsSet.Builder claims = context.getClaims();
        claims.subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .claim("roles", clientRoles.rolesFor(user.getId(), clientId));

        if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            // Mutable list: immutable List.of(...) is not on the JSON allowlist used to store tokens
            List<String> audience = new ArrayList<>();
            audience.add(ClientDefaults.audienceOf(context.getRegisteredClient()));
            claims.audience(audience);
        }
    }
}
