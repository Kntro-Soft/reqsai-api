-- Global subscription registry — lives in the PUBLIC schema (NOT per-tenant), one row per organization.
-- Billing owns the subscription lifecycle. Cross-context reference to the organization is a plain UUID
-- (no cross-context FK), consistent with the modular-monolith rules.

CREATE TABLE public.subscriptions (
    id                     UUID         PRIMARY KEY,
    organization_id        UUID         NOT NULL,
    plan_type              VARCHAR(16)  NOT NULL,
    status                 VARCHAR(16)  NOT NULL,
    payment_provider       VARCHAR(32),
    payment_external_id    VARCHAR(255),
    current_period_start   TIMESTAMPTZ  NOT NULL,
    current_period_end     TIMESTAMPTZ  NOT NULL,
    token_quota_used       BIGINT       NOT NULL DEFAULT 0,
    cancelled_at           TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL,
    updated_at             TIMESTAMPTZ  NOT NULL,
    created_by             UUID,
    updated_by             UUID,
    CONSTRAINT uq_subscriptions_organization UNIQUE (organization_id)
);
