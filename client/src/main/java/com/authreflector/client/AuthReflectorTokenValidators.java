package com.authreflector.client;

import java.util.Collection;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;

/**
 * Rules for bearer tokens: signed by AuthReflector (checked by the decoder), not expired, issued by our
 * AuthReflector, and addressed to THIS app's API ("aud"). The audience check is what stops a token
 * issued for CodeQuror from working at SplitMate.
 */
public final class AuthReflectorTokenValidators {

    private AuthReflectorTokenValidators() {
    }

    public static OAuth2TokenValidator<Jwt> create(AuthReflectorClientProperties properties) {
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<Collection<String>>(
                JwtClaimNames.AUD, aud -> aud != null && aud.contains(properties.audience()));
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(properties.issuer()), audience);
    }
}
