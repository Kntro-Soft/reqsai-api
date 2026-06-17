# 0013. Exception handling strategy

- Status: Accepted
- Date: 2026-06-17
- Deciders: Kntro-Soft team

## Context

The codebase has two kinds of failures:

- **Domain rule violations** — a business invariant the caller broke (session in the wrong state,
  near-duplicate story, entity not found). The caller caused the problem; the message is
  informative and safe to expose.
- **External-service failures** — an adapter could not reach an AI model, STT provider, or embedding
  API. The _infrastructure_ caused the problem; internal messages (`"Null response from AI model"`,
  `"AssemblyAI job timed out"`) must not reach API consumers.

Before this ADR, infrastructure adapters imported `DiscoveryExceptions` and threw `DomainException`
for external failures. This caused two concrete bugs:

1. **Session stuck in `PROCESSING` forever.** `StartDiscoveryProcessingCommandHandler` guards
   re-throw of domain violations: `catch (DomainException e) { throw e; }`. Any `DomainException`
   from an adapter hit that guard and bypassed the `catch (Exception e)` branch — so the session
   was never marked `FAILED`.

2. **Internal messages leaked to clients.** `GlobalExceptionHandler` renders `DomainException`
   messages verbatim in the RFC 9457 `ProblemDetail` body. Adapter error strings reached consumers.

Additionally, infrastructure error codes (`REQUIREMENT_GENERATION_UNAVAILABLE`,
`TRANSCRIPTION_UNAVAILABLE`) were embedded in `DiscoveryError` — a domain enum — which muddied the
boundary: domain errors should only describe business-rule violations.

## Decision

We establish a two-layer exception strategy, consistent across all bounded contexts.

---

### Layer 1 — Domain exceptions

#### 1a. `XxxError` enum (domain layer)

Each BC defines an enum implementing `ErrorCatalog` placed in `<bc>.domain.exception`. It holds
**business-rule violation** codes only. Example:

```java
// discovery.domain.exception
public enum DiscoveryError implements ErrorCatalog {
    DUPLICATE_USER_STORY(HttpStatus.CONFLICT),
    INVALID_SESSION_STATUS(HttpStatus.UNPROCESSABLE_CONTENT),
    REQUIREMENT_GENERATION_FAILED(HttpStatus.UNPROCESSABLE_CONTENT),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND),
    USER_STORY_NOT_FOUND(HttpStatus.NOT_FOUND);
    ...
}
```

> **Note on shared codes.** Some codes serve both layers. `REQUIREMENT_GENERATION_FAILED` is used by
> the application layer ("no transcript") _and_ by infrastructure adapters ("AI returned null").
> It stays in `DiscoveryError` because its HTTP semantics belong to the domain (`422 Unprocessable
> Content`). Infrastructure references it via `InfrastructureException`, but client-visible
> behaviour differs (see §GlobalExceptionHandler below).

#### 1b. `XxxExceptions` factory (domain layer)

Each BC defines a final utility class that produces typed `DomainException` (or
`EntityNotFoundException`) instances. Placed alongside the error enum:

```java
// discovery.domain.exception
public final class DiscoveryExceptions {
    public static DomainException duplicateUserStory(double similarity) { ... }
    public static EntityNotFoundException sessionNotFound(UUID id) { ... }
    public static DomainException invalidSessionStatus(SessionStatus current, SessionStatus required) { ... }
    public static DomainException requirementGenerationFailed(String reason) { ... }
}
```

Domain model classes (`DiscoverySession`, `UserStory`, …) and command handlers use this factory.
No string-formatting happens outside it.

---

### Layer 2 — Infrastructure exceptions

#### 2a. `XxxInfrastructureError` enum (infrastructure layer)

Each BC with infrastructure adapters adds a second `ErrorCatalog` enum in
`<bc>.infrastructure.exception`. It holds codes for **external-service failures** — codes that must
not appear in the domain enum:

```java
// discovery.infrastructure.exception
public enum DiscoveryInfrastructureError implements ErrorCatalog {
    REQUIREMENT_GENERATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    TRANSCRIPTION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    EMBEDDING_FAILED(HttpStatus.SERVICE_UNAVAILABLE);
    ...
}
```

