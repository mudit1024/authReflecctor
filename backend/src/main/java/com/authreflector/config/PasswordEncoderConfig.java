package com.authreflector.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

    // Hashes with BCrypt and stores them as "{bcrypt}$2a$10$...".
    // The "{id}" prefix (unlike SplitMate's plain BCryptPasswordEncoder) records which algorithm made each hash,
    // so a central identity provider can move to a stronger algorithm later (e.g. Argon2) and still verify
    // every existing password, upgrading hashes as users log in.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
