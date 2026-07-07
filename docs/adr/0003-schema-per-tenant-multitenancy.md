# 0003. Schema-per-tenant multitenancy

- Status: Accepted
- Date: 2026-06-08
- Deciders: Kntro-Soft team

## Context

Reqs-AI is B2B SaaS: each customer organization's data (projects, capture sessions, requirements)
must be strongly isolated. The main options are: a shared schema with a `tenant_id` discriminator
column; a schema per tenant within one database; or a database per tenant. The discriminator approach
risks data leakage from a single missing filter; database-per-tenant is operationally heavy for an
MVP.

## Decision

Use **schema-per-tenant** in a single PostgreSQL database: one schema `tenant_<slug>` per
organization. Hibernate's `SCHEMA` multitenancy routes each session via a
`MultiTenantConnectionProvider` that sets `search_path` to the tenant schema (resetting to `public`
on release). The `orgId` JWT claim is resolved to a schema by a Caffeine-cached `TenantSchemaResolver`
that reads the global `public.organizations` registry. New tenants are provisioned by
`ProvisioningService` (create schema → run `db/migration/tenant` Flyway → roll back on failure);
`TenantMigrationRunner` migrates existing tenants on startup. Tenant aggregates therefore carry **no**
`tenant_id` column — isolation is structural.

## Consequences

- Strong isolation: a tenant's connection cannot see other schemas; the resolver fails closed to
  `public`, and `search_path` is reset on connection release to prevent leakage across pooled threads.
- Per-tenant schema evolution via Flyway, each schema with its own history table.
- Operational cost grows with tenant count (many schemas, migrations fan out on deploy); acceptable at
  the MVP scale, revisitable if tenant count becomes very large.
- Cross-tenant analytics require explicit, deliberate queries (a feature, not a bug, for isolation).
