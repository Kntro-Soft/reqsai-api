-- Discovery sessions — tenant-scoped (one table per entity, no grouped migrations).

CREATE TABLE discovery_sessions (
    id         UUID         PRIMARY KEY,
    project_id UUID         NOT NULL,
    title      VARCHAR(200) NOT NULL,
    language   VARCHAR(8)   NOT NULL,
    status     VARCHAR(16)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    created_by UUID,
    updated_by UUID
);
CREATE INDEX idx_sessions_project ON discovery_sessions (project_id);
