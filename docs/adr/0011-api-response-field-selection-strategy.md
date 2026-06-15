# 0011. API response field-selection strategy

- Status: Accepted
- Date: 2026-06-14
- Deciders: Kntro-Soft team
- Builds on: [0010](0010-use-case-vertical-slice-workflow.md) (vertical slice workflow)

## Context

Every aggregate has audit fields (`createdAt`, `updatedAt`, `createdBy`, `updatedBy`) plus
domain-specific fields. We need a consistent rule for:

1. Which fields go into each REST response DTO.
2. How to avoid returning more data than the client needs (OWASP **API3:2023 — Broken Object
   Property Level Authorization** explicitly lists "overly verbose responses" as a top-3 API
   security risk).
3. How to prevent DTO proliferation as the API grows (many DTOs → forgetting to update one →
   stale or leaking data).

### Options considered

| Option                                           | Description                                                                                                                                                                               | Why rejected                                                                                                                                                                            |
|--------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **One DTO per resource** *(chosen)*              | One shared DTO per aggregate (e.g. `DiscoverySessionResponse`) used by all endpoints for that resource; fields explicitly chosen. Split into Summary/Detail only when a real need arises. | —                                                                                                                                                                                       |
| **One DTO per use case**                         | Separate `CreateXxxResponse`, `GetXxxResponse`, `ListXxxResponse`… for every operation.                                                                                                   | Extreme proliferation; the same fields end up duplicated across many classes with no real benefit at this scale.                                                                        |
| **`@JsonView` (Jackson)**                        | Single DTO class, fields annotated with view markers; controller selects view.                                                                                                            | Couples serialisation concerns to the DTO with annotations; less readable; unusual for new contributors.                                                                                |
| **Sparse fieldsets (JSON:API / Google AIP-157)** | Client passes `?fields=id,title` to pick fields at runtime.                                                                                                                               | Client-driven selection shifts the security burden to runtime whitelist validation; adds complexity without benefit for a known internal frontend. Revisit when the API becomes public. |
| **GraphQL**                                      | Client defines the response shape.                                                                                                                                                        | Paradigm shift; unjustified at this stage.                                                                                                                                              |

## Decision

### Rule 1 — One DTO per resource, shared across use cases

All endpoints for the same resource share one DTO:

```
POST   /projects/{p}/sessions    → DiscoverySessionResponse  (create)
GET    /projects/{p}/sessions    → DiscoverySessionResponse  (list)
GET    /projects/{p}/sessions/{id} → DiscoverySessionResponse (detail)
```

The DTO is split into `XxxSummaryResponse` + `XxxDetailResponse` only when a list endpoint
demonstrably needs a lighter payload than the detail view (Rule 5).

### Rule 2 — Fields included in every response DTO

| Category                                                | Include? | Rationale                                                                    |
|---------------------------------------------------------|----------|------------------------------------------------------------------------------|
| Business / domain fields                                | ✅ Yes    | Core data the client needs                                                   |
| `createdAt`, `updatedAt`                                | ✅ Yes    | Clients display "created X ago", sort by recency                             |
| `createdBy`, `updatedBy`                                | ❌ No     | Internal audit trail; not needed by the frontend                             |
| Large / expensive fields (`transcript`, `embedding`, …) | ❌ No     | Separate endpoint on explicit request (e.g. `GET /sessions/{id}/transcript`) |

### Rule 3 — No `createdBy` / `updatedBy` in responses

These are persisted for compliance and internal traceability only.
They must not appear in any response DTO unless a specific use case explicitly requires them
(e.g. an admin audit log endpoint).

### Rule 4 — Consistency across all aggregates

Every response DTO must follow the same rule. Inconsistency between aggregates
(one includes `createdAt`, another does not) is treated as a defect.

### Rule 5 — Separate endpoints for heavy fields

Fields that can be large (full text, binary, embeddings) are never included in the standard
resource response. They are served from a dedicated sub-resource endpoint so that list/detail
views stay fast.

```
GET /api/projects/{p}/sessions/{id}            → DiscoverySessionResponse  (no transcript)
GET /api/projects/{p}/sessions/{id}/transcript → { "transcript": "..." }
```

### Rule 6 — Future: Summary vs Detail split

When the API grows and a list endpoint needs a lighter payload than a detail endpoint, introduce
a second DTO (`XxxSummaryResponse` + `XxxDetailResponse`) rather than switching to `@JsonView`
or sparse fieldsets. Two explicit DTOs are clearer and safer than one annotated class.

## Consequences

- **Security:** server controls exactly what is returned — OWASP API3:2023 compliant by
  construction; no runtime whitelist needed.
- **Consistency:** all response DTOs include `createdAt` / `updatedAt` and exclude
  `createdBy` / `updatedBy` and heavy fields.
- **Maintenance:** one shared DTO per resource; new fields are added once and reviewed in the PR.
- **Trade-off:** list and detail endpoints share the same DTO until Rule 6 is triggered —
  slightly over-fetching on lists. Acceptable at this scale.
