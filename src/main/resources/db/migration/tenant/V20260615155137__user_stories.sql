-- User stories — tenant-scoped (one table per entity, no grouped migrations).

CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;

CREATE TABLE user_stories (
    id           UUID               PRIMARY KEY,
    session_id   UUID,
    project_id   UUID               NOT NULL,
    title        VARCHAR(200)       NOT NULL,
    role         VARCHAR(500)       NOT NULL,
    action       VARCHAR(500)       NOT NULL,
    benefit      VARCHAR(500)       NOT NULL,
    priority     VARCHAR(16)        NOT NULL,
    story_points INTEGER,
    status       VARCHAR(16)        NOT NULL,
    embedding    public.vector(768),
    created_at   TIMESTAMPTZ        NOT NULL,
    updated_at   TIMESTAMPTZ        NOT NULL,
    created_by   UUID,
    updated_by   UUID
);

CREATE INDEX idx_stories_project ON user_stories (project_id);
