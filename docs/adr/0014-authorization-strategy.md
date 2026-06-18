# 0014. Authorization strategy

- Status: Accepted
- Date: 2026-06-18
- Deciders: Kntro-Soft team

## Context

The API exposes multi-tenant, project-scoped resources. Three distinct authorization questions
arise on every request, and different layers are best equipped to answer each one:

1. **Is the caller authenticated?** — does the JWT exist and is it valid?
2. **Does the caller have the right role?** — role-based access (e.g. `ROLE_USER`, `ROLE_ADMIN`).
3. **Does the caller have access to this specific resource?** — attribute-based, depends on the
   resource's data.

Before this ADR, all three checks were implicit: (1) handled by the JWT filter, (2) absent, and
(3) applied inconsistently in handlers — some used `.filter(s -> s.getProjectId().equals(...))`,
others did not.

The absence of a consistent ownership check exposes the API to **IDOR / BOLA** (Insecure Direct
Object Reference, OWASP API1:2025): a tenant user could send a valid `sessionId` belonging to a
different project and the handler would still find and mutate it.

## Decision

### Layer responsibilities

| Layer                      | Where                                                | Question answered                                     | Failure response                          |
|----------------------------|------------------------------------------------------|-------------------------------------------------------|-------------------------------------------|
| Authentication             | `JwtAuthenticationFilter`                            | Is the token valid?                                   | `401 Unauthorized`                        |
| Role check                 | `@PreAuthorize` on controller method                 | Does the caller have the required role?               | `403 Forbidden`                           |
| Tenant isolation           | Schema-per-tenant (ADR-0003)                         | Does the caller belong to this org?                   | automatic — wrong schema = data not found |
| **Resource scope**         | Handler `.filter(s -> s.getProjectId().equals(...))` | Does this resource exist in this URL scope?           | `404 Not Found`                           |
| **Ownership / membership** | `@PreAuthorize("@xSecurity.isCreator(...)")`         | Does this caller own or have access to this resource? | `403 Forbidden`                           |

### Resource scope check — handler filter

For every command that carries a `projectId` + `sessionId`, the handler **must** verify that the
session's stored `projectId` matches the one from the URL:

```java
sessions.findById(command.sessionId())
    .filter(s -> s.getProjectId().equals(command.projectId()))
    .orElseThrow(() -> DiscoveryExceptions.sessionNotFound(command.sessionId()));
```

This is **not** a `@PreAuthorize` for two reasons:

- The correct HTTP response when a resource doesn't exist in a given scope is `404`, not `403`.
  A `@PreAuthorize` would throw `AccessDeniedException` → `403`, leaking that the ID exists.
- A `@PreAuthorize` bean would load the session to check the `projectId`, then the handler would
  load it again — two queries for one operation. The handler filter keeps it to one query.

### Ownership / membership check — `@PreAuthorize` bean

When user-level ownership matters (only the session creator may start recording, or only project
members may operate on a session), a dedicated evaluator bean is used so the logic is reusable
across all endpoints and the controller stays free of business logic:

```java
@Component("sessionSecurity")
@RequiredArgsConstructor
public class SessionSecurityEvaluator {
    private final DiscoverySessionRepository sessions;

    public boolean isCreator(UUID sessionId, Authentication auth) {
        UUID callerId = UUID.fromString(auth.getName());
        return sessions.findById(sessionId)
                .map(s -> s.getCreatedBy().equals(callerId))
                .orElse(false);
    }
}
```

```java
@PreAuthorize("@sessionSecurity.isCreator(#sessionId, authentication)")
ResponseEntity<...> start(@PathVariable UUID projectId, @PathVariable UUID sessionId) { ... }
```

When a `ProjectMember` model is added in the future, a `ProjectSecurityEvaluator` replaces the
creator check with a membership check — controllers need no change.

### projectId belongs in commands

Every command that operates on a project-scoped session carries `(UUID projectId, UUID sessionId)`.
The `projectId` is the **authorization scope parameter** — it enables the handler filter and, in
the future, the `@PreAuthorize` membership check. Removing it would require a separate DB lookup
to reconstruct the scope.

### What is NOT implemented yet

- `@PreAuthorize("hasRole('ROLE_USER')")` on lifecycle endpoints — implicit from `SecurityConfig`
  (`anyRequest().authenticated()`), but explicit annotation would be clearer.
- `ProjectMember` entity + `ProjectSecurityEvaluator.isMember()` — deferred until a collaboration
  use case requires multi-user project access.

## Consequences

**Positive**
- IDOR/BOLA is closed at the handler layer with a single `.filter()` call per handler — consistent
  across all project-scoped operations.
- The `@PreAuthorize` bean pattern centralizes ownership logic; all endpoints share the same
  evaluator without duplicating checks in every handler.
- The `projectId` in commands makes the scope explicit and auditable in logs.
- HTTP semantics are correct: scope mismatch → `404`, permission denial → `403`.

**Negative**
- The handler filter is a manual convention — nothing enforces that a new handler includes it.
  Code review must catch omissions until a shared base class or annotation is introduced.

**Neutral**
- Tenant isolation (schema-per-tenant) continues to handle cross-org data separation with no
  application code. This ADR covers only intra-tenant, project-level authorization.
