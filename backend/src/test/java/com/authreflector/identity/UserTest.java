package com.authreflector.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

// Ported from SplitMate UserTest (SPLIT-12)
class UserTest {

    @Test
    void shouldHaveUserRoleByDefault() {

        User user = new User("Mudit", "mudit@example.com", "hash");

        assertEquals(Role.USER, user.getRole());
    }
}
