package com.kntro.reqsai.testsupport;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests that boot the app and provision tenant schemas — those run the
 * pgvector migrations, so they need a Postgres image carrying the {@code vector} extension.
 * <p>
 * A single {@code pgvector/pgvector:pg16} container is shared across all test classes in the same
 * JVM fork ({@code static} + {@code @Container}). {@code @ServiceConnection} auto-configures the
 * datasource so no {@code @DynamicPropertySource} is needed.
 */
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres"));
}
