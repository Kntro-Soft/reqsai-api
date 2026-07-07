# 0006. Deploy on AWS ECS Fargate

- Status: Accepted
- Date: 2026-06-08
- Deciders: Kntro-Soft team

## Context

The backend is a single containerized Spring Boot application that needs a managed runtime, a
managed PostgreSQL (with pgvector), and a secure secrets story. AWS is the chosen cloud. Options
considered: AWS App Runner (simplest, fully managed), ECS Fargate (serverless containers with full
control), Elastic Beanstalk (classic PaaS), and EKS (Kubernetes, heaviest).

## Decision

Deploy on **AWS ECS Fargate** with the image in **ECR** and the database on **RDS for PostgreSQL**
(pgvector enabled). Secrets live in **AWS Secrets Manager** and are injected into the task. CI/CD
uses GitHub Actions authenticating via **OIDC** (no long-lived AWS keys): `deploy.yml` builds and
pushes the image, renders a new task-definition revision, and rolls the service waiting for
stability. The task definition is version-controlled at `ecs/task-definition.json`.

## Consequences

- Serverless containers: no EC2 hosts to patch, pay per task, autoscaling available.
- Full control over networking (VPC, ALB, security groups) — more setup than App Runner, but
  standard and portable.
- OIDC removes static cloud credentials from GitHub.
- The task definition and ALB/RDS/Secrets must be provisioned (ideally via IaC — Terraform/CDK —
  as a follow-up); not yet codified in this repo.
- Structured (ECS/JSON) logs in prod integrate cleanly with CloudWatch.
