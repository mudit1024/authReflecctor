package com.authreflector.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Ported from SplitMate: one injectable clock, so time-dependent code (token expiry, error timestamps)
// can be tested with a fixed clock
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
