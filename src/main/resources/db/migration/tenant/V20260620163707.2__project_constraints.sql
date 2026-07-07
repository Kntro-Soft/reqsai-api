CREATE TABLE project_constraints (
    id          UUID NOT NULL PRIMARY KEY,
    project_id  UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    description TEXT NOT NULL,
    embedding   vector(768),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID
);

CREATE INDEX idx_project_constraints_project_id ON project_constraints (project_id);
CREATE UNIQUE INDEX uq_project_constraints_project_description_ci
    ON project_constraints (project_id, lower(description));
CREATE INDEX idx_project_constraints_embedding ON project_constraints USING hnsw (embedding vector_cosine_ops)
    WHERE embedding IS NOT NULL;
