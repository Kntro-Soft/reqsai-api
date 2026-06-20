package com.kntro.reqsai.testsupport;

import com.kntro.reqsai.workspace.api.WorkspaceModuleApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
 * <p>
 * {@code workspaceApi} is mocked because the Workspace module's API implementation lives in a
 * parallel branch and is merged later — unit-test coverage for the discovery→workspace interaction
 * belongs in {@code RealtimeSuggestionServiceTest}.
 */
public abstract class AbstractIntegrationTest {

    @MockitoBean
    protected WorkspaceModuleApi workspaceApi;

    @Value("${local.server.port:0}")
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
