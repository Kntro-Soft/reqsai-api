-- Third-party integration connections (ADR-0022): ORG-scoped credentials, encrypted at rest.

CREATE TABLE integration_connections (
    id                UUID NOT NULL PRIMARY KEY,
    organization_id   UUID NOT NULL,
    provider          VARCHAR(32)  NOT NULL,
    site_url          VARCHAR(500) NOT NULL,
    email             VARCHAR(320) NOT NULL,
    secret_ciphertext BYTEA        NOT NULL,
    status            VARCHAR(32)  NOT NULL,
    last_verified_at  TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID
);

CREATE INDEX idx_integration_connections_org ON integration_connections (organization_id);

-- At most one active (non-disconnected) connection per org per provider.
CREATE UNIQUE INDEX uq_integration_connections_active_org_provider
    ON integration_connections (organization_id, provider)
    WHERE status <> 'DISCONNECTED';