#### 2b. `XxxInfrastructureExceptions` factory (infrastructure layer)

Mirrors `XxxExceptions` but produces `InfrastructureException` instances. Placed alongside the
error enum. All adapters use this factory — never inline exception construction, never
`DiscoveryExceptions`:

```java
// discovery.infrastructure.exception
public final class DiscoveryInfrastructureExceptions {
    public static InfrastructureException generationUnavailable() { ... }
    public static InfrastructureException generationFailed(String reason) { ... }
    public static InfrastructureException generationFailed(String reason, Throwable cause) { ... }
    public static InfrastructureException transcriptionUnavailable() { ... }
    public static InfrastructureException transcriptionFailed(String reason) { ... }
    public static InfrastructureException transcriptionFailed(String reason, Throwable cause) { ... }
}
```

#### 2c. Provider-specific subclasses

Adapters that wrap a single external provider (e.g. embedding) may define a subclass of
`InfrastructureException` that prepends the provider name to every message for fast log triage:

```java
// discovery.infrastructure.exception
public class EmbeddingProviderException extends InfrastructureException {
    public EmbeddingProviderException(String provider, String reason) {
        super(DiscoveryInfrastructureError.EMBEDDING_FAILED, "[" + provider + "] " + reason, null);
    }
}
```

#### 2d. The golden rule: adapters never import domain exceptions

No adapter file may import `DiscoveryExceptions`, `DomainException`, or any `XxxError` domain enum
(except to reference a shared code like `REQUIREMENT_GENERATION_FAILED` when constructing an
`InfrastructureException`). All outward-facing adapter failures go through the infrastructure
factory or provider-specific subclass.

---

### How exceptions flow through `GlobalExceptionHandler`

```
DomainException         → HTTP status from ErrorCatalog
                          message rendered verbatim in ProblemDetail
                          logged at WARN (no stacktrace)

InfrastructureException → HTTP status from ErrorCatalog
                          message replaced with "A server error occurred" in ProblemDetail
                          full message + stacktrace logged at ERROR

EntityNotFoundException → 404, message rendered (subtype of DomainException)
```

This is why adapters must throw `InfrastructureException`: only then does the handler hide internal
detail and log with a stacktrace. A `DomainException` from an adapter reaches the client and skips
the `catch (Exception e)` branch that marks the session `FAILED`.

---

### Package layout per BC

```
<bc>/
  domain/
    exception/
      XxxError.java                  ← ErrorCatalog enum (business violations)
      XxxExceptions.java             ← DomainException factory
  infrastructure/
    exception/
      XxxInfrastructureError.java    ← ErrorCatalog enum (external failures)
      XxxInfrastructureExceptions.java ← InfrastructureException factory
      ProviderSpecificException.java ← optional subclass per external provider
```

`InfrastructureException` itself lives in `shared.domain.exception` (alongside `DomainException`)
because it extends `DomainException` and is handled by the shared `GlobalExceptionHandler`. It is
a shared contract, not a discovery-specific type.

## Consequences

**Positive**

- Sessions are correctly marked `FAILED` when an adapter throws — `InfrastructureException` is not
  caught by `catch (DomainException e) { throw e; }` and reaches `catch (Exception e)` instead.
- Internal adapter messages never reach API clients.
- Domain error enums contain only business-rule violation codes; infrastructure codes are isolated.
- Pattern is uniform across BCs: adding a new BC means adding `XxxError` + `XxxExceptions` (domain)
  and, if it has adapters, `XxxInfrastructureError` + `XxxInfrastructureExceptions` (infrastructure).
- Central factories prevent scattered inline message strings and make global message changes easy.

**Neutral**

- Some codes (e.g. `REQUIREMENT_GENERATION_FAILED`) appear in the domain enum but are also
  referenced by infrastructure. This is intentional: the HTTP semantics and error code are domain
  concerns; the exception _type_ determines client visibility, not the error code enum.
- Each BC carries two catalogs. The duplication is deliberate — each catalog changes for different
  reasons (business rules vs. external API contracts) and should evolve independently.

**Negative**

- A developer unfamiliar with the pattern could mistakenly throw `DomainException` from an adapter.
  The compiler will not catch this. Code review and the golden rule in §2d are the safeguards.
