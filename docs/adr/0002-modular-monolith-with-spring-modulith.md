# 0002. Modular monolith with Spring Modulith

- Status: Accepted
- Date: 2026-06-08
- Deciders: Kntro-Soft team

## Context

Reqs-AI has five bounded contexts (iam, billing, workspace, discovery, gateway). A microservices
deployment would add operational complexity (networking, distributed transactions, multiple
pipelines) that a 5-person academic team and an MVP do not need. However, we still want strong module
boundaries so the contexts stay decoupled and could be extracted later.

## Decision

Build a **modular monolith** using **Spring Modulith**. Each bounded context is a top-level package
(module) under `com.kntro.reqsai`, developed in hexagonal layers (`api`, `domain`, `application`,
`infrastructure`, `interfaces`). The `shared` module is OPEN (Shared Kernel); every other module is
CLOSED and may depend only on `shared`. Boundaries are enforced at build time by `ModularityTests`
(`ApplicationModules.verify()`), wired into a dedicated `verifyModularity` Gradle task. Cross-module
communication uses published interfaces in `api` and Spring Modulith application events.

## Consequences

- A single deployable artifact and pipeline; simple local development and transactions.
- Module boundaries are verified automatically; illegal coupling fails the build.
- Modulith generates module documentation (PlantUML) from the code.
- If a context ever needs independent scaling, its clear boundary eases extraction into a service.
- Everything shares one JVM/database; a noisy module can affect others (acceptable for the MVP).
