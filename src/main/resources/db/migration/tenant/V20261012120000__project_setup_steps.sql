CREATE TABLE project_setup_steps (
    id         UUID NOT NULL PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    step_key   VARCHAR(32)  NOT NULL,
    status     VARCHAR(16)  NOT NULL,
    detail     VARCHAR(500),
    request    JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_project_setup_steps UNIQUE (project_id, step_key)
);
