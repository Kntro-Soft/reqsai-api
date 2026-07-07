# 0018. CVE scanning with OWASP Dependency-Check

- Status: Accepted
- Date: 2026-06-18
- Deciders: Kntro-Soft team

## Context

The project depends on ~60 third-party libraries (Spring Boot, JJWT, Flyway, pgvector, Spring AI,
Testcontainers, etc.). Any of these can have publicly disclosed CVEs that introduce security risk.
CodeQL (already configured) performs static analysis of our own code but does not check third-party
dependency vulnerabilities. Dependabot raises PRs for version updates but does not block CI on
unpatched CVEs between update cycles.

## Decision

Add **OWASP Dependency-Check** (`org.owasp.dependencycheck:12.2.2`) as a **separate weekly
workflow** (`owasp.yml`) rather than part of the PR pipeline.

Rationale for a separate workflow:
- First run downloads the NVD database (~500 MB, 5–10 minutes) — unacceptable latency for PRs.
- CVE reports are informational for development; only Critical-severity (CVSS ≥ 9) blocks the build.
- The NVD API key (`NVD_API_KEY` secret) speeds up subsequent runs significantly.

Configuration:
- `failBuildOnCVSS = 9.0f` — only Critical CVEs (CVSS ≥ 9.0) fail the workflow.
- `scanConfigurations = listOf("runtimeClasspath")` — test dependencies excluded (not shipped).
- `owasp-suppressions.xml` — template for suppressing known false positives with documented justification.
- Schedule: every Monday 08:00 UTC + `workflow_dispatch` for on-demand runs.

## Consequences

- The team gets a weekly report on known CVEs in production dependencies.
- Critical CVEs create an actionable blocker (workflow failure) without disrupting the daily PR flow.
- False positives (common with OWASP) are managed via `owasp-suppressions.xml` with required notes.
- The NVD rate-limit (without an API key) allows 5 requests/30s; the API key removes this limit.
  Register at https://nvd.nist.gov/developers/request-an-api-key and add to GitHub secrets.
