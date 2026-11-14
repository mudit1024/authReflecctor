package com.authreflector.client;

/**
 * Published every time someone signs in to this app through AuthReflector (browser sign-in).
 * Listen to it to create or update the app's own user row (just-in-time provisioning):
 * {@code @EventListener void onSignIn(AuthReflectorSignInEvent event) { users.upsert(event.user()); }}
 */
public record AuthReflectorSignInEvent(AuthReflectorUser user) {
}
