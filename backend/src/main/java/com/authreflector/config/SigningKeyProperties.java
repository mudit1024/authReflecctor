package com.authreflector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * The RSA key pair that signs every token AuthReflector issues. Bound from "authreflector.signing-key.*".
 *
 * @param keyId              the "kid" written into each token header; apps use it to pick the right
 *                           public key from /oauth2/jwks, which is what makes key rotation possible
 * @param publicKeyLocation  X.509 PEM ("BEGIN PUBLIC KEY"), e.g. file:secrets/dev-public.pem
 * @param privateKeyLocation PKCS#8 PEM ("BEGIN PRIVATE KEY"); never committed, comes from a secret store in production
 */
@Validated
@ConfigurationProperties(prefix = "authreflector.signing-key")
public record SigningKeyProperties(
        @NotBlank String keyId,
        @NotNull Resource publicKeyLocation,
        @NotNull Resource privateKeyLocation
) {
}
