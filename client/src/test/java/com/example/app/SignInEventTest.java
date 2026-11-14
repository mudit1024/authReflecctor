package com.example.app;

import com.authreflector.client.AuthReflectorClientProperties;
import com.authreflector.client.AuthReflectorSignInEvent;
import com.authreflector.client.AuthReflectorTokenValidators;
import com.authreflector.client.AuthReflectorUser;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

// Apps keep their own user table: the starter tells them whenever someone signs in (just-in-time provisioning)
@SpringBootTest(classes = TestApp.class)
@RecordApplicationEvents
class SignInEventTest {

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private ApplicationEvents events;

    @Test
    void shouldPublishWhoSignedInAfterABrowserSignIn() {
        UUID id = UUID.randomUUID();
        OidcIdToken idToken = OidcIdToken.withTokenValue("t").subject(id.toString())
                .claim("email", "asha@example.com").claim("name", "Asha").claim("roles", List.of("USER"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        DefaultOidcUser user = new DefaultOidcUser(List.of(new OidcUserAuthority(idToken)), idToken, "sub");

        publisher.publishEvent(new InteractiveAuthenticationSuccessEvent(
                new OAuth2AuthenticationToken(user, user.getAuthorities(), "authreflector"), getClass()));

        assertThat(events.stream(AuthReflectorSignInEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.user().id()).isEqualTo(id);
                    assertThat(event.user().email()).isEqualTo("asha@example.com");
                    assertThat(event.user().name()).isEqualTo("Asha");
                });
    }
}
