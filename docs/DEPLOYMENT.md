# Deployment — Reqs-AI API

Target platform: **AWS** — container on **ECS Fargate**, image in **ECR**, database on **RDS for
PostgreSQL** (with the `pgvector` extension), secrets in **AWS Secrets Manager**.

## Container image

A multi-stage [`Dockerfile`](../Dockerfile) builds the executable jar (JDK 25) and runs it on a JRE
base as a non-root user:

```bash
docker build -t reqsai-api:local .
docker run --rm -p 8080:8080 -e SPRING_PROFILES_ACTIVE=prod reqsai-api:local
```

## Pipelines (GitHub Actions)

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| [`ci.yml`](../.github/workflows/ci.yml) | PR, push to `develop`/`main`/`release/**`/`hotfix/**`, called by `release.yml` | Build, test, lint |
| [`codeql.yml`](../.github/workflows/codeql.yml) | PR, push (same branches), weekly | Static security analysis (CodeQL, Java) |
| [`release.yml`](../.github/workflows/release.yml) | Push to `release/**` / `hotfix/**` | CI → image built once as candidate `X.Y.Z-rc.N` (pre-release with digest and tree hash) → automatic verification → PR `release: X.Y.Z` to `main` |
| [`produccion.yml`](../.github/workflows/produccion.yml) | Push to `main` | Candidate with the same tree → approval in `produccion` → same digest deployed through `reqsai-infra` → `X.Y.Z`/`latest` labels, tag `vX.Y.Z`, back-merge PR |
| [`rollback.yml`](../.github/workflows/rollback.yml) | Manual (`version`) | Ship the digest of an earlier final release again |

The MVP runs on a single EC2 host managed by
[`reqsai-infra`](https://github.com/Kntro-Soft/reqsai-infra) (Docker Compose + Caddy). There is no second host for
a staging environment, so each candidate is verified on the runner with the same digest that later goes to
production; `reqsai-infra` reaches the host with GitHub OIDC + SSM and backs up the database before each deploy.
Each step is switched on by an organization variable (`ENABLE_REQSAI_API_IMAGE`, `ENABLE_REQSAI_API_DEPLOY`,
`ENABLE_REQSAI_INFRA_DEPLOY`). The release process, approvals and switches are in
[CONTRIBUTING.md](../.github/CONTRIBUTING.md#releases-and-deployment).

The sections below describe the ECS Fargate target (`envs/production` in `reqsai-infra`), which is not the
one serving the MVP.

## AWS resources

| Resource              | Purpose                                                        |
|-----------------------|----------------------------------------------------------------|
| **ECR** repository    | Stores the container image                                     |
| **ECS cluster**       | Fargate cluster running the service                            |
| **ECS service + task**| Runs the container; task definition committed as `ecs/task-definition.json` |
| **ALB**               | HTTP(S) ingress + health checks on `/actuator/health`          |
| **RDS PostgreSQL**    | Database; `pgvector` enabled (`CREATE EXTENSION vector`)        |
| **Secrets Manager**   | DB password, Gemini key, mail password, JWT keys               |
| **IAM role (OIDC)**   | Assumed by GitHub Actions to push/deploy                       |

## Configuration (prod profile)

Plain environment variables on the task definition:

| Variable                 | Value                                |
|--------------------------|--------------------------------------|
| `SPRING_PROFILES_ACTIVE` | `prod`                               |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` | RDS connection          |
| `JWT_ISSUER`             | JWT issuer claim                     |
| `JWT_PRIVATE_KEY_PATH` / `JWT_PUBLIC_KEY_PATH` | filesystem paths to the mounted keys |

Injected from Secrets Manager via the task definition `secrets` block:

| Secret (Secrets Manager) | Container env / file               |
|--------------------------|------------------------------------|
| `reqsai/db-password`     | `DB_PASSWORD`                      |
| `reqsai/gemini-api-key`  | `GEMINI_API_KEY`                  |
| `reqsai/mail-password`   | `MAIL_PASSWORD`                   |
| `reqsai/jwt-private-key` | RSA private key PEM                |
| `reqsai/jwt-public-key`  | RSA public key PEM                |

> **JWT keys on ECS.** The app loads keys from `JWT_*_KEY_PATH` (classpath or filesystem). On
> Fargate, inject the two PEM secrets and write them to a path (e.g. via the container entrypoint or
> a sidecar) that `JWT_PRIVATE_KEY_PATH` / `JWT_PUBLIC_KEY_PATH` point to. Never bake keys into the
> image.

### GitHub repository configuration (ECS target)

- **Variables:** `AWS_REGION`, `ECR_REPOSITORY`, `ECS_CLUSTER`, `ECS_SERVICE`, `ECS_TASK_DEFINITION`
  (path to `ecs/task-definition.json`), `CONTAINER_NAME`, `AWS_DEPLOY_ROLE_ARN`. No workflow uses them
  while the MVP runs on EC2.

## Database

RDS PostgreSQL must have **pgvector** enabled. Flyway runs `db/migration/common` on startup; tenant
schemas are migrated by `ProvisioningService` / `TenantMigrationRunner`. In prod, Flyway `clean` is
disabled and structured (ECS/JSON) logging is enabled for CloudWatch.
