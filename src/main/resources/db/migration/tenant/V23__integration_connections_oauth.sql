-- Jira Cloud OAuth 2.0 (3LO) support alongside the existing API-token flow (ADR-0022).
-- Additive & backward-compatible: existing rows default to credential_type = 'API_TOKEN' and keep their
-- email + secret_ciphertext. OAuth rows carry cloud_id + encrypted refresh/access tokens instead.
--
-- Invariant (application-enforced): exactly one credential shape is populated per credential_type —
--   API_TOKEN -> (email, secret_ciphertext) NOT NULL, oauth_* NULL
--   OAUTH2    -> (cloud_id, oauth_refresh_ciphertext) NOT NULL, email + secret_ciphertext NULL
-- site_url stays NOT NULL for both (OAuth sets it from the discovered accessible-resource site URL).

ALTER TABLE integration_connections
    ADD COLUMN credential_type          VARCHAR(32) NOT NULL DEFAULT 'API_TOKEN';

ALTER TABLE integration_connections
    ADD COLUMN cloud_id                 VARCHAR(64);

ALTER TABLE integration_connections
    ADD COLUMN oauth_refresh_ciphertext BYTEA;

ALTER TABLE integration_connections
    ADD COLUMN oauth_access_ciphertext  BYTEA;

ALTER TABLE integration_connections
    ADD COLUMN oauth_access_expires_at  TIMESTAMPTZ;

-- Relax the API-token-only NOT NULL constraints so OAuth connections (no basic-auth email/token) fit the
-- shared table. The one-populated-shape-per-credential_type invariant is enforced in the domain factory.
ALTER TABLE integration_connections ALTER COLUMN email DROP NOT NULL;
ALTER TABLE integration_connections ALTER COLUMN secret_ciphertext DROP NOT NULL;
