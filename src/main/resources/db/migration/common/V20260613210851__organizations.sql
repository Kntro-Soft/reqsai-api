-- Global organization registry — lives in the PUBLIC schema (NOT per-tenant).
-- The TenantSchemaResolver reads this table to map an org id/slug to its tenant_<slug> schema;
-- rows in status 'PENDING' are excluded there until provisioning completes.

CREATE TABLE public.organizations (
    id                      UUID         PRIMARY KEY,
    name                    VARCHAR(150) NOT NULL,
    slug                    VARCHAR(50)  NOT NULL,
    owner_id                UUID         NOT NULL,
    status                  VARCHAR(16)  NOT NULL,
    meeting_language               VARCHAR(8)  NOT NULL,
    audio_retention_days           INTEGER     NOT NULL,
    max_members                    INTEGER     NOT NULL,
    max_projects                   INTEGER     NOT NULL,
    max_documents_per_project      INTEGER     NOT NULL,
    max_tokens_per_month           BIGINT      NOT NULL,
    max_glossary_terms_per_project INTEGER     NOT NULL,
    created_at                     TIMESTAMPTZ NOT NULL,
    updated_at                     TIMESTAMPTZ NOT NULL,
    created_by                     UUID,
    updated_by                     UUID,
    CONSTRAINT uq_organizations_slug UNIQUE (slug)
);
