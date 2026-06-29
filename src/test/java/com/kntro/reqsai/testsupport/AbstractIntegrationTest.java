package com.kntro.reqsai.testsupport;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Base class for integration tests that boot the app and provision tenant schemas — those run the
 * pgvector migrations, so they need a Postgres image carrying the {@code vector} extension.
 * <p>
 * Singleton container pattern: one {@code pgvector/pgvector:pg16} container is started once per JVM
 * fork (static initializer) and reused by every subclass. {@code @DynamicPropertySource} wires the
 * datasource so each Spring context connects to the same running instance.
 */
public abstract class AbstractIntegrationTest {

    @Value("${local.server.port:0}")
    private int port;

    protected RestClient client() {
        // Generous read timeout: the first GET /api-docs triggers a full (cold) OpenAPI document
        // generation that can take well over the request factory's short default under a loaded
        // Testcontainers JVM, intermittently failing with a Netty ReadTimeoutException.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(factory)
                .build();
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
