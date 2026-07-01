package com.kntro.reqsai.shared.infrastructure.persistence.multitenancy;

import com.kntro.reqsai.shared.domain.exception.Exceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

/**
 * Default {@link ProvisioningService}: schema creation + dynamic Flyway migration.
 * <p>
 * Tenant migrations live in {@code classpath:db/migration/tenant} and are applied per schema, each
 * schema keeping its own {@code flyway_schema_history} table. {@code clean} is disabled to prevent
 * accidental data loss.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProvisioningServiceImpl implements ProvisioningService {

    private static final String TENANT_MIGRATIONS = "classpath:db/migration/tenant";

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Override
    public void provisionTenant(String slug) {
        String schema = TenantSchemaResolver.SCHEMA_PREFIX + slug.toLowerCase();
        try {
            log.info("Provisioning tenant schema: {}", schema);
            jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS \"" + schema + "\"");
            runTenantMigrations(schema);
            log.info("Tenant schema provisioned successfully: {}", schema);
        } catch (Exception e) {
            log.error("Failed to provision tenant schema {} — rolling back", schema, e);
            try {
                jdbcTemplate.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
            } catch (Exception rollbackEx) {
                log.error("Rollback (DROP SCHEMA) failed for {}", schema, rollbackEx);
            }
            throw Exceptions.tenantProvisioningFailed(slug, e);
        }
    }

    @Override
    public void migrateExistingTenant(String slug) {
        String schema = TenantSchemaResolver.SCHEMA_PREFIX + slug.toLowerCase();
        log.info("Applying pending migrations to tenant schema: {}", schema);
        runTenantMigrations(schema);
    }

    @Override
    public void deprovisionTenant(String slug) {
        String schema = TenantSchemaResolver.SCHEMA_PREFIX + slug.toLowerCase();
        try {
            log.info("Deprovisioning tenant schema: {}", schema);
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
            log.info("Tenant schema dropped: {}", schema);
        } catch (Exception e) {
            log.error("Failed to deprovision tenant schema {}", schema, e);
            throw Exceptions.tenantProvisioningFailed(slug, e);
        }
    }

    private void runTenantMigrations(String schema) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .locations(TENANT_MIGRATIONS)
                .baselineOnMigrate(true)
                .table("flyway_schema_history")
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .load()
                .migrate();
    }
}
