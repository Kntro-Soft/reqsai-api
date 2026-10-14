DROP INDEX IF EXISTS uq_integration_connections_active_org_provider;

CREATE UNIQUE INDEX uq_integration_connections_active_org_provider_site
    ON integration_connections (organization_id, provider, site_url)
    WHERE status <> 'DISCONNECTED';
