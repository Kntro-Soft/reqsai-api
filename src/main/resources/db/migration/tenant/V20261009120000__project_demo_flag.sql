-- Demo project seeded for every new organization (US28). Excluded from the plan's project count and
-- restorable to its original content. The partial unique index keeps at most one demo per organization,
-- so provisioning stays idempotent even under a retried organization-created event.
ALTER TABLE projects
    ADD COLUMN demo BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uq_projects_org_demo
    ON projects (organization_id)
    WHERE demo;
