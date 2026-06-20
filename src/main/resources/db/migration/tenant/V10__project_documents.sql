CREATE TABLE project_documents (
    id            UUID         NOT NULL PRIMARY KEY,
    project_id    UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name          VARCHAR(255) NOT NULL,
    document_type VARCHAR(32)  NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID
);

CREATE INDEX idx_project_documents_project_id ON project_documents (project_id);
CREATE UNIQUE INDEX uq_project_documents_project_name_status_ci
    ON project_documents (project_id, lower(name), status);
