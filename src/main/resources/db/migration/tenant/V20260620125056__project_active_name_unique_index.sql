DROP INDEX IF EXISTS idx_projects_org_name;

CREATE UNIQUE INDEX idx_projects_org_active_name
    ON projects (organization_id, lower(name))
    WHERE status = 'ACTIVE';
