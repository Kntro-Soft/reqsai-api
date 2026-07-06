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

### A new `integrations` bounded context

Introduce `com.kntro.reqsai.integrations` as its own Spring Modulith application module
(`@ApplicationModule(allowedDependencies = {"shared", "workspace::api", "discovery::api"})`), with the
usual hexagonal layers (`domain`, `application`, `infrastructure`, `interfaces`) plus an `api`
named-interface package reserved for future cross-module exposure. It depends on `workspace::api` for
org/project authorization context and on a new `discovery::api` named interface for reading the
stories it pushes.

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

### API token now, OAuth 2.0 (3LO) later

Authentication today is Jira **basic auth with an API token**:
`Authorization: Basic base64(email:token)`, base URL `https://{site}/rest/api/3/...`. The credential
abstraction (`IntegrationConnection` carrying an encrypted secret + the `IntegrationProvider` seam)
is deliberately auth-mechanism-agnostic: adding OAuth 2.0 (3LO) later means storing an OAuth
refresh/access token in the same encrypted secret column (or a sibling column), adding a
`credentialType` discriminator, and having `JiraProvider` build an `Authorization: Bearer` header
instead of `Basic` — the endpoints, RBAC, target model and push flow are unchanged. No OAuth code
ships now; the seam is what ships.

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

### Error surface

- Domain: `IntegrationsError` — `INTEGRATION_CONNECTION_NOT_FOUND` (404),
  `INTEGRATION_ALREADY_CONNECTED` (409), `INTEGRATION_TARGET_NOT_CONFIGURED` (409),
  `JIRA_PROJECT_NOT_FOUND` (404).
- Infrastructure: `IntegrationsInfrastructureError` — `JIRA_AUTH_FAILED` (401),
  `JIRA_UNREACHABLE` (502), `JIRA_PUSH_FAILED` (502), `INTEGRATION_ENCRYPTION_ERROR` (500).

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
- The push-all endpoint captures per-story failures and continues the batch, so one bad story never
  aborts the export of the rest.
```
