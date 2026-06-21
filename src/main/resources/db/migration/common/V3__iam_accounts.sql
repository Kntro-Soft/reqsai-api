-- IAM accounts — lives in the PUBLIC schema (shared across tenants, per report §5.1).
-- Holds credentials and the account lifecycle (PENDING_VERIFICATION → ACTIVE → SUSPENDED/DELETED).
-- Email verification tokens live in public.email_verifications (separate aggregate).
-- Password-reset state and terms acceptance are embedded here (no extra table needed).

CREATE TABLE public.accounts (
    id                              UUID         PRIMARY KEY,
    email                           VARCHAR(320) NOT NULL,
    password_hash                   VARCHAR(255) NOT NULL,
    status                          VARCHAR(30)  NOT NULL,
    password_reset_token            VARCHAR(64),
    password_reset_token_expires_at TIMESTAMPTZ,
    terms_accepted_at               TIMESTAMPTZ,
    terms_version                   VARCHAR(50),
    created_at                      TIMESTAMPTZ  NOT NULL,
    updated_at                      TIMESTAMPTZ  NOT NULL,
    created_by                      UUID,
    updated_by                      UUID,
    CONSTRAINT uq_accounts_email UNIQUE (email)
);

CREATE INDEX idx_accounts_password_reset_token ON public.accounts (password_reset_token)
    WHERE password_reset_token IS NOT NULL;
