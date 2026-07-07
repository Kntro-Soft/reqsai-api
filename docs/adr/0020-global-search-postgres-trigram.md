# 0020. Global search with Postgres trigram lexical matching

- Status: Accepted
- Date: 2026-06-30
- Deciders: Kntro-Soft team

## Context

The frontend needs a universal command palette (⌘K) that searches across many entity types at once —
projects, user stories, organizations, members, glossary terms and project documents — from a single
query box. The backend that must serve it is a **Spring Modulith modular monolith** (ADR-0002) running
on **one PostgreSQL instance with schema-per-tenant multitenancy** (ADR-0003), where the tenant-owned
entities (projects, user stories, glossary terms, documents) live in per-tenant schemas while the
organization/member registries live in `public`.

Three forces are in tension:

- **Cross-cutting query, isolated modules.** A palette search inherently spans bounded contexts, but the
  Modulith boundaries (ArchUnit-enforced, ADR-0019) forbid one context from importing another's
  internals. A search feature must not become a backdoor that couples `workspace`, `discovery` and the
  rest together.
- **Multitenancy and authorization must hold.** Search must never leak rows from another tenant's schema,
  nor from projects the caller cannot access. The existing authorization rules (ADR-0014) — owner/admin
  see all projects, members see only assigned ones — have to be reused, not re-implemented.
- **Cost and operational simplicity.** This is an early-stage product. Standing up and operating a
  separate search cluster is disproportionate to the current need.

## Decision

Adopt a **staged approach**, and implement Stage 0 + Stage 1 now.

### Stage 0 — Postgres-native lexical search via `pg_trgm`

Use the PostgreSQL `pg_trgm` contrib extension for fuzzy lexical matching instead of a new datastore.
Each searchable column gets a **trigram GIN index**, and queries use the `%` similarity operator to
match and `similarity(col, :term)` to rank (`ORDER BY similarity(...) DESC ... LIMIT :limit`).

- `pg_trgm` is created once `WITH SCHEMA public` (mirroring how `vector`/pgvector is provisioned) via an
  idempotent `CREATE EXTENSION IF NOT EXISTS pg_trgm`, and is visible in every tenant schema through the
  request's `search_path`.
- Trigram GIN indexes are added by Flyway: a tenant migration over `projects.name`,
  `user_stories.title`, `glossary_terms.term`, `project_documents.name`; a common migration over
  `organizations.name`, `organizations.slug`, `members.display_name`, `members.email`.

### Stage 1 — an aggregator `search` module fanning out to per-context named-interface ports

Introduce a dedicated Spring Modulith `search` bounded context that owns only the HTTP endpoint
(`GET /api/search`) and the fan-out/merge logic. It depends solely on `shared` and on each searchable
context's `@NamedInterface("search")` port (e.g. `workspace::search`, `discovery::search`).

- Each context exposes its own search port that runs its own trigram queries and returns **value
  snapshots** (`shared.application.search.SearchHit`) — never a JPA entity. This keeps module boundaries
  intact: the aggregator sees a common value type, not another context's domain model.
- Each port is **authorization-filtered by an accessible-project scope** (`ProjectScope`), resolved once
  from the existing `ProjectAccessService`. Project-scoped hit types (PROJECT, USER_STORY,
  GLOSSARY_TERM, DOCUMENT) are filtered to the caller's visible projects; organization/member searches
  are scoped to the caller's own organizations/membership. The tenant is resolved from the JWT `orgId`,
  so a caller only ever searches their own tenant schema.
- The aggregator fans out sequentially (a single connection pool with one `search_path` bound per
  request), takes the top-K per type, then merges and caps to `limit`.

The `SearchHit.type` enum is the stable contract: `PROJECT`, `USER_STORY`, `ORGANIZATION`, `MEMBER`,
`GLOSSARY_TERM`, `DOCUMENT`.

### Deferred stages (not built now)

- **Stage 2 — event-driven CQRS search projection.** A dedicated read model updated from domain events
  would decouple search latency from the source tables and enable richer ranking/pagination. Deferred
  because it adds a projection to keep consistent and is not yet justified by scale.
- **Stage 3 — external search engine.** Elasticsearch/OpenSearch, Meilisearch or Typesense would give
  best-in-class relevance, typo tolerance and faceting. Deferred because it adds a cluster to operate,
  provision and secure per-tenant, and re-implements the authorization filtering we get for free inside
  Postgres.

## Alternatives considered

- **Dedicated event-driven CQRS search projection (Stage 2).** Strong long-term design, but premature:
  another read model to maintain and reconcile, for a feature whose current relevance needs are modest.
- **External search engine (Elasticsearch/Meilisearch/Typesense).** Best relevance, but the heaviest
  operationally: a new cluster, per-tenant index isolation, an ingestion pipeline, and authorization
  re-implemented outside the database. Disproportionate at this stage.
- **`LIKE '%term%'` / `ILIKE`.** Simplest, but no fuzzy matching and no index-backed ranking; leading
  wildcards cannot use a B-tree index and degrade to sequential scans. `pg_trgm` gives fuzzy matching
  *and* a usable GIN index.
- **Postgres full-text search (`tsvector`/`tsquery`).** Good for natural-language documents, but tuned
  for word/stemming matching rather than the short-label, typo-tolerant prefix/substring matching a
  command palette wants; trigram similarity fits the palette better.

## Consequences

- **Strong module boundaries preserved.** The aggregator never imports another context's internals;
  everything crosses the boundary as a `SearchHit` value snapshot through a named interface, and ArchUnit
  keeps it honest.
- **Multitenancy and authorization correct by construction.** Search runs inside the caller's tenant
  schema and reuses the existing `ProjectAccessService` scope, so no cross-tenant or unauthorized row can
  leak — no bespoke security logic to get wrong.
- **No new infrastructure.** `pg_trgm` ships with the official Postgres image (and the pgvector image we
  already run), so **no docker-compose or container-image change is needed** — only an idempotent
  `CREATE EXTENSION IF NOT EXISTS pg_trgm` plus index migrations via Flyway.
- **Ranking and pagination are deliberately naive for now.** Results are top-K per type, then merged and
  capped — there is no global relevance normalization across types, and pagination is approximate. This
  is acceptable for a palette that surfaces a handful of best matches, and is exactly what Stage 2/3 would
  improve if the need arises.
- **Adding a new searchable type is a local change.** A context adds a trigram index, a query, and a
  `SearchHit` mapping behind its `search` named interface, then the aggregator includes it in the
  fan-out — no boundary is weakened.
