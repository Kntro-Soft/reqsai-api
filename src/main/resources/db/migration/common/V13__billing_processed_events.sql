-- Idempotency ledger for payment-provider webhook events — lives in the PUBLIC schema (global, not
-- per-tenant), consistent with public.subscriptions. Billing records each provider event id the first
-- time it is handled; a webhook redelivery finds the id already present and becomes a no-op.

CREATE TABLE public.billing_processed_events (
    event_id     VARCHAR(255) PRIMARY KEY,
    event_type   VARCHAR(64)  NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
