# Security Policy — Kntro-Soft / reqsai-api

## Supported Versions

| Version             | Supported |
|---------------------|-----------|
| `main` / `develop`  | Yes       |
| Older tags          | No        |

## Reporting a Vulnerability

If you discover a security vulnerability or find a secret accidentally committed (credentials, API
keys, private keys, tokens), **do not open a public Issue or Pull Request**.

### How to Report

Email **Jhosepmyr Gutiérrez Soto** — `jhosepmyrgutierrezsoto@gmail.com` with:

- A description of the issue and its impact
- Steps to reproduce (or the file/commit where the secret is exposed)
- Any relevant logs or evidence

We commit to acknowledging within **72 hours** and addressing the issue within **7 business days**.
If a secret was exposed, it must be **rotated immediately** and purged from history.

## Security Practices in This Repository

- **No secrets in the repo.** Credentials, API keys and tokens are provided via environment
  variables / a git-ignored `.env`, never committed.
- **JWT keys** are never committed. Dev keys are generated locally with
  `scripts/generate-jwt-keys.sh` (git-ignored under `src/main/resources/certs/`); production keys are
  mounted as secrets (see [`deploy.yml`](./workflows/deploy.yml)).
- **JWT** uses RS256 (asymmetric); only the public key is needed to verify tokens.
- **Multitenancy isolation**: every database connection resets `search_path` on release; the
  tenant resolver fails closed to `public` on any lookup error.
- **Dependencies** are monitored via Dependabot ([`dependabot.yml`](./dependabot.yml)) and the
  CodeQL workflow.
- Production disables Swagger UI, OpenAPI docs, and Flyway `clean`.
