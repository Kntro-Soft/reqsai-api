# 0023. Extensible third-party integrations, first provider Jira Cloud

- Status: Accepted
- Date: 2026-07-06
- Deciders: Kntro-Soft team

## Context

Teams that run discovery in Reqs-AI keep their delivery backlog in an external tracker. The first and
most requested target is **Jira Cloud**: an analyst approves user stories in Reqs-AI and wants to push
them into a Jira project as issues without re-typing them. The `UserStory` review lifecycle already
has an `EXPORTED` terminal state described as _"Pushed to an external tracker (e.g. Jira)"_, so the
domain anticipated this.

We want the design to generalize beyond Jira (Azure DevOps, Linear, GitHub Issues, …) rather than
bolt a one-off Jira client onto an existing context, and we want to keep the credential and the
push concern out of `discovery` and `workspace` — they are a distinct capability with their own
lifecycle, error surface and RBAC.

Forces:

- **Where do credentials live vs. where does a push target live?** A Jira site + API token is an
  organization-wide asset (billed once, administered by an org admin). A Jira *project key* and
  *issue type* are per Reqs-AI-project routing decisions any project writer makes. These have
  different owners, different RBAC and different lifecycles.
- **Auth will evolve.** Jira Cloud supports both an **API token / basic auth** (simplest, works today,
  no app registration) and **OAuth 2.0 (3LO)**. We ship the token flow now but must not paint
  ourselves into a corner that blocks OAuth later.
- **Secrets at rest.** A Jira API token is a bearer credential. It must be encrypted in the tenant
  database, never logged, and never returned by any endpoint.
- **Module boundaries must hold.** The backend is a Spring Modulith modular monolith (ADR-0002) with
  schema-per-tenant multitenancy (ADR-0003); ArchUnit + `verifyModularity` (ADR-0019) enforce that
  cross-module talk only happens through named interfaces. Reading user stories to push them must go
  through a published discovery interface, not into discovery internals.
- **Identity/auth is out of scope.** This feature must not touch IAM's account/authn model. It only
  adds project-scoped RBAC permissions to the existing workspace `Permission` catalog.

## Decision

### Housed in the reserved `gateway` bounded context

The integration lives in `com.kntro.reqsai.gateway`, the Spring Modulith application module reserved
for external integrations (`@ApplicationModule(allowedDependencies = {"shared", "workspace::api",
"discovery::api"})`), with the usual hexagonal layers (`domain`, `application`, `infrastructure`,
`interfaces`). It depends on `workspace::api` for org/project authorization context and on a new
`discovery::api` named interface for reading the stories it pushes. The Jira feature keeps its own
domain vocabulary (`IntegrationConnection`, `ProjectIntegrationTarget`, etc.) — those name the
concept, while `gateway` names the module.

### Org-level connection, project-level target (the split)

Two aggregates, two scopes:

- **`IntegrationConnection` (organization-scoped)** — one row in `integration_connections` per
  `(organization_id, provider)`. Holds the provider (`JIRA`), the Jira `site_url`, the account
  `email`, the **encrypted** API token (`secret_ciphertext BYTEA`), a `status`
  (`CONNECTED`/`DISCONNECTED`) and `last_verified_at`. A **partial unique index**
  (`WHERE status <> 'DISCONNECTED'`) enforces **at most one active connection per org per provider**.
  Managed by org admins.
- **`ProjectIntegrationTarget` (project-scoped)** — one row in `project_integration_targets` per
  project (see the uniqueness decision below). References a `connection_id`, plus the Jira
  `jira_project_key` and `issue_type_name` chosen for that Reqs-AI project. Managed by project
  writers.

This mirrors the real ownership: credentials are administered once at the top; routing is decided
per project by the people who own that project.

**Uniqueness choice — one target per project.** `project_integration_targets` is uniquely indexed on
`project_id` alone (`uq_project_integration_targets_project`), *not* on
`(project_id, connection_id)`. A Reqs-AI project pushes to exactly one Jira destination at a time; the
`PUT .../target` endpoint is an upsert that replaces the single target. This keeps the push path
unambiguous (no "which target?" question) and matches the locked REST contract, which exposes a
singular `/integration/jira/target` resource. Re-pointing a project at a different connection or Jira
project is a `PUT` overwrite.

