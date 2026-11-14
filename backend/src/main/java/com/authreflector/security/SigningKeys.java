package com.authreflector.security;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;

import com.authreflector.config.SigningKeyProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

/**
 * Loads and checks the token signing key pair. Every problem fails at startup with a message that says
 * what to do: a server that starts with a broken key would issue tokens that no app can verify.
 */
public final class SigningKeys {

    private static final String HOW_TO_FIX =
            "Set AUTH_SIGNING_PUBLIC_KEY / AUTH_SIGNING_PRIVATE_KEY, or for local development run scripts/generate-dev-keys.sh";

    private SigningKeys() {
    }

    public static RSAKey load(SigningKeyProperties properties) {
        RSAPublicKey publicKey = read(properties.publicKeyLocation(), RsaKeyConverters.x509());
        RSAPrivateKey privateKey = read(properties.privateKeyLocation(), RsaKeyConverters.pkcs8());

        requireMatchingPair(publicKey, privateKey);

        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(properties.keyId())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
    }

    private static <K> K read(Resource location, Converter<InputStream, K> converter) {
        if (!location.exists()) {
            throw new IllegalStateException("Signing key not found at " + location.getDescription() + ". " + HOW_TO_FIX);
        }
        try (InputStream inputStream = location.getInputStream()) {
            K key = converter.convert(inputStream);
            if (key == null) {
                throw new IllegalStateException("Signing key at " + location.getDescription() + " is empty");
            }
            return key;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException(
                    "Cannot read signing key from " + location.getDescription()
                            + " (expected a PEM file: 'BEGIN PUBLIC KEY' or 'BEGIN PRIVATE KEY')",
                    exception);
        }
    }

    // In an RSA key pair, both halves share the same modulus
    private static void requireMatchingPair(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        boolean sameModulus = publicKey.getModulus().equals(privateKey.getModulus());
        boolean samePublicExponent = !(privateKey instanceof RSAPrivateCrtKey crtKey)
                || crtKey.getPublicExponent().equals(publicKey.getPublicExponent());

        if (!sameModulus || !samePublicExponent) {
            throw new IllegalStateException(
                    "The configured public and private signing keys do not belong together. " + HOW_TO_FIX);
        }
    }
}
