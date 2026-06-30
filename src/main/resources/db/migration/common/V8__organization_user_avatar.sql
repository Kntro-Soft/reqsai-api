-- Avatar bytes for the global registries (public schema).
-- A deterministic avatar image is downloaded on entity creation and stored as bytea so it can be
-- served back from our own public GET endpoint (browser <img src> cannot carry the Bearer token).
-- users.avatar_url is dropped: the avatar is now generated and stored as bytes, not a client-supplied URL.

ALTER TABLE public.organizations
    ADD COLUMN avatar              BYTEA,
    ADD COLUMN avatar_content_type VARCHAR(64);

ALTER TABLE public.users
    DROP COLUMN avatar_url,
    ADD COLUMN avatar              BYTEA,
    ADD COLUMN avatar_content_type VARCHAR(64);
