CREATE TABLE project_constraints (
    id          UUID         NOT NULL PRIMARY KEY,
    project_id  UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    description VARCHAR(500) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID
);

CREATE INDEX idx_project_constraints_project_id ON project_constraints (project_id);
CREATE UNIQUE INDEX uq_project_constraints_project_description_ci
    ON project_constraints (project_id, lower(description));
