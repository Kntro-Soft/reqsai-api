-- Durable integration sync jobs (ADR-0023): async Jira import / push-all runs persisted per project.
-- The job row is the source of truth for progress; STOMP pushes mirror it and a reload recovers from it.

CREATE TABLE integration_sync_jobs (
    id           UUID NOT NULL PRIMARY KEY,
    project_id   UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    job_type     VARCHAR(16)  NOT NULL, -- IMPORT | PUSH_ALL
    status       VARCHAR(16)  NOT NULL, -- RUNNING | COMPLETED | FAILED
    total        INT NOT NULL DEFAULT 0,
    processed    INT NOT NULL DEFAULT 0,
    succeeded    INT NOT NULL DEFAULT 0,
    failed       INT NOT NULL DEFAULT 0,
    message      VARCHAR(1000),
    requested_by UUID,
    finished_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   UUID,
    updated_by   UUID
);

CREATE INDEX idx_integration_sync_jobs_project_status ON integration_sync_jobs (project_id, status);

-- At most one RUNNING job per (project, type): a concurrent start gets 409 INTEGRATION_JOB_ALREADY_RUNNING.
CREATE UNIQUE INDEX uq_integration_sync_jobs_running
    ON integration_sync_jobs (project_id, job_type)
    WHERE status = 'RUNNING';
