-- GitHub-style RBAC floor: every project member of an organization gets a base permission applied
-- on top of (additive with) their explicit project role. NONE grants nothing extra; READ grants a
-- read-only baseline across the workspace resources. Owners/admins bypass this and keep full access.
-- Lives in the PUBLIC schema alongside the rest of the organizations registry.

ALTER TABLE public.organizations
    ADD COLUMN member_base_permission VARCHAR(16) NOT NULL DEFAULT 'READ';
