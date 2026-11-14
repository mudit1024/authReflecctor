package com.authreflector.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import com.authreflector.identity.User;
import com.authreflector.security.AuthenticatedUser;

import tools.jackson.databind.json.JsonMapper;

class AuthorizationJsonTest {

    private final JsonMapper json = AuthorizationJson.mapper();

    // The signed-in principal is stored in oauth2_authorization.attributes: no password hash may end up there
    @Test
    void shouldStoreThePrincipalWithoutThePasswordHash() {

        String stored = json.writeValueAsString(signedIn());

        assertThat(stored).contains("asha@example.com").doesNotContain("{bcrypt}").doesNotContain("passwordHash");
    }

    @Test
    void shouldReadThePrincipalBack() {

        String stored = json.writeValueAsString(signedIn());
        Authentication restored = json.readValue(stored, Authentication.class);

        AuthenticatedUser principal = (AuthenticatedUser) restored.getPrincipal();
        assertThat(principal.getEmail()).isEqualTo("asha@example.com");
        assertThat(principal.getName()).isEqualTo("Asha");
        assertThat(principal.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
    }

    private static Authentication signedIn() {
        User user = new User("Asha", "asha@example.com", "{bcrypt}$2a$10$secret-hash");
        ReflectionTestUtils.setField(user, "id", UUID.fromString("8f14e45f-ceea-467f-a0e6-1b2c3d4e5f60"));
        AuthenticatedUser principal = AuthenticatedUser.from(user);
        principal.eraseCredentials();
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }
}
