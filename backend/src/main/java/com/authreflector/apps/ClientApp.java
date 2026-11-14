package com.authreflector.apps;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** AuthReflector's own details of a registered app (table client_apps, migration V6). */
@Entity
@Table(name = "client_apps")
public class ClientApp {

    @Id
    @Column(length = 100)
    private String clientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AppType appType;

    @Column(length = 500)
    private String homeUrl;

    @Column(length = 300)
    private String description;

    @Column(length = 7)
    private String color;

    @Column(nullable = false)
    private boolean showInLauncher = true;

    @Column(nullable = false)
    private boolean enabled = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected ClientApp() {
        // Required by JPA
    }

    public ClientApp(String clientId, AppType appType) {
        this.clientId = clientId;
        this.appType = appType;
    }

    void update(String homeUrl, String description, String color, boolean showInLauncher, boolean enabled) {
        this.homeUrl = homeUrl;
        this.description = description;
        this.color = color;
        this.showInLauncher = showInLauncher;
        this.enabled = enabled;
    }

    public String getClientId() {
        return clientId;
    }

    public AppType getAppType() {
        return appType;
    }

    public String getHomeUrl() {
        return homeUrl;
    }

    public String getDescription() {
        return description;
    }

    public String getColor() {
        return color;
    }

    public boolean isShowInLauncher() {
        return showInLauncher;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
