plugins {
    java
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.kntro"
version = "0.0.0"
description = "backend-reqsai"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

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
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
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
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")

    // ==================================
    // SPRING AI
    // ==================================
    implementation("org.springframework.ai:spring-ai-advisors-vector-store")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("com.deepgram:deepgram-java-sdk:0.5.0")
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
    testImplementation("net.datafaker:datafaker:2.5.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testCompileOnly("org.projectlombok:lombok")
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
}
