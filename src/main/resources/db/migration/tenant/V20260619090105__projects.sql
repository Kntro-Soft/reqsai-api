CREATE TABLE projects (
    id                      UUID            NOT NULL PRIMARY KEY,
    organization_id         UUID            NOT NULL,
    name                    VARCHAR(150)    NOT NULL,
    description             VARCHAR(2000),
    programming_languages   VARCHAR(100)[]  NOT NULL,
    frameworks              VARCHAR(100)[]  NOT NULL,
    client_platforms        VARCHAR(100)[]  NOT NULL,
    databases               VARCHAR(100)[]  NOT NULL,
    architecture            VARCHAR(100)    NOT NULL,
    domain                  VARCHAR(100)    NOT NULL,
    status                  VARCHAR(16)     NOT NULL,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by              UUID,
    updated_by              UUID
);

CREATE UNIQUE INDEX idx_projects_org_name ON projects (organization_id, name);

CREATE TABLE glossaries (
    id          UUID        NOT NULL PRIMARY KEY,
    project_id  UUID        NOT NULL UNIQUE REFERENCES projects(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID
);
