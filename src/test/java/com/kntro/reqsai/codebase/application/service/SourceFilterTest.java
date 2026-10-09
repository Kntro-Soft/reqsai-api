package com.kntro.reqsai.codebase.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SourceFilter")
class SourceFilterTest {

    @ParameterizedTest
    @ValueSource(strings = {"src/reservations/reservation.service.ts", "app/models.py", "src/main/java/A.java",
            "package.json", "build.gradle.kts", "README.md", "docs/README.es.md", "db/migrations/001_init.sql",
            "docker-compose.yml", "android/app/src/main/AndroidManifest.xml"})
    @DisplayName("reads source code, manifests and READMEs")
    void keeps(String path) {
        assertThat(SourceFilter.keep(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"node_modules/express/index.js", "dist/main.js", "build/classes/A.class",
            "package-lock.json", "bun.lock", "src/app.min.js", "src/app.js.map", ".env", ".env.production",
            "config/secrets.yml", "certs/server.pem", "keys/id_rsa", "src/assets/logo.png", "types/index.d.ts",
            "infra/prod.tfvars", "src/credentials.json"})
    @DisplayName("leaves out dependencies, build output, lockfiles, binaries and files that hold secrets")
    void skips(String path) {
        assertThat(SourceFilter.keep(path)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"src/app.spec.ts", "tests/test_reservas.py", "src/test/java/ATest.java",
            "src/__tests__/a.ts", "app/reservation_test.go"})
    @DisplayName("recognizes test files")
    void tests(String path) {
        assertThat(SourceFilter.isTest(path)).isTrue();
    }
}
