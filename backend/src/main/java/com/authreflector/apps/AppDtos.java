package com.authreflector.apps;

import java.util.List;

import com.authreflector.apps.AppConstraints.HomeUrl;
import com.authreflector.apps.AppConstraints.RedirectUris;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response shapes of the admin apps API. */
public final class AppDtos {

    private AppDtos() {
    }

    public record AppRequest(
            @NotNull(message = "Type must be WEB or SPA")
            AppType type,

            @NotBlank(message = "Client id must not be blank")
            @Pattern(regexp = "^[a-z0-9][a-z0-9-]{2,49}$",
                    message = "Client id: 3-50 lowercase letters, digits and dashes, e.g. splitmate-web")
            String clientId,

            @NotBlank(message = "Name must not be blank")
            @Size(max = 100, message = "Name must be at most 100 characters")
            String name,

            // The "aud" of this app's access tokens (its API), e.g. splitmate-api. Defaults to the client id.
            @Pattern(regexp = "^[a-z0-9][a-z0-9-]{2,99}$", message = "Audience: lowercase letters, digits and dashes")
            String audience,

            @NotEmpty(message = "At least one redirect URI is required")
            @Size(max = 10, message = "At most 10 redirect URIs")
            @RedirectUris
            List<String> redirectUris,

            @Size(max = 10, message = "At most 10 post-logout redirect URIs")
            @RedirectUris
            List<String> postLogoutRedirectUris,

            @HomeUrl
            @Size(max = 500, message = "Home URL must be at most 500 characters")
            String homeUrl,

            @Size(max = 300, message = "Description must be at most 300 characters")
            String description,

            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Color must look like #10B981")
            String color,

            Boolean showInLauncher,

            Boolean enabled
    ) {
    }

    public record AppResponse(
            AppType type,
            String clientId,
            String name,
            String audience,
            List<String> redirectUris,
            List<String> postLogoutRedirectUris,
            String homeUrl,
            String description,
            String color,
            boolean showInLauncher,
            boolean enabled
    ) {
    }

    /** The secret appears here exactly once (create); it is stored only as a hash. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreatedApp(AppResponse app, String clientSecret) {
    }

    public record NewSecret(String clientSecret) {
    }
}
