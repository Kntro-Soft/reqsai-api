-- IAM users — lives in the PUBLIC schema (shared across tenants, per report §5.1).
-- Holds the user profile, linked 1:1 to public.accounts by account_id.
-- The user id is what the issued JWT carries as `sub` and what public.organizations stores as owner_id.
-- avatar_url and last_visited_* are nullable — populated lazily once the user updates their profile (TS17).

CREATE TABLE public.users (
    id                        UUID         PRIMARY KEY,
    account_id                UUID         NOT NULL,
    first_name                VARCHAR(100) NOT NULL,
    last_name                 VARCHAR(100) NOT NULL,
    avatar_url                VARCHAR(2048),
    last_visited_org_id       UUID,
    last_visited_project_id   UUID,
    created_at                TIMESTAMPTZ  NOT NULL,
    updated_at                TIMESTAMPTZ  NOT NULL,
    created_by                UUID,
    updated_by                UUID,
    CONSTRAINT uq_users_account_id UNIQUE (account_id),
    CONSTRAINT fk_users_account FOREIGN KEY (account_id) REFERENCES public.accounts (id)
);