### Provider/adapter pattern (`IntegrationProvider` port + `JiraProvider`)

The push/verify capability is expressed as an `IntegrationProvider` port in the application layer.
`JiraProvider` is the first (and currently only) implementation; it delegates the raw HTTP to a
`JiraClient` RestClient adapter in `infrastructure/jira`. Adding Azure DevOps later means adding an
`AzureDevOpsProvider` selected by the connection's `provider` value — no change to the handlers, the
endpoints or the aggregates. The `JiraClient` mirrors the existing `AssemblyAiAdapter` RestClient
style (per-call `RestClient`, typed response records, status→exception mapping).

### API token and OAuth 2.0 (3LO), side by side

Authentication started as Jira **basic auth with an API token**:
`Authorization: Basic base64(email:token)`, base URL `https://{site}/rest/api/3/...`. The credential
abstraction (`IntegrationConnection` carrying an encrypted secret + the `IntegrationProvider` seam)
is deliberately auth-mechanism-agnostic, and we have now added **OAuth 2.0 (3LO)** alongside it — the
API-token flow is unchanged.

**Update (OAuth 2.0 3LO shipped).** A `credentialType` discriminator (`API_TOKEN` | `OAUTH2`) selects
the credential shape on the same `integration_connections` table (migration `V23`, additive): OAuth
rows carry the Atlassian `cloud_id` and the **encrypted** refresh/access tokens (`oauth_refresh_ciphertext`
/ `oauth_access_ciphertext`, same `SecretCipher`/`AesGcmCipher` as the API token) plus
`oauth_access_expires_at`, while `email` + `secret_ciphertext` are relaxed to nullable and left empty.
Exactly one shape is populated per `credentialType` (app-enforced). The **same** `JiraProvider`/`JiraClient`
serve both modes: the base URL + `Authorization` header are chosen per call — `https://{site}/rest/api/3`
+ `Basic` for API tokens, `https://api.atlassian.com/ex/jira/{cloudId}/rest/api/3` + `Bearer` for OAuth.
Before an OAuth call, `JiraOAuthTokenService` refreshes the access token if it is expired/near-expiry and
persists the **rotated** tokens (a refresh failure surfaces as `JIRA_AUTH_FAILED`).

Two new org-admin-gated endpoints drive the flow, keyed off a **stateless HMAC-signed `state`** (over
org + user + short expiry + nonce, using a dedicated `JIRA_OAUTH_STATE_SECRET`) so nothing is stored to
survive the browser redirect:
`GET /organizations/{orgId}/integrations/jira/oauth/authorize-url` → `{url, state}`, and
`POST /organizations/{orgId}/integrations/jira/oauth/callback` `{code, state, cloudId?}` which validates
the state, exchanges the code, and either saves an `OAUTH2` connection (cloudId given or exactly one
accessible site) or returns the site list to choose from (multiple sites). Because authorization codes
are **single-use**, the callback caches the exchanged tokens + discovered sites under the `state` (short
in-memory TTL) so the follow-up site-selection POST completes from the cache without re-exchanging the
already-consumed code. OAuth config is **optional**: when the client id/secret/redirect are absent the
app still boots and the endpoints answer `JIRA_OAUTH_NOT_CONFIGURED` (501) so the UI disables the button.
New error codes: `JIRA_OAUTH_NOT_CONFIGURED` (501), `JIRA_OAUTH_STATE_INVALID` (400),
`JIRA_OAUTH_EXCHANGE_FAILED` (502). `IntegrationConnectionResponse` now carries `credentialType`, and
`email` is `null` for OAuth connections; no token or ciphertext is ever returned.

### Encryption at rest (AES-256-GCM)

There is no existing encryption utility, so we add one: a JPA `AttributeConverter<String, byte[]>`
(`EncryptedStringConverter`) backed by an `AesGcmCipher`. It encrypts the token with **AES-256-GCM**,
a random **12-byte IV per value prepended to the ciphertext** (`IV || ciphertext+tag`), keyed from
`INTEGRATIONS_ENCRYPTION_KEY` (base64-encoded 32 bytes) bound via `application.yml`
`${INTEGRATIONS_ENCRYPTION_KEY:}`. For local/dev and tests a documented default 32-byte test key is
provided through `application-test.yml` so the suite runs without a `.env`. The token is decrypted
only inside `JiraProvider` when building the auth header; it is never logged and never leaves the
backend in any response. Cipher failures raise `INTEGRATION_ENCRYPTION_ERROR` (500).

