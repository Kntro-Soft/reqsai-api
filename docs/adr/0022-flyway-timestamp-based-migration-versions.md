# 0022. Timestamp-based Flyway migration versions

- Status: Accepted
- Date: 2026-07-06
- Deciders: Kntro-Soft team

## Context

Both `db/migration/common` and `db/migration/tenant` (ADR-0003) used sequential integer Flyway
versions (`V1`, `V2`, ...). With several developers working on parallel `feature/*` branches, two
branches can independently add the next number (e.g. both add `V21`). The collision is invisible
until both branches land on `develop`, where it surfaces as a duplicate version — Flyway refuses to
run — or as a silent semantic clash if one file is renamed to resolve the conflict, which discards the
authorship information encoded in the number.

Forces:

- **No natural total order across branches.** Sequential integers assume a single linear stream of
  migrations being created, which doesn't hold once N people branch from `develop` concurrently.
- **`outOfOrder` was off.** Flyway's default (`outOfOrder = false`) rejects a migration with a version
  lower than the highest already-applied version. Once versions are no longer guaranteed to merge in
  the same order they were authored, this default becomes a hard blocker for otherwise-valid merges.
- **Migrations must stay per-environment idempotent.** Each environment's `flyway_schema_history`
  (`public` schema for `common`, one per tenant schema — `ProvisioningServiceImpl`) tracks applied
  versions independently; a fresh environment must still apply everything in a valid order, while an
  environment with partial history must only pick up what's missing.

## Decision

### Version = UTC commit timestamp, second precision

Migration versions are now `V<yyyyMMddHHmmss>` (UTC), taken from the moment the file is authored,
not a hand-picked sequence number. Two developers on different branches now collide only if they
create a migration in the very same second — using `scripts/new-migration.sh` (see
`docs/MIGRATIONS.md`) that window is closed further: the script serializes local invocations with a
lock and bumps the version by whole seconds if the target file already exists, so a single machine
never produces a duplicate. All 32 existing migrations (12 `common`, 20 `tenant`) were renamed to
their original commit timestamp, preserving relative order; two batches that had multiple migrations
committed in the same second were spread across consecutive seconds instead of using dotted
sub-versions (`V<ts>.1`, `V<ts>.2`), to keep every version in the same plain `V<yyyyMMddHHmmss>`
shape.

### `outOfOrder: true` on both Flyway configs

Enabled in `application.yml` (`common`, schema `public`) and in `ProvisioningServiceImpl`
(`tenant`, per-schema `Flyway.configure()`). This is required, not cosmetic: with timestamp
versions, the order migrations are *authored* no longer guarantees the order they *merge* into
`develop`. A migration authored earlier can legitimately merge after one authored later. With
`outOfOrder: true`, Flyway fills in whatever is missing from an environment's history regardless of
whether it's chronologically "behind" the highest applied version, instead of refusing to start.
On a schema with no history at all, Flyway still applies every migration in ascending version order —
`outOfOrder` only changes behavior once a `flyway_schema_history` already exists.

## Consequences

- Positive: creating a migration on a feature branch no longer requires knowing what the next free
  number is on `develop` — collisions across branches are effectively eliminated.
- Positive: a migration merged after another (in `develop`'s commit order) but authored earlier does
  not block app startup or tenant provisioning.
- Trade-off: version numbers are no longer a small, easy-to-read sequence; the file name carries the
  full timestamp. Mitigated by keeping the description suffix meaningful and by `scripts/new-migration.sh`
  generating the name automatically.
- Trade-off: `outOfOrder: true` means Flyway will *not* detect a migration that should have been
  reviewed before another already-applied one — the team relies on PR review and CI, not Flyway, to
  catch semantically-incompatible orderings (e.g. a migration that assumes a column another
  out-of-order migration hasn't added yet). Migrations should stay independent of each other's
  side effects where possible.
- Neutral: no already-deployed environment had these migrations applied under the old numbering at
  the time of the rename (confirmed before renaming), so no `flyway_schema_history` backfill was
  needed. Any future rename of an *already-applied* migration would require a manual
  `flyway_schema_history` update, since Flyway identifies migrations by version, not file content.
