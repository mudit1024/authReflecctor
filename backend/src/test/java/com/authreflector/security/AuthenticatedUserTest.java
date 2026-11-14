package com.authreflector.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import com.authreflector.identity.Role;
import com.authreflector.identity.User;

class AuthenticatedUserTest {

    @Test
    void shouldUseEmailAsUsernameAndRoleAsAuthority() {

        AuthenticatedUser principal = AuthenticatedUser.from(user(Role.USER));

        assertThat(principal.getUsername()).isEqualTo("asha@example.com");
        assertThat(principal.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
    }

    @Test
    void shouldMapAdminRoleToRoleAdminAuthority() {

        AuthenticatedUser principal = AuthenticatedUser.from(user(Role.ADMIN));

        assertThat(principal.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
    }

    // The principal lives in the session for days: the password hash must not stay in it after login
    @Test
    void shouldForgetThePasswordHashWhenCredentialsAreErased() {

        AuthenticatedUser principal = AuthenticatedUser.from(user(Role.USER));
        assertThat(principal.getPassword()).isEqualTo("{bcrypt}hash");

        principal.eraseCredentials();

        assertThat(principal.getPassword()).isNull();
    }

    @Test
    void shouldNotPrintThePasswordHash() {

        assertThat(AuthenticatedUser.from(user(Role.USER)).toString()).doesNotContain("hash");
    }

    private static User user(Role role) {
        User user = new User("Asha", "asha@example.com", "{bcrypt}hash");
        ReflectionTestUtils.setField(user, "id", UUID.fromString("8f14e45f-ceea-467f-a0e6-1b2c3d4e5f60"));
        ReflectionTestUtils.setField(user, "role", role);
        return user;
    }
}
