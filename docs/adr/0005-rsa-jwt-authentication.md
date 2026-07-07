# 0005. Stateless authentication with RS256 JWT

- Status: Accepted
- Date: 2026-06-08
- Deciders: Kntro-Soft team

## Context

The backend is stateless and deployed to AWS ECS Fargate (potentially multiple tasks), so server-side
sessions are undesirable. Authentication must carry the tenant (`orgId`) so each request can be routed
to the correct schema. We must choose a JWT signing strategy: symmetric (HS256, shared secret) or
asymmetric (RS256, key pair).

## Decision

Use **stateless JWT signed with RS256** (asymmetric RSA). The private key signs tokens; the public key
verifies them. Tokens carry `sub` (user id), `orgId` (tenant), and `role`. Keys are configured via
`JwtProperties` (`app.jwt.*`) and loadable from classpath (dev) or filesystem (prod secret mount).
Dev keys are generated with `scripts/generate-jwt-keys.sh` and are git-ignored; production keys are
provided via AWS Secrets Manager. Spring Security runs stateless with CSRF disabled; `JwtAuthenticationFilter`
validates the token, binds the tenant in `TenantContext`, and clears it in a `finally` block.

## Consequences

- The signing secret (private key) never has to be shared with verifiers; other services / the
  frontend can verify tokens with only the public key.
- Cleaner key rotation and a smaller blast radius than a shared HS256 secret.
- No server session state — horizontally scalable.
- Slightly more setup than HS256 (key-pair management); mitigated by the generation script and mounted
  secrets.
- Revocation is not built in (inherent to stateless JWT); short access-token lifetimes + refresh
  tokens mitigate this, to be implemented by the `iam` context.
