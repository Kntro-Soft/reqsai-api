-- US50: read-only links that let a client without an account review a project's stories. Lives in the
-- PUBLIC schema (like invitations) because an anonymous visitor has no tenant: the link says which
-- organization and project it opens. Only the SHA-256 hash of the raw token is stored.
CREATE TABLE public.share_links (
    id              UUID        NOT NULL PRIMARY KEY,
    organization_id UUID        NOT NULL,
    project_id      UUID        NOT NULL,
    token_hash      VARCHAR(64) NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    CONSTRAINT uq_share_links_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_share_links_project ON public.share_links (organization_id, project_id);
