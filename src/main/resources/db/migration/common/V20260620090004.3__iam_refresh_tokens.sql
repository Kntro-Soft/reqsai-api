-- IAM refresh tokens — lives in the PUBLIC schema (shared across tenants).
-- Stores only the SHA-256 hash of the raw token; the raw value is never persisted.
-- One active token per session; old tokens are revoked on rotation or logout.

CREATE TABLE IF NOT EXISTS public.refresh_tokens (
    id          UUID         NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    user_id     UUID         NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    updated_by  UUID,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES public.users (id)
);

CREATE INDEX idx_refresh_tokens_token_hash ON public.refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_user_id    ON public.refresh_tokens (user_id);
