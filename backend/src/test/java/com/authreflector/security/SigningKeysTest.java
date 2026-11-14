package com.authreflector.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.authreflector.config.SigningKeyProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

class SigningKeysTest {

    private static final SigningKeyProperties TEST_KEYS = new SigningKeyProperties(
            "test-key-1",
            new ClassPathResource("keys/test-public.pem"),
            new ClassPathResource("keys/test-private.pem"));

    @Test
    void shouldLoadKeyPairWithKeyIdForRs256Signing() throws Exception {

        RSAKey key = SigningKeys.load(TEST_KEYS);

        assertThat(key.getKeyID()).isEqualTo("test-key-1");
        assertThat(key.isPrivate()).isTrue();
        assertThat(key.getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(key.toRSAPublicKey().getModulus().bitLength()).isGreaterThanOrEqualTo(2048);
    }

    // Ported from SplitMate AccessTokenServiceTest.shouldUseRs256WithKeyIdInHeader
    @Test
    void shouldSignTokensWithRs256AndKeyIdInHeader() throws Exception {

        RSAKey key = SigningKeys.load(TEST_KEYS);

        Jwt jwt = decoderFor(key.toRSAPublicKey()).decode(sign(key));

        // Decoded headers hold plain strings, not the SignatureAlgorithm enum
        assertThat(jwt.getHeaders().get("alg")).hasToString("RS256");
        assertThat(jwt.getHeaders()).containsEntry("kid", "test-key-1");
        assertThat(jwt.getSubject()).isEqualTo("user-1");
    }

    // Ported from SplitMate AccessTokenServiceTest.shouldRejectTokenSignedWithAnotherKey
    @Test
    void shouldRejectTokenWhenVerifiedWithAnotherPublicKey() throws Exception {

        String token = sign(SigningKeys.load(TEST_KEYS));
        RSAKey otherKey = SigningKeys.load(new SigningKeyProperties(
                "other",
                new ClassPathResource("keys/other-public.pem"),
                new ClassPathResource("keys/other-private.pem")));

        JwtDecoder decoder = decoderFor(otherKey.toRSAPublicKey());

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    // Ported from SplitMate AccessTokenServiceTest.shouldRejectTokenWhosePayloadWasTampered
    @Test
    void shouldRejectTokenWhosePayloadWasTampered() throws Exception {

        RSAKey key = SigningKeys.load(TEST_KEYS);
        String[] parts = sign(key).split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"user-1\",\"role\":\"ADMIN\"}".getBytes(StandardCharsets.UTF_8));
        String forgedToken = parts[0] + "." + forgedPayload + "." + parts[2];

        JwtDecoder decoder = decoderFor(key.toRSAPublicKey());

        assertThatThrownBy(() -> decoder.decode(forgedToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void shouldFailFastWithHelpfulMessageWhenKeyFileIsMissing() {

        SigningKeyProperties missing = new SigningKeyProperties(
                "dev",
                new FileSystemResource("does-not-exist/public.pem"),
                new FileSystemResource("does-not-exist/private.pem"));

        assertThatThrownBy(() -> SigningKeys.load(missing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does-not-exist")
                .hasMessageContaining("generate-dev-keys");
    }

    @Test
    void shouldFailFastWhenPublicKeyDoesNotMatchPrivateKey() {

        // A mismatched pair would make every app reject every token: catch it at startup instead
        SigningKeyProperties mismatched = new SigningKeyProperties(
                "test-key-1",
                new ClassPathResource("keys/other-public.pem"),
                new ClassPathResource("keys/test-private.pem"));

        assertThatThrownBy(() -> SigningKeys.load(mismatched))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("do not belong together");
    }

    @Test
    void shouldFailFastWhenFileIsNotAPemKey() {

        SigningKeyProperties notAKey = new SigningKeyProperties(
                "test-key-1",
                new ClassPathResource("application-test.yaml"),
                new ClassPathResource("keys/test-private.pem"));

        assertThatThrownBy(() -> SigningKeys.load(notAKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("application-test.yaml");
    }

    private static String sign(RSAKey key) {
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("user-1")
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(5)))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static JwtDecoder decoderFor(RSAPublicKey publicKey) {
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }
}
