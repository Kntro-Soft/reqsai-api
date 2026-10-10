-- Code-aware copilot: the GitHub App installations an organization connected. Lives in the PUBLIC schema
-- because GitHub's webhooks name an installation, not a tenant: this table says which organizations it
-- serves. Only the installation id and its account are stored; access tokens are minted per use (1 hour)
-- and never persisted.
CREATE TABLE public.code_host_installations (
    id                   UUID         NOT NULL PRIMARY KEY,
    organization_id      UUID         NOT NULL,
    provider             VARCHAR(16)  NOT NULL,
    installation_id      BIGINT       NOT NULL,
    account_login        VARCHAR(100) NOT NULL,
    account_type         VARCHAR(20)  NOT NULL,
    repository_selection VARCHAR(16),
    manage_url           VARCHAR(500),
    suspended_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           UUID,
    updated_by           UUID,
    CONSTRAINT uq_code_host_installations UNIQUE (organization_id, provider, installation_id)
);

CREATE INDEX idx_code_host_installations_installation ON public.code_host_installations (provider, installation_id);
