CREATE TABLE members (
    id              UUID         NOT NULL PRIMARY KEY,
    organization_id UUID         NOT NULL,
    user_id         UUID,
    email           VARCHAR(320) NOT NULL,
    display_name    VARCHAR(150) NOT NULL,
    role            VARCHAR(16)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    invited_by      UUID,
    invited_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID
);

CREATE INDEX idx_members_organization_id ON members (organization_id);
CREATE UNIQUE INDEX uq_members_org_email_ci ON members (organization_id, lower(email));
CREATE UNIQUE INDEX uq_members_org_user_id ON members (organization_id, user_id);

CREATE TABLE project_roles (
    id          UUID         NOT NULL PRIMARY KEY,
    project_id  UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    permissions VARCHAR(1000) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID
);

CREATE INDEX idx_project_roles_project_id ON project_roles (project_id);
CREATE UNIQUE INDEX uq_project_roles_project_name_ci ON project_roles (project_id, lower(name));

CREATE TABLE project_members (
    id          UUID        NOT NULL PRIMARY KEY,
    project_id  UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    member_id   UUID        NOT NULL REFERENCES members(id),
    role_id     UUID        NOT NULL REFERENCES project_roles(id),
    assigned_by UUID        NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID
);

CREATE INDEX idx_project_members_project_id ON project_members (project_id);
CREATE UNIQUE INDEX uq_project_members_project_member ON project_members (project_id, member_id);
