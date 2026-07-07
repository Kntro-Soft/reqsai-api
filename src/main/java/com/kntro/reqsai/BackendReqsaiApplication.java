package com.kntro.reqsai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Reqs-AI backend entry point.
 * <p>
 * AI-powered B2B SaaS requirements elicitation platform.
 * Modular monolith (Spring Modulith) with schema-per-tenant multitenancy.
 *
 * <ul>
 *   <li>{@link ConfigurationPropertiesScan} — binds {@code @ConfigurationProperties} records (e.g. JwtProperties).</li>
 *   <li>{@link EnableJpaAuditing} — populates createdAt/updatedAt/createdBy/updatedBy on aggregates.</li>
 *   <li>{@link EnableCaching} — enables the Caffeine cache used by the tenant-schema resolver.</li>
 *   <li>{@link EnableScheduling} — activates {@code @Scheduled} tasks (e.g. nightly token purge).</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaAuditing
@EnableCaching
@EnableScheduling
public class BackendReqsaiApplication {
    static void main(String[] args) {
        SpringApplication.run(BackendReqsaiApplication.class, args);
    }
}
