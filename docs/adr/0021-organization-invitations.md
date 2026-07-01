# 0021. Organization invitations with tokenized email as the trust anchor

- Status: Accepted
- Date: 2026-07-01
- Deciders: Kntro-Soft team

## Context

Owners and admins need to invite people to their organization by email. An invited person may or may
not already have a Reqs-AI account, so the flow has to work both for someone who clicks the emailed
link and for someone who simply signs up with the invited address. The backend is a **Spring Modulith
modular monolith** (ADR-0002) on **one PostgreSQL instance with schema-per-tenant multitenancy**
(ADR-0003); the `members` and `organizations` registries live in `public`, ArchUnit enforces the
module boundaries (ADR-0019), and IAM already owns account/email-verification and the transactional
email machinery.

Forces:

- **Two entry paths, one identity link.** The person may accept via the link (Stage 1) or just verify
  their email during normal signup (Stage 2). Both must converge on the same result: the PENDING
  member row becomes ACTIVE and linked to the caller's user id.
- **Trust.** Possession of an unguessable token proves the link was delivered to the invited inbox, but
  the owner requires that the account that ultimately joins actually **owns the invited email**.
- **Reuse, not duplication.** IAM already has the one-time hashed-token pattern (SHA-256, `expiresAt`,
  `isValid`/`markUsed`), an `EmailNotificationPort`, `@ApplicationModuleListener` email senders, and the
  `AccountVerifiedEvent` fired when email ownership is proven. Invitations should mirror these.
- **Module boundaries must hold.** `verifyModularity` must stay green; cross-module talk is only through
  named interfaces and domain events.

## Decision

### A separate `Invitation` aggregate carrying the token lifecycle

Introduce an **`Invitation`** aggregate root in `workspace` (table `public.invitations`, like
`members`), distinct from `Member`. The existing PENDING `Member` is still created on invite (so the
members list, RBAC and existing endpoints keep working unchanged); the `Invitation` references it by
`memberId` and owns the acceptance lifecycle: `PENDING → ACCEPTED | EXPIRED | REVOKED | SUPERSEDED`.
Only the SHA-256 **hash** of the raw token is stored — the raw token travels solely in the
`MemberInvitedEvent` for the email. A partial unique index (`WHERE status = 'PENDING'`) enforces **one
active invitation per member**; resend supersedes the prior one and issues a fresh token/expiry.

### Tokenized email as the trust anchor, plus an explicit email-match rule

The emailed token is the primary trust anchor (delivered only to the invited inbox). On top of that,
**acceptance is email-bound**: `POST /api/invitations/accept` requires authentication AND that the
caller's account email equals the invited email (exact, case-insensitive). A mismatch returns **403**
and links nothing. Token possession alone is therefore not sufficient — this defends against a token
being forwarded to or intercepted by a different account. The caller's email is resolved through a new
`AccountLookupPort` in IAM's `ports` named interface (the inverse of the existing
`OrganizationLookupPort` that workspace implements for IAM), so workspace never touches IAM internals.

### Stage 1 (accept) + Stage 2 (link-on-signup)

- **Stage 1** — the invitee opens the link and calls `accept`. Unknown token → 404; expired PENDING →
  mark EXPIRED and 410; already ACCEPTED → idempotent 200; on success the member is
  `reactivate(callerUserId)`d (→ ACTIVE) and the invitation ACCEPTED. A **public** `GET
  /api/invitations/{token}` returns a minimal, non-sensitive view for the accept/signup screen.
- **Stage 2 (safety net)** — a workspace `@ApplicationModuleListener` reacts to IAM's
  `AccountVerifiedEvent` (fired only when email ownership is **proven**) and auto-accepts any PENDING
  invitations addressed to that exact email. The verified email is naturally the trust anchor here, so
  the same exact email-match rule holds by construction.

### Module-boundary wiring

- The **email listener** lives in `workspace` and calls IAM's `EmailNotificationPort`
  (`sendInvitationEmail`), which sits in the `iam::ports` named interface that workspace already
  depends on — no new dependency needed for Stage 1.
- For **Stage 2**, IAM's `domain.event` package is exposed as a new `iam::events` named interface and
  added to workspace's `allowedDependencies`, so workspace may subscribe to `AccountVerifiedEvent`.
  That event now carries the verified `email` (like `AccountCreatedEvent`), avoiding a lookup on the
  common path; `AccountLookupPort.findUserIdByAccountId` resolves the user id to link.
- `verifyModularity` passes with these two named-interface dependencies (`iam::ports`, `iam::events`).

### Configuration

Invitation TTL is `reqsai.invitation.expiry` (a `Duration`, **default 7d**) bound via
`@ConfigurationProperties`, mirroring how IAM's token TTLs are configured.

## Consequences

- Positive: existing member/RBAC code is untouched; the token lifecycle is isolated in its own
  aggregate; both entry paths converge deterministically; token + email-match gives a strong trust
  model; IAM's email/token/event infrastructure is reused rather than duplicated.
- Trade-off: the email-match rule means a user cannot accept an invitation from an account whose email
  differs from the invited address, even with a valid token — this is intentional and owner-approved.
- Trade-off: two named-interface dependencies from workspace to IAM (`ports`, `events`) widen the
  coupling slightly, but only through published, boundary-checked interfaces.
- The invitation email is delivered asynchronously (after-commit `@ApplicationModuleListener`), so a
  mail-provider failure never rolls back the invite.
