package com.kntro.reqsai.shared.infrastructure.persistence.multitenancy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * On startup, applies pending tenant migrations to every already-provisioned organization.
 * <p>
 * Reads non-{@code PENDING} organizations from the {@code public.organizations} tenant registry
 * (those have a schema) and runs {@link ProvisioningService#migrateExistingTenant(String)} for each.
 * A failure on one tenant is logged and skipped, so it never blocks startup or the other tenants.
 * <p>
 * The {@code organizations} registry is owned by the {@code workspace} bounded context (it ships the
 * {@code public} migration that creates it). Until that exists, this runner degrades gracefully:
 * a missing table is logged and skipped, so the foundation boots with no tenants provisioned yet.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class TenantMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final ProvisioningService provisioningService;

    @Override
    public void run(@NonNull ApplicationArguments args) {
        List<String> slugs;
        try {
            slugs = jdbcTemplate.queryForList(
                    "SELECT slug FROM public.organizations WHERE status <> 'PENDING'",
                    String.class);
        } catch (DataAccessException e) {
            log.info("Tenant registry (public.organizations) not present yet — skipping tenant migrations. "
                    + "It is created by the 'workspace' bounded context.");
            return;
        }

        if (slugs.isEmpty()) {
            log.info("No provisioned tenants found. Skipping tenant migrations.");
            return;
        }

        log.info("Starting migrations for {} provisioned tenant(s)...", slugs.size());
        List<String> failed = new ArrayList<>();
        for (String slug : slugs) {
            try {
                provisioningService.migrateExistingTenant(slug);
            } catch (Exception e) {
                log.error("Failed to migrate tenant '{}' — skipping", slug, e);
                failed.add(slug);
            }
        }

        log.info("Tenant migration summary: {}/{} succeeded.", slugs.size() - failed.size(), slugs.size());
        if (!failed.isEmpty()) {
            log.warn("Tenants that could not be migrated: {}", failed);
        }
    }
}
