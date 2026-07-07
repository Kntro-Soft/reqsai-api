-- At most one live (RECORDING or PAUSED) session per project.
--
-- A partial unique index makes the database the final arbiter of the "single active session" rule, so
-- two concurrent starts/resumes cannot both win even if their application-level checks interleave — the
-- losing transaction hits a unique violation. The application layer still checks first to return a clean
-- 409 SESSION_ALREADY_ACTIVE in the common (uncontended) case.
CREATE UNIQUE INDEX IF NOT EXISTS uq_sessions_project_active
    ON discovery_sessions (project_id)
    WHERE status IN ('RECORDING', 'PAUSED');
