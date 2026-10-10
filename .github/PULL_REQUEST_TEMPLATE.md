## Description

<!-- What does this PR change and why? -->

**Bounded context / area:** <!-- iam | billing | workspace | discovery | gateway | shared | build | ci -->

Closes # <!-- issue number, e.g. Closes #12 (one line per issue; use "Refs #12" if it does not finish it) -->

---

## Type of Change

- [ ] `feat` — new feature
- [ ] `fix` — bug fix
- [ ] `refactor` — code change without behavior change
- [ ] `test` — tests only
- [ ] `docs` — documentation only
- [ ] `build` / `ci` — build, dependencies, or CI/CD
- [ ] `chore` — maintenance

---

## Checklist

- [ ] The PR targets `develop` (only `release/X.Y.Z` and `hotfix/X.Y.Z` target `main`)
- [ ] Branch name follows `feature/<issue>-<slug>`, `bugfix/<issue>-<slug>`, `release/X.Y.Z` or `hotfix/X.Y.Z`
- [ ] The issue is linked above and is on the ReqsAI project board
- [ ] Commits follow Conventional Commits
- [ ] `./gradlew build` passes locally (compile + tests + `verifyModularity`)
- [ ] New cross-module access respects module boundaries (no reaching into another module's internals)
- [ ] Added/updated tests for the change (Testcontainers for DB-dependent code)
- [ ] No secrets, credentials, or `.pem` keys are committed
- [ ] `CHANGELOG.md` updated under `[Unreleased]`
- [ ] Database changes are expressed as Flyway migrations (`common/` or `tenant/`)

---

## How to Test

<!-- Steps for a reviewer to verify the change -->

## Notes / Screenshots (optional)
