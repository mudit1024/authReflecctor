package com.authreflector;

import org.springframework.boot.SpringApplication;

public class TestAuthreflectorApplication {

    public static void main(String[] args) {
        SpringApplication.from(AuthreflectorApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
