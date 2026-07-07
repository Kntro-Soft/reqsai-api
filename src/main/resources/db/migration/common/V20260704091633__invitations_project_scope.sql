-- Project-scoped invitations — extend public.invitations with an optional target project + role.
-- When an org owner/admin invites a NEW person (by email) directly to a project, the PENDING org
-- invitation carries the target project and project-role. On accept the member becomes ACTIVE and a
-- ProjectMember assignment is materialized in the tenant schema.
--
-- No cross-schema FKs: projects and project_roles live in tenant schemas (tenant_<slug>), while
-- invitations live in the public registry, so these stay as plain UUIDs. Both columns are NULLABLE —
-- a plain org invitation leaves them NULL.

ALTER TABLE public.invitations
    ADD COLUMN target_project_id UUID,
    ADD COLUMN target_role_id    UUID;
