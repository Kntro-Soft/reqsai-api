# Database migrations

Flyway manages the schema in two independent locations:

- `src/main/resources/db/migration/common` — schema `public`, applied automatically on app startup
  (`application.yml`).
- `src/main/resources/db/migration/tenant` — applied per tenant schema, on provisioning and on
  startup for existing tenants (`ProvisioningServiceImpl`).

Both use **timestamp-based versions** (`V<yyyyMMddHHmmss>__description.sql`, UTC) instead of
sequential integers, so that migrations added on parallel branches never collide on the same version
number. See [ADR-0022](adr/0022-flyway-timestamp-based-migration-versions.md) for the full rationale,
and [ADR-0003](adr/0003-schema-per-tenant-multitenancy.md) for the schema-per-tenant model these
migrations run against.

## Creating a new migration

Never hand-name a migration file. Always use:

```bash
scripts/new-migration.sh <common|tenant> <short_description>
```

Examples:

```bash
scripts/new-migration.sh tenant add_project_tags
scripts/new-migration.sh common billing_plan_limits
```

This creates an empty file named `V<yyyyMMddHHmmss>__<short_description>.sql` in the right
directory, using the current UTC time as the version. Write your SQL in the generated file.

- `<short_description>` is sanitized to lowercase `snake_case` automatically (spaces and punctuation
  become `_`).
- If you run the script twice back to back, or two people run it on the same machine at nearly the
  same time, the script serializes the calls and bumps the version by whole seconds to guarantee two
  files are never generated with the same version — you never need to check what the "next" version
  is.
- The script does **not** protect against two people on **different machines/branches** picking the
  exact same second; that's why the version is a full timestamp instead of a sequence number — the
  odds of an exact-second collision across two independent developers are negligible, and even if it
  happens Flyway will simply fail loudly at merge/startup time rather than silently misordering
  anything.

## Choosing `common` vs `tenant`

- `common`: data shared across all tenants and not tied to a single organization's isolated schema —
  identity/accounts, organizations, invitations, subscriptions, and similar `public`-schema
  registries.
- `tenant`: anything that belongs to one organization's isolated schema — projects, discovery
  sessions, user stories, suggestions, and similar per-tenant data (ADR-0003).

If you're unsure, check whether the entity/table already has siblings in one of the two
directories — new features almost always extend an existing bounded context's tables.

## Why `outOfOrder: true`

Both Flyway configs (`application.yml` for `common`, `ProvisioningServiceImpl` for `tenant`) have
`outOfOrder: true`. With timestamp versions, the order migrations are *authored* doesn't always match
the order they end up merged into `develop`. Without `outOfOrder`, Flyway rejects a migration whose
version is lower than the highest one already applied in that environment. With it enabled, Flyway
fills in whatever is missing regardless of chronological position, and a schema with no history at
all still applies everything in ascending version order.

This means **migration review, not Flyway, is what has to catch ordering problems** — write
migrations that don't depend on another migration merged around the same time (e.g. don't assume a
column another in-flight migration is adding already exists).

## What not to do

- Don't rename an already-merged/deployed migration file. Flyway identifies a migration by its
  version, not its file content — renaming one that has already run anywhere makes Flyway think it's
  a brand-new, unapplied migration and try to run it again.
- Don't hand-pick a version number "to be safe" — always go through `scripts/new-migration.sh`.
- Don't put `tenant`-scoped tables in `common` (or vice versa) to avoid a Flyway wait; follow the
  schema-per-tenant model in ADR-0003.
