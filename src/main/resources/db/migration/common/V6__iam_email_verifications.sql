-- Email verification tokens — lives in the PUBLIC schema.
-- Stores only the SHA-256 hash of the raw token; raw value is never persisted.
-- One-time use: marked used_at once consumed; expires via expires_at.

CREATE TABLE public.email_verifications (
    id          UUID         NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    account_id  UUID         NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    updated_by  UUID,
    CONSTRAINT pk_email_verifications PRIMARY KEY (id),
    CONSTRAINT uq_email_verifications_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_email_verifications_account FOREIGN KEY (account_id) REFERENCES public.accounts (id)
);

CREATE INDEX idx_email_verifications_token_hash ON public.email_verifications (token_hash);
