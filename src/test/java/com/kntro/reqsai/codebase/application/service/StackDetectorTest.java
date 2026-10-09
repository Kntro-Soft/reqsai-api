package com.kntro.reqsai.codebase.application.service;

import com.kntro.reqsai.codebase.application.port.CodeHostPort.SourceFile;
import com.kntro.reqsai.codebase.domain.model.CodeProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StackDetector")
class StackDetectorTest {

    @Test
    @DisplayName("detects languages, frameworks, databases and platforms from sources and manifests")
    void detects() {
        List<SourceFile> files = new ArrayList<>();
        for (int i = 0; i < 6; i++) files.add(new SourceFile("src/a" + i + ".ts", "x"));
        files.add(new SourceFile("src/app/app.component.html", "<div></div>"));
        files.add(new SourceFile("package.json", """
                {"dependencies": {"@angular/core": "22", "express": "4", "pg": "8", "tailwindcss": "4"}}"""));
        files.add(new SourceFile("docker-compose.yml", "services:\n  db:\n    image: postgres:16\n  cache:\n    image: redis"));

        CodeProfile profile = StackDetector.detect(files);

        assertThat(profile.languages()).contains("TypeScript");
        assertThat(profile.frameworks()).contains("Angular", "Express", "Tailwind CSS");
        assertThat(profile.databases()).contains("PostgreSQL", "Redis");
        assertThat(profile.platforms()).containsExactly("Web");
    }

    @Test
    @DisplayName("Spring Boot with Kotlin, and a mobile Flutter app")
    void backendAndMobile() {
        List<SourceFile> files = new ArrayList<>();
        for (int i = 0; i < 4; i++) files.add(new SourceFile("src/main/kotlin/A" + i + ".kt", "x"));
        files.add(new SourceFile("build.gradle.kts", "implementation(\"org.springframework.boot:spring-boot-starter-web\")\nruntimeOnly(\"com.mysql:mysql-connector-j\")"));
        CodeProfile backend = StackDetector.detect(files);
        assertThat(backend.languages()).containsExactly("Kotlin");
        assertThat(backend.frameworks()).containsExactly("Spring Boot");
        assertThat(backend.databases()).containsExactly("MySQL");

        CodeProfile mobile = StackDetector.detect(List.of(new SourceFile("pubspec.yaml", "dependencies:\n  flutter:\n    sdk: flutter")));
        assertThat(mobile.platforms()).contains("Android", "iOS");
    }
}
