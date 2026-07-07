-- Tenant schema baseline.
-- This file is applied by ProvisioningService into each tenant_<slug> schema (NOT the public schema).
-- Per-bounded-context tenant tables (iam, workspace, discovery, gateway) are added here as
-- subsequent versioned migrations (V2__iam.sql, V3__workspace.sql, ...) by their owners.
--
-- A no-op marker keeps V1 as the baseline, so every tenant schema starts from a known version.
DO $$
BEGIN
    -- intentionally empty: baseline only
    NULL;
END $$;
