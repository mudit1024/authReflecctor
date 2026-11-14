package com.authreflector.apps;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.authreflector.apps.AppDtos.AppRequest;
import com.authreflector.apps.AppDtos.AppResponse;
import com.authreflector.apps.AppDtos.CreatedApp;
import com.authreflector.apps.AppDtos.NewSecret;

import jakarta.validation.Valid;

// ADMIN only: everything under /api/v1/admin/** requires ROLE_ADMIN (SecurityConfig)
@RestController
@RequestMapping("/api/v1/admin/apps")
public class AdminAppsController {

    private final AppRegistry registry;

    public AdminAppsController(AppRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<AppResponse> list() {
        return registry.list();
    }

    @GetMapping("/{clientId}")
    public AppResponse get(@PathVariable String clientId) {
        return registry.get(clientId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedApp create(@Valid @RequestBody AppRequest request) {
        return registry.create(request);
    }

    @PutMapping("/{clientId}")
    public AppResponse update(@PathVariable String clientId, @Valid @RequestBody AppRequest request) {
        return registry.update(clientId, request);
    }

    @PostMapping("/{clientId}/secret")
    public NewSecret regenerateSecret(@PathVariable String clientId) {
        return registry.regenerateSecret(clientId);
    }
}
