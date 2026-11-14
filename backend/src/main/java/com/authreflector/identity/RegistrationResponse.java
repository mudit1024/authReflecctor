package com.authreflector.identity;

// Name and email only: a password or hash is never returned
public record RegistrationResponse(String name, String email) {
}
