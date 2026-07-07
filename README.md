<div align="center">

# ReqsAI — Backend API

![Java](https://img.shields.io/badge/Java-25-f89820?logo=openjdk&logoColor=white&labelColor=1f2937)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4-6db33f?logo=springboot&logoColor=white&labelColor=1f2937)
![Spring Modulith](https://img.shields.io/badge/Spring_Modulith-2.0-16a34a?logo=spring&logoColor=white&labelColor=1f2937)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-336791?logo=postgresql&logoColor=white&labelColor=1f2937)
![Architecture](https://img.shields.io/badge/architecture-schema--per--tenant-38bdf8?labelColor=0f172a)
![Status](https://img.shields.io/badge/status-in%20development-facc15?labelColor=713f12)

</div>

Backend de **Reqs-AI** — plataforma SaaS B2B de elicitación de requisitos asistida por IA.

**Stack:** Java 25 · Spring Boot 4 · Spring Modulith · Spring AI (Gemini + pgvector) · PostgreSQL
**Arquitectura:** DDD + CQRS + Hexagonal · monolito modular · **multitenancy schema-per-tenant**

> Decisiones de arquitectura (el *por qué*) en **[`docs/adr/`](./docs/adr/)** · convenciones, capas y
> flujo de trabajo en **[`.github/CONTRIBUTING.md`](./.github/CONTRIBUTING.md)**.

---

## Requisitos

- JDK 25 (el wrapper de Gradle usa el toolchain configurado)
- Docker + Docker Compose (PostgreSQL/pgvector de desarrollo)
- `openssl` (para generar las claves JWT de desarrollo)

## Puesta en marcha (dev)

```bash
# 1. (Opcional) Variables locales. Todo tiene defaults, así que el .env es opcional.
cp .env.example .env        # edita lo que quieras sobreescribir (ej. GEMINI_API_KEY)

# 2. Generar el par de claves RSA de desarrollo para firmar JWT (solo la primera vez).
#    Las claves NO se commitean (.gitignore). En prod se montan como secretos.
./scripts/generate-jwt-keys.sh

# 3. Levantar la infra local (PostgreSQL/pgvector + Mailpit) — perfil `core`.
docker compose --profile core up -d
#    Mailpit (correo de dev): bandeja en http://localhost:8025  ·  SMTP en 1025
#    Opcional (solo si desarrollas IA): añade el perfil `ai` para STT (Whisper) → ver docs/LOCAL_AI.md
#    docker compose --profile core --profile ai up -d

# 4. Ejecutar.
./gradlew bootRun           # DevTools recarga en caliente al recompilar

# Alternativa: correr la app en contenedor con rebuild automático al cambiar el código.
# docker compose --profile core --profile app watch
```

Verificación:

- API health → http://localhost:8080/actuator/health
- Swagger UI → http://localhost:8080/swagger-ui.html
- Diagramas de módulos → `build/spring-modulith-docs/` (tras `./gradlew test`)

## Build y verificación

```bash
./gradlew build            # compila + tests + verifyModularity (límites de módulos)
./gradlew verifyModularity # solo la verificación de arquitectura Spring Modulith
```

## Code Quality

| Command                                                        | What it does                                                                                          |
|----------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| `./gradlew spotlessCheck`                                      | Verify all Java/Kotlin files match the formatter (CI-enforced)                                        |
| `./gradlew spotlessApply`                                      | Auto-format all Java/Kotlin files locally                                                             |
| `./gradlew jacocoTestReport`                                   | Generate coverage report → `build/reports/jacoco/test/index.html`                                     |
| `./gradlew jacocoTestCoverageVerification`                     | Fail if coverage < 50% (also runs as part of `check`)                                                 |
| `export NVD_API_KEY=your_key ./gradlew dependencyCheckAnalyze` | Scan dependencies for CVEs (slow — downloads NVD on first run). The key is optional for higher speed. |
| `./gradlew test --tests "*.ArchitectureTests"`                 | Run ArchUnit architecture fitness functions only                                                      |

> **Formatter:** Eclipse formatter (chosen for JDK 25 compatibility — Palantir/Google Java Format
> use javac internals removed in JDK 23). Run `spotlessApply` before your first commit on this branch.
>
> **Coverage:** Codecov badge and report are updated automatically on every push to `develop`/`main`.
> Threshold starts at 50% and will be raised as feature modules are implemented.
>
> **OWASP:** Runs weekly via `owasp.yml` — not part of the PR pipeline. Check the
> [Actions tab](../../actions/workflows/owasp.yml) for the latest report.

---

## Estructura (bounded contexts)

```
com.kntro.reqsai
├── shared/      (OPEN) Kernel común: agregado base (UUID v7 + auditoría + soft-delete),
│                       excepciones DDD, multitenancy, seguridad JWT, web, OpenAPI
├── iam/         Identity & Access Management
├── billing/     Suscripciones y planes
├── workspace/   Organizaciones y proyectos
├── discovery/   Sesiones de captura, US, pipeline IA
└── gateway/     Integración con Jira
```

Cada bounded context se desarrolla en sus capas hexagonales (`api`, `domain`, `application`,
`infrastructure`, `interfaces`) y solo puede depender del módulo `shared`. Los límites se
verifican automáticamente en cada build (`ModularityTests`).

## Multitenancy (schema-per-tenant)

Un schema PostgreSQL por organización (`tenant_<slug>`). El claim `orgId` del JWT se resuelve a un
schema y Hibernate enruta la conexión con `SET search_path`. El registro global de organizaciones
vive en `public.organizations`. Al activar una organización, `ProvisioningService` crea el schema y
corre las migraciones de `db/migration/tenant` (ver [ADR-0003](./docs/adr/0003-schema-per-tenant-multitenancy.md)).

## Migraciones Flyway

```
src/main/resources/db/migration/
├── common/   Tablas globales en public (Flyway al arrancar): event_publication, organizations
└── tenant/   Tablas por-tenant (ProvisioningService por cada schema): V1 baseline + tablas de cada BC
```

## Seguridad

Spring Security **stateless** con **JWT firmado por RSA (RS256)**. La **verificación** del token es
cross-cutting (puerto `TokenVerifier` + adaptador `JjwtTokenVerifier`, solo clave pública, en
`shared`); la **emisión** (login/refresh, clave privada) es de `iam`. Claves de dev: `scripts/generate-jwt-keys.sh`;
en prod, secretos montados (ver `.github/workflows/deploy.yml`). Endpoints públicos: `/api/auth/**`,
Swagger, `/actuator/health`, `/ws/**`. Ver [ADR-0005](./docs/adr/0005-rsa-jwt-authentication.md).

## Versionado de API

Las rutas tienen la forma `/api/<recurso>` (ej. `/api/organizations`). La versión se negocia mediante
el header **`Api-Version: 1`** (no en el path). El valor por defecto es `V1`. Spring Framework 7 resuelve
el endpoint correcto de forma nativa sin ningún middleware adicional. El header es obligatorio en todos
los endpoints de negocio; los endpoints públicos (Swagger, actuator, `/ws/**`) no lo requieren.

## Patrones transversales (shared)

- **CQRS + Hexagonal**: commands mutan agregados y registran eventos de dominio; queries leen.
  Repos = puerto en `domain`/`application` + adaptador JPA en `infrastructure`.
- **Errores**: `ErrorCatalog` (códigos genéricos en `CommonError`; cada BC define los suyos) +
  `GlobalExceptionHandler` que responde **RFC 9457 `ProblemDetail`** con `code`/`correlationId`.
- **Paginación**: `PageResponse` (envoltura estable) · `PageCriteria` · `PageRequestFactory`
  (clamp de tamaño con `PaginationProperties`) · `SortPolicy` (whitelist + tie-breaker `id`) ·
  `Specifications` (filtros funcionales null-safe).
- **Tiempo real**: STOMP sobre WebSocket (`/ws`), CONNECT autenticado con el mismo `TokenVerifier`;
  los BCs publican vía el puerto `RealtimeNotifier`. Broker **conmutable** (`reqsai.websocket.broker.mode`):
  `SIMPLE` (dev/1 instancia) o `RELAY` a un broker externo (Amazon MQ) para multi-instancia — sin tocar
  código. Ver [ADR-0007](./docs/adr/0007-realtime-stomp-switchable-broker.md).
- **IA**: Spring AI (Gemini + pgvector) viene **excluido** hasta que `discovery` lo cablee con una
  API key real, para que la app arranque sin credenciales de IA.
- **Eventos entre módulos**: `@ApplicationModuleListener` (outbox de Spring Modulith).

## CI/CD

- **CI** (`.github/workflows/ci.yml`): build + tests + verificación de módulos en cada PR/push.
- **CodeQL** (`.github/workflows/codeql.yml`): análisis de seguridad estático (Java).
- **Deploy** (`.github/workflows/deploy.yml`): imagen Docker → ECR → ECS Fargate (AWS) en push a `main`.

Detalle en [`docs/DEPLOYMENT.md`](./docs/DEPLOYMENT.md).

## Documentación y governance

| Documento                                                                                                            | Propósito                                            |
|----------------------------------------------------------------------------------------------------------------------|------------------------------------------------------|
| [`docs/adr/`](./docs/adr/)                                                                                           | Architecture Decision Records (el *por qué*)         |
| [`docs/PROFILES.md`](./docs/PROFILES.md)                                                                             | Perfiles `dev` / `test` / `prod`: qué, cuándo y cómo |
| [`docs/REALTIME.md`](./docs/REALTIME.md)                                                                             | WebSocket/STOMP: cómo emitir y consumir tiempo real  |
| [`docs/LOCAL_AI.md`](./docs/LOCAL_AI.md)                                                                             | IA local↔nube (LLM, embeddings, STT) — Mac/Win/Linux |
| [`docs/DEPLOYMENT.md`](./docs/DEPLOYMENT.md)                                                                         | Despliegue (Docker, AWS ECS Fargate, CI/CD)          |
| [`docs/MIGRATIONS.md`](./docs/MIGRATIONS.md)                                                                         | Cómo crear migraciones Flyway (`scripts/new-migration.sh`) |
| [`.github/CONTRIBUTING.md`](./.github/CONTRIBUTING.md)                                                               | Flujo de trabajo, build, tests, ramas, commits       |
| [`CHANGELOG.md`](./CHANGELOG.md)                                                                                     | Historial de cambios (Keep a Changelog)              |
| [`AUTHORS.md`](./AUTHORS.md) · [`CONTRIBUTORS.md`](./CONTRIBUTORS.md) · [`ACKNOWLEDGMENTS.md`](./ACKNOWLEDGMENTS.md) | Equipo y créditos                                    |
| [`.github/SECURITY.md`](./.github/SECURITY.md) · [`SUPPORT.md`](./SUPPORT.md)                                        | Seguridad y soporte                                  |

## Licencia

[Apache 2.0](./LICENSE).
