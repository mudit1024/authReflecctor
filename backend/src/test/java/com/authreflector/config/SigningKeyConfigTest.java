package com.authreflector.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.authreflector.IntegrationTest;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

@IntegrationTest
class SigningKeyConfigTest {

    @Autowired
    private JWKSource<SecurityContext> jwkSource;

    @Test
    void shouldExposeTheConfiguredSigningKeyAsJwkSource() throws Exception {

        // The authorization server (A1) signs tokens with this source and publishes its public part at /oauth2/jwks
        List<JWK> keys = jwkSource.get(new JWKSelector(new JWKMatcher.Builder().build()), null);

        assertThat(keys).hasSize(1);
        assertThat(keys.getFirst().getKeyID()).isEqualTo("test-key-1");
    }
}
