package com.kntro.reqsai.shared.infrastructure.persistence.multitenancy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/**
 * Routes Hibernate connections to the right PostgreSQL schema by setting {@code search_path}.
 * <p>
 * On {@link #getConnection(String)}: {@code SET search_path TO <tenant_schema>, public} (so tenant
 * tables resolve first, shared/extension objects in {@code public} remain visible). On release the
 * connection is reset to {@code public} before returning to the pool — preventing schema leakage.
 * <p>
 * Uses a single {@link DataSource} (one connection pool); only the {@code search_path} changes per
 * tenant. Self-registers via {@link HibernatePropertiesCustomizer}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MultiTenantConnectionProviderImpl implements MultiTenantConnectionProvider<String>, HibernatePropertiesCustomizer {

    private final DataSource dataSource;

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantSchema) throws SQLException {
        Connection connection = getAnyConnection();
        if (tenantSchema != null && !TenantContext.DEFAULT_SCHEMA.equals(tenantSchema)) {
            try (Statement st = connection.createStatement()) {
                st.execute("SET search_path TO \"" + tenantSchema + "\", public");
                log.trace("search_path -> {}", tenantSchema);
            } catch (SQLException e) {
                log.error("Failed to set search_path for schema {}", tenantSchema, e);
                connection.close();
                throw e;
            }
        }
        return connection;
    }

    @Override
    public void releaseConnection(String tenantSchema, Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("SET search_path TO public");
        } catch (SQLException e) {
            log.error("Failed to reset search_path for schema {}", tenantSchema, e);
        } finally {
            connection.close();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return null;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER, this);
    }
}
