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

| Workflow                                        | Trigger                       | Purpose                                              |
|-------------------------------------------------|-------------------------------|------------------------------------------------------|
| [`ci.yml`](../.github/workflows/ci.yml)         | PR, push to `develop`/`main`  | Build, test, verify module boundaries                |
| [`codeql.yml`](../.github/workflows/codeql.yml) | PR, push, weekly              | Static security analysis (CodeQL, Java)              |
| [`deploy.yml`](../.github/workflows/deploy.yml) | Push to `main`, manual        | Build → push to ECR → deploy to ECS Fargate          |

The deploy job authenticates to AWS with **OIDC** (keyless — no long-lived access keys), pushes the
image to ECR, renders a new task definition revision with that image, and rolls the ECS service
(`wait-for-service-stability`).

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

### GitHub repository configuration for `deploy.yml`

- **Variables:** `AWS_REGION`, `ECR_REPOSITORY`, `ECS_CLUSTER`, `ECS_SERVICE`, `ECS_TASK_DEFINITION`
  (path to `ecs/task-definition.json`), `CONTAINER_NAME`, `AWS_DEPLOY_ROLE_ARN`.
- **Secrets:** none required when using OIDC (the IAM role is referenced by ARN).

## Database

RDS PostgreSQL must have **pgvector** enabled. Flyway runs `db/migration/common` on startup; tenant
schemas are migrated by `ProvisioningService` / `TenantMigrationRunner`. In prod, Flyway `clean` is
disabled and structured (ECS/JSON) logging is enabled for CloudWatch.
