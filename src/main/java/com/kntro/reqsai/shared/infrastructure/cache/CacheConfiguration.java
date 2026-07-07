package com.kntro.reqsai.shared.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Caffeine-backed caching.
 * <p>
 * Hosts the {@code tenantSchemas} cache used by {@link
 * com.kntro.reqsai.shared.infrastructure.persistence.multitenancy.TenantSchemaResolver} so the tenant-id →
 * schema lookup hits the database once per organization rather than on every request.
 */
@Configuration
public class CacheConfiguration {

    public static final String TENANT_SCHEMAS_CACHE = "tenantSchemas";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(TENANT_SCHEMAS_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofHours(1)));
        return manager;
    }
}