### Reading discovery stories through a published interface

Integrations needs the story's title/role/action/benefit/priority/story-points and its Given/When/Then
acceptance criteria to build the Jira issue. Discovery exposes a new **`discovery::api`** named
interface with a `DiscoveryStoryReadPort` returning value-only `StoryView` / `AcceptanceCriterionView`
records (no JPA entities cross the boundary), implemented inside discovery over its existing
`UserStoryRepository`. Integrations consumes only that interface; `verifyModularity` and the ArchUnit
fitness functions stay green.

### RBAC — new project permissions only

The workspace `Permission` catalog gains `INTEGRATION_READ`, `INTEGRATION_WRITE`, `INTEGRATION_DELETE`
and `INTEGRATION_SYNC`, wired into the default role permission sets exactly like the existing
resources. **Project** endpoints (target read/write/delete, story push) are gated with
`@authz.projectPermission(...)`. **Org connection** endpoints are gated with the existing org-admin
path (`@authz.orgOwnerOrAdmin(#orgId, authentication)`) — administering an org-wide credential is an
org-admin action, not a project permission. IAM identity/authn is untouched.

### Async sync jobs on Spring Batch (import / push-all)

Import (dozens of issues × one LLM transformation each) and push-all take minutes; a blocking HTTP
request locks the UI and a page reload loses everything. Both operations therefore run as
**background jobs**: `POST .../import` and `POST .../stories/push-all` answer **202 Accepted**
immediately with an `IntegrationJobResponse` snapshot, progress streams over STOMP, and the state
survives reloads. The execution engine is **Spring Batch**, chosen over a hand-rolled `@Async`
worker because it gives us, out of the box, a persistent execution ledger, chunked transactions,
a declarative per-item skip policy, and an operational vocabulary (job/step/execution) that any
Spring developer can read.

**Spring Batch in one paragraph.** A *Job* is a named, parameterized unit of work. Launching a job
with a set of *identifying* `JobParameters` creates a *JobInstance* (the logical run: "import for
domain job X"); every attempt at an instance is a *JobExecution* (rows in the `BATCH_*` metadata
tables, written by the *JobRepository*). A job is a sequence of *Steps*; our jobs have exactly one
**chunk-oriented step**, which reads items one at a time (*ItemReader*), transforms them
(*ItemProcessor*), and commits a transaction every N items (chunk size 5) — bounding transaction
size and, in restartable designs, lost work. `faultTolerant().skip(Exception)` makes a throwing
item a *skip* (counted, logged, execution continues) instead of a failure — exactly the
per-item-failure semantics the old synchronous endpoints had. We deliberately do **not** use Batch
restartability (each API launch is a fresh JobInstance keyed by the domain job UUID): a half-done
import is re-run safely because the discovery dedup skips already-imported stories.

