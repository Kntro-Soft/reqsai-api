package com.kntro.reqsai;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the full Spring context against a Testcontainers PostgreSQL (see {@code application-test.yml}).
 * <p>
 * This is the cheapest regression net for the foundation: it fails if any bean fails to wire — which is
 * exactly how the smoke-test boot bugs would surface in CI instead of only at {@code bootRun} time
 * (servlet filters auto-registering and failing {@code init()}, the pgvector store demanding an
 * {@code EmbeddingModel}, etc.). If this test is green, the application starts.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class ApplicationContextTest {
    @Test
    void contextLoads() {}
}
