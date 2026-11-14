package com.authreflector.oidc;

import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.oauth2.server.authorization.jackson.OAuth2AuthorizationServerJacksonModule;

import com.authreflector.security.AuthenticatedUser;

import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON used to store authorizations (who signed in to which app) in the database.
 *
 * Reading JSON back into arbitrary Java types is a classic attack ("deserialization gadgets"), so Spring
 * only allows its own known types. We keep that allowlist and add exactly one class: our principal.
 */
final class AuthorizationJson {

    private AuthorizationJson() {
    }

    static JsonMapper mapper() {
        ClassLoader classLoader = AuthorizationJson.class.getClassLoader();

        OAuth2AuthorizationServerJacksonModule authorizationServerModule = new OAuth2AuthorizationServerJacksonModule();
        BasicPolymorphicTypeValidator.Builder allowlist = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(AuthenticatedUser.class);
        authorizationServerModule.configurePolymorphicTypeValidator(allowlist);

        return JsonMapper.builder()
                .addModules(SecurityJacksonModules.getModules(classLoader, allowlist))
                .addModule(authorizationServerModule)
                .build();
    }
}
