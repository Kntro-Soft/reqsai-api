# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Dependency layer first: copy only build inputs so the Gradle download layer is cached and reused
# until the build files change. The BuildKit cache mount keeps the Gradle home warm across builds.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x ./gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Build the executable jar (tests run in CI, not in the image build), then explode it into layers
# (dependencies / spring-boot-loader / snapshot-dependencies / application) for optimal image caching.
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon clean bootJar -x test && \
    java -Djarmode=tools -jar build/libs/*.jar extract --layers --launcher --destination build/extracted

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# OS + JVM timezone.
ENV TZ=America/Lima

# Run as a non-root user.
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

# Copy layers from least- to most-frequently changed to maximize Docker layer cache hits on redeploys.
COPY --from=build --chown=spring:spring /workspace/build/extracted/dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /workspace/build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/build/extracted/application/ ./

EXPOSE 8080

# JDK_JAVA_OPTIONS is read automatically by the launcher, so the entrypoint stays exec-form
# (java is PID 1 → correct signal handling / graceful shutdown). Container-aware heap + small stacks.
ENV JDK_JAVA_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Xss512k" \
    SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
