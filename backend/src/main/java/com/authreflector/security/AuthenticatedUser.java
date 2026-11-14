package com.authreflector.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.authreflector.identity.Role;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.authreflector.identity.User;

/**
 * The signed-in user as stored in the session (a snapshot taken at login, not the JPA entity:
 * entities don't belong in long-lived HTTP sessions). Email is the username.
 */
// JSON: stored inside OIDC authorizations in the database (see oidc.AuthorizationJson). Only identity
// fields are written; the password hash and derived values never are.
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AuthenticatedUser implements UserDetails, CredentialsContainer {

    private final UUID id;
    private final String name;
    private final String email;
    private final Role role;
    private String passwordHash;

    @JsonCreator
    static AuthenticatedUser fromJson(
            @JsonProperty("id") UUID id,
            @JsonProperty("name") String name,
            @JsonProperty("email") String email,
            @JsonProperty("role") Role role) {
        return new AuthenticatedUser(id, name, email, role, null);
    }

    private AuthenticatedUser(UUID id, String name, String email, Role role, String passwordHash) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.passwordHash = passwordHash;
    }

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getPasswordHash());
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    @Override
    @JsonIgnore
    public String getUsername() {
        return email;
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return passwordHash;
    }

    // "ROLE_" prefix is Spring Security's convention, so hasRole("ADMIN") matches ROLE_ADMIN
    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    // Called by Spring Security right after a successful login: the hash is no longer needed
    // and must not live on in the session for days
    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }

    @Override
    public String toString() {
        return "AuthenticatedUser[id=" + id + ", email=" + email + ", role=" + role + "]";
    }
}
