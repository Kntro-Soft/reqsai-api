plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.9.0"
    id("org.owasp.dependencycheck") version "12.2.2"
    jacoco
}

group = "com.kntro"
version = "0.0.0"
description = "backend-reqsai"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

val mockitoAgent by configurations.creating

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

extra["springAiVersion"] = "2.0.0-M8"
extra["springModulithVersion"] = "2.0.6"

dependencies {

    // ==================================
    // WEB + WS
    // ==================================
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-websocket")

    // ==================================
    // CACHE
    // ==================================
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")

    // ==================================
    // SECURITY + JWT
    // ==================================
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    // ==================================
    // VALIDATION
    // ==================================
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // ==================================
    // DATA + DB
    // ==================================
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-batch")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.hibernate.orm:hibernate-vector")
    runtimeOnly("org.postgresql:postgresql")

    // ==================================
    // MAIL
    // ==================================
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // ==================================
    // ACTUATOR
    // ==================================
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // ==================================
    // OPENAPI
    // ==================================
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

    // ==================================
    // SPRING AI
    // ==================================
    implementation("org.springframework.ai:spring-ai-advisors-vector-store")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("com.deepgram:deepgram-java-sdk:0.7.1")
    implementation("org.springframework.ai:spring-ai-starter-model-google-genai")
    implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")

    // ==================================
    // SPRING MODULITH
    // ==================================
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jpa")
    runtimeOnly("org.springframework.modulith:spring-modulith-actuator")
    runtimeOnly("org.springframework.modulith:spring-modulith-observability")
    runtimeOnly("org.springframework.modulith:spring-modulith-runtime")

    // ==================================
    // UTILITIES
    // ==================================
    implementation("com.github.f4b6a3:uuid-creator:6.1.1")

    // ==================================
    // ANNOTATION PROCESSORS
    // ==================================
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    annotationProcessor("org.hibernate.orm:hibernate-processor")
    annotationProcessor("org.hibernate.validator:hibernate-validator-annotation-processor")
    testAnnotationProcessor("org.projectlombok:lombok")

    // ==================================
    // DEV
    // ==================================
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    developmentOnly("org.springframework.ai:spring-ai-spring-boot-docker-compose")

    // ==================================
    // TEST
    // ==================================
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("net.datafaker:datafaker:2.7.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testCompileOnly("org.projectlombok:lombok")
    mockitoAgent("org.mockito:mockito-core") { isTransitive = false }
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.2")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    jvmArgs("-javaagent:${mockitoAgent.asPath}")
}

// Keep the real-OpenAI 'llm' probe (real tokens, non-deterministic) out of the default `test` lane
// (which `build`/`check` run). It is opted into ONLY by the dedicated `llmTest` task below. Applied to
// the `test` task by name so it does not leak into `llmTest` (a global withType exclude would be merged
// with llmTest's include and — since exclude wins — silently deselect the very tests it must run).
tasks.named<Test>("test") {
    useJUnitPlatform { excludeTags("llm") }
}

// Fast feedback loop: unit/slice tests only — skips Testcontainers integration,
// architecture, and modularity tests (no Docker, no app-context boot).
// Run with: ./gradlew unitTest
val unitTest by tasks.registering(Test::class) {
    description = "Runs fast tests only (excludes integration, architecture, modularity)"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { excludeTags("integration", "architecture", "modularity", "llm") }
}

// Real-LLM behavioral probe — boots the app against REAL OpenAI (generation + embeddings) + pgvector.
// Costs real tokens and is non-deterministic, so it is NOT part of any other lane. It SKIPS gracefully
// when OPENAI_API_KEY is unset. Run ONLY this suite, single-forked to keep token usage bounded, with:
//   ./gradlew llmTest --max-workers=1
val llmTest by tasks.registering(Test::class) {
    description = "Runs the real-OpenAI behavioral suggestion probe (tag 'llm'); skips without OPENAI_API_KEY"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("llm") }
    // One fork: the probe is deliberately serial so total OpenAI calls stay bounded.
    maxParallelForks = 1
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true // surface the [LLM-E2E] report blocks in the console
    }
}

// Testcontainers-backed integration / slice tests (need Docker + the Spring context).
// Run with: ./gradlew integrationTest
val integrationTest by tasks.registering(Test::class) {
    description = "Runs Testcontainers-backed integration tests"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
}

// ArchUnit architecture fitness functions — no Docker, but kept out of the fast unit lane.
// Run with: ./gradlew architectureTest
val architectureTest by tasks.registering(Test::class) {
    description = "Runs ArchUnit architecture fitness functions"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("architecture") }
}

// ==================================
// MODULARITY VERIFICATION
// ==================================
val verifyModularity by tasks.registering(Test::class) {
    description = "Verifies Spring Modulith architecture rules"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("modularity") }
    dependsOn(tasks.testClasses)
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.named("check") {
    dependsOn(verifyModularity)
}

// ==================================
// JPA STATIC METAMODEL
// ==================================
tasks.withType<JavaCompile> {
    options.generatedSourceOutputDirectory.set(
        file("$projectDir/build/generated/sources/annotationProcessor/java/main")
    )
}

sourceSets {
    main {
        java {
            srcDirs(
                "src/main/java",
                "$projectDir/build/generated/sources/annotationProcessor/java/main"
            )
        }
    }
    test {
        java {
            srcDirs("src/test/java")
        }
    }
}

// ==================================
// SPOTLESS — Java + Gradle DSL formatting
// ==================================
spotless {
    isEnforceCheck = false
    java {
        eclipse()
        target("src/**/*.java")
    }
    kotlinGradle {
        ktfmt()
        target("*.gradle.kts")
    }
}

// ==================================
// JACOCO — code-coverage reporting
// ==================================
jacoco { toolVersion = "0.8.15" }

tasks.jacocoTestReport {
    // Report over whichever Test task produced execution data, so the same task works after the
    // full `test` (local `build`) and after `unitTest` / `integrationTest` alone (per-stage CI
    // coverage that Codecov merges by flag). Ordered after any test task present in the graph.
    executionData(fileTree(layout.buildDirectory.get().asFile).include("jacoco/*.exec"))
    mustRunAfter(tasks.withType<Test>())
    reports {
        xml.required = true
        html.required = true
        csv.required = false
    }
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) {
                    exclude(
                        "**/BackendReqsaiApplication.class",
                        "**/Q*.class", // JPA static metamodel
                    )
                }
            }))
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.50".toBigDecimal()
            }
        }
    }
}

tasks.named("check") { dependsOn("jacocoTestReport") }

// ==================================
// OWASP DEPENDENCY-CHECK
// ==================================
dependencyCheck {
    failBuildOnCVSS = 9.0f
    suppressionFile = "owasp-suppressions.xml"
    nvd {
        apiKey = System.getenv("NVD_API_KEY") ?: "" // (optional for higher speed)
    }
    scanConfigurations = listOf("runtimeClasspath")
}
