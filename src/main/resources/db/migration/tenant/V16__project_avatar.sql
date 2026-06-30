-- Avatar bytes for projects (per-tenant schema).
-- A deterministic avatar image is downloaded on project creation and stored as bytea so it can be
-- served back from our own public GET endpoint (browser <img src> cannot carry the Bearer token).

ALTER TABLE projects
    ADD COLUMN avatar              BYTEA,
    ADD COLUMN avatar_content_type VARCHAR(64);
