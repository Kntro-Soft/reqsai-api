-- Project-scoped Jira push routing (ADR-0022): one target per project, pointing at an org connection.

CREATE TABLE project_integration_targets (
    id               UUID NOT NULL PRIMARY KEY,
    project_id       UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    connection_id    UUID NOT NULL REFERENCES integration_connections(id) ON DELETE CASCADE,
    jira_project_key VARCHAR(100) NOT NULL,
    issue_type_name  VARCHAR(100) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID
);

-- One integration target per project (the PUT .../target endpoint upserts this single row).
CREATE UNIQUE INDEX uq_project_integration_targets_project
    ON project_integration_targets (project_id);

CREATE INDEX idx_project_integration_targets_connection ON project_integration_targets (connection_id);
