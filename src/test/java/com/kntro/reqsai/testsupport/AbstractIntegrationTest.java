package com.kntro.reqsai.testsupport;

import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests that boot the app and provision tenant schemas — those run the
 * pgvector migrations, so they need a Postgres image carrying the {@code vector} extension.
 * <p>
 * Singleton container pattern: one {@code pgvector/pgvector:pg16} container is started once per JVM
 * fork (static initializer) and reused by every subclass. {@code @DynamicPropertySource} wires the
 * datasource so each Spring context connects to the same running instance.
 */
public abstract class AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    protected RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }
}