The topology (all in `gateway.infrastructure.batch` — the engine is an infrastructure detail behind
the application's `IntegrationJobLauncher` port; handlers and REST contract never see Batch types):

- `jiraImportJob` / `jiraPushAllJob`, one chunk step each. Step-scoped readers resolve the work list
  up front (Jira fetch / story list) and fix the projection's `total`; processors delegate one item
  to the *existing* `JiraImportService` / `StoryPushService`; the writer is a no-op (services own
  their side effects).
- **Tenant propagation**: the launcher captures `TenantContext` (tenant id + schema) on the request
  thread into *non-identifying job parameters*; a `JobExecutionListener` restores it in `beforeJob`
  and clears it in `afterJob` — the same snapshot-then-restore pattern as
  `TenantAwareModuleListener`. The whole execution runs on one executor thread, so every Hibernate
  session in the job resolves the caller's schema.
- **Batch metadata lives in `public`** (`V20260709100001__spring_batch_metadata.sql`, common
  migration) with the JobRepository configured with table prefix **`public.BATCH_`**
  (`shared/.../BatchConfiguration extends JdbcDefaultBatchConfiguration`). Rationale: the single
  DataSource rewrites `search_path` per tenant, so unqualified `BATCH_*` SQL could land in an
  arbitrary tenant schema; qualifying every metadata query makes it immune to whatever
  `search_path` a pooled connection carries. Batch metadata is operational, org-agnostic data —
  global like `public.organizations`. (Boot 4 / Batch 6 default to an in-memory "resourceless"
  JobRepository; subclassing `JdbcDefaultBatchConfiguration` is the opt-in to durable JDBC metadata,
  and `spring.batch.job.enabled=false` stops Boot replaying jobs at startup.)
- **Domain projection, not `BATCH_*` exposure**: the API reads/writes the per-tenant
  `integration_sync_jobs` row (`{id, projectId, jobType, status, total, processed, succeeded,
  failed, message, createdAt, finishedAt}`), updated per item by step listeners and finalized in
  `afterJob`. The projection is tenant-scoped, queryable per project, and keeps the REST/STOMP
  contract stable even if the engine changes; the `BATCH_*` tables stay an internal ledger
  (1:1 linked via the identifying `domainJobId` parameter).
- **Realtime + recovery**: every counter update is broadcast as the full job snapshot on
  `/topic/projects/{projectId}/integration-jobs` (same JSON as `IntegrationJobResponse`); a reloaded
  client re-attaches via `GET .../jobs?active=true` and `GET .../jobs/{jobId}` — the durable row is
  the source of truth, STOMP is only the push channel.
- **Concurrency**: at most one RUNNING job per (project, type), enforced in the application layer
  (pre-check + partial unique index backstop → 409 `INTEGRATION_JOB_ALREADY_RUNNING`) — Batch's
  JobInstance uniqueness is not used for this rule.
- Import duplicates count toward `processed` only (neither `succeeded` nor `failed`); the terminal
  message summarizes them ("N duplicados omitidos"). A fatal error (e.g. Jira unreachable in the
  reader) fails the step, and `afterJob` marks the projection FAILED with the first failure message.
- The single-story push and the import preview remain synchronous (fast, no job).

### Error surface

- Domain: `IntegrationsError` — `INTEGRATION_CONNECTION_NOT_FOUND` (404),
  `INTEGRATION_ALREADY_CONNECTED` (409), `INTEGRATION_TARGET_NOT_CONFIGURED` (409),
  `INTEGRATION_JOB_ALREADY_RUNNING` (409), `INTEGRATION_JOB_NOT_FOUND` (404),
  `JIRA_PROJECT_NOT_FOUND` (404), `JIRA_OAUTH_NOT_CONFIGURED` (501), `JIRA_OAUTH_STATE_INVALID` (400).
- Infrastructure: `IntegrationsInfrastructureError` — `JIRA_AUTH_FAILED` (401),
  `JIRA_UNREACHABLE` (502), `JIRA_PUSH_FAILED` (502), `INTEGRATION_ENCRYPTION_ERROR` (500),
  `JIRA_OAUTH_EXCHANGE_FAILED` (502).

Both are `ErrorCatalog` enums auto-mapped by the shared `GlobalExceptionHandler`; infrastructure
errors never leak the token or the internal cause to the client.

## Consequences

- Positive: credentials and routing sit at their natural owners; the provider seam and the
  auth-mechanism-agnostic credential make Azure DevOps / OAuth additive; the token is encrypted at
  rest and never exposed; discovery is read through a boundary-checked interface; the existing
  `EXPORTED` story state is finally reachable.
- Trade-off: one active connection per org per provider and one target per project are intentional
  simplifications for the first release; multi-connection / multi-target routing can be revisited by
  relaxing the two unique indexes without a shape change to the endpoints.
- Trade-off: a symmetric AES-GCM key in config means key rotation is a manual re-encrypt for now; a
  KMS-backed key can replace the converter's key source later without touching the model.
- The push-all and import jobs capture per-item failures (skip policy) and continue, so one bad
  story/issue never aborts the rest; a stuck modal is gone — the UI follows the job row.
- Trade-off: Spring Batch adds metadata tables and a learning curve, but buys a durable execution
  ledger, chunked transactions and skip/retry semantics we would otherwise reimplement; the engine
  stays swappable behind the `IntegrationJobLauncher` port.
