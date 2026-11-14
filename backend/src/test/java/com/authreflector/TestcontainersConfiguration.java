package com.authreflector;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// Starts a throwaway PostgreSQL in Docker for tests. @ServiceConnection points the app's
// DataSource at it automatically, so tests never touch the local development database.
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        // Same major version as docker-compose.yml and production: never "latest"
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
    }
}
