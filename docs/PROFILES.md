# Configuration profiles

The app uses **layered Spring profiles**: `application.yml` holds the shared base; each profile file
overrides only what differs. Exactly one of `dev` / `prod` is active at runtime; `test` is activated by
the test suite. Everything is bound to typed `@ConfigurationProperties` records under `reqsai.*`.

## How a profile is selected

```yaml
# application.yml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}   # default is dev
  config:
    import: optional:file:.env[.properties] # local secrets/overrides (git-ignored), optional
```

- **Local run** → `dev` (the default). Override with `SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun`.
- **Container / AWS** → set `SPRING_PROFILES_ACTIVE=prod` in the task definition.
- **Tests** → `test`, set by `@ActiveProfiles("test")` on the IT base; you never select it by hand.
- **AI (local or cloud)** → enable via your `.env` by setting the `SPRING_AI_MODEL_*` and
  provider key/URL vars — no extra profile needed. See [LOCAL_AI.md](LOCAL_AI.md).
- **Secrets/overrides** → a git-ignored `.env` at the repo root (loaded via `spring.config.import`),
  or real environment variables. Never commit secrets.

## At a glance

| Aspect         | `dev` (default)                           | `test` (auto, CI)                        | `prod`                                  |
|----------------|-------------------------------------------|------------------------------------------|-----------------------------------------|
| **When**       | local development (`bootRun`)             | `./gradlew test` / CI                    | deployed container (ECS Fargate)        |
| **Database**   | local Postgres `localhost:5432/reqsai`    | Testcontainers (`jdbc:tc:postgresql:16`) | RDS via `DB_HOST`/`DB_NAME` (required)  |
| **Flyway**     | `common` (+ tenant on provisioning)       | `common` only                            | `common`; **`clean` disabled**          |
| **JWT keys**   | `src/main/resources/certs` (git-ignored)  | committed throwaway test pair (`test/…`) | env / mounted secret (`JWT_*_KEY_PATH`) |
| **Mail**       | Mailpit `localhost:1025` (UI `:8025`)     | —                                        | real SMTP via env                       |
| **AI**         | off by default; enable via `.env` vars    | off (no AI beans)                        | Gemini (`GEMINI_API_KEY` from env)      |
| **Logging**    | DEBUG + SQL + bound params, pretty        | DEBUG (app only)                         | structured JSON (ECS), `root=WARN`      |
| **Swagger**    | on (`/swagger-ui.html`)                   | on                                       | **off**                                 |
| **`show-sql`** | `true`                                    | `false`                                  | `false`                                 |

> AI is **off by default** (`spring.ai.model.chat`/`embedding: none`) so devs not on `discovery` need no
> Ollama. Enable locally via `.env` — copy `.env.example` and uncomment the provider section you want.
> `prod` selects **Gemini**. See [LOCAL_AI.md](LOCAL_AI.md).

## `dev` — local development

Defaults assume the Docker Compose `core` profile (Postgres/pgvector + Mailpit) is up.

```bash
docker compose --profile core up -d     # Postgres + Mailpit
./scripts/generate-jwt-keys.sh          # first time only (keys are git-ignored)
./gradlew bootRun                        # http://localhost:8080  (profile: dev)
```

- Verbose logging (app DEBUG, Hibernate SQL + bound params) for fast feedback.
- Mail goes to Mailpit — no real email leaves your machine; read it at `http://localhost:8025`.
- **AI is off** by default. Enable any provider by setting the relevant vars in your `.env`
  (copy `.env.example`, uncomment the section) — see [LOCAL_AI.md](LOCAL_AI.md).
- **No `iam` yet?** Mint a real JWT to call secured endpoints: `GET /api/v1/auth/dev-token` (dev-only,
  signs with the dev key) → use the returned `Bearer` token. Real authorization, no auth bypass.
- pgvector `initialize-schema: true` (the dev image ships the extension).

## `test` — integration tests & CI

Lives on the **test classpath** (`src/test/resources/application-test.yml`) so it never ships in the
production jar. Activated automatically by `@ActiveProfiles("test")`.

- **Database**: Testcontainers JDBC URL `jdbc:tc:postgresql:16:///reqsai_test` spins up Postgres per
  run — no local DB needed, nothing to clean up.
- **JWT**: a committed **throwaway** keypair (`src/test/resources/certs`) so secured tests run on a
  clean checkout and in CI with zero setup (production keys stay git-ignored). See
  [CONTRIBUTING — Testing secured endpoints](../.github/CONTRIBUTING.md#testing-secured-endpoints).
- **AI**: providers inherit the base (`ollama`) but stay dormant — no test calls a live model; the
  pgvector store autoconfig is excluded so ITs run on plain `postgres:16`.
- Run: `./gradlew test` (or `build`, which also runs `verifyModularity`).

## `prod` — deployed container

Activated by `SPRING_PROFILES_ACTIVE=prod` in the ECS task definition. **All connection values come
from the environment — there are no localhost fallbacks**, so a missing `DB_HOST` fails fast.

- Structured **JSON logs** (`ecs` format) for CloudWatch; `root=WARN`, SQL logging off.
- **`flyway.clean-disabled: true`** — a hard guard against wiping production data.
- Swagger and `/api-docs` **disabled**.
- Response compression on.
- Required env: `DB_HOST`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_*_KEY_PATH` (or mounted keys),
  `GEMINI_API_KEY`, `CORS_ALLOWED_ORIGINS`, and `WS_BROKER_*` if using a STOMP relay. See
  [DEPLOYMENT.md](DEPLOYMENT.md).

## Local infrastructure (Docker Compose profiles)

`compose.yaml` groups local services under opt-in profiles, so you start only what you need:

| Profile | Services                      | Start                                               | For                                                      |
|---------|-------------------------------|-----------------------------------------------------|----------------------------------------------------------|
| `core`  | Postgres/pgvector, Mailpit    | `docker compose --profile core up -d`               | everyday dev (the default stack)                         |
| `app`   | the API itself (live rebuild) | `docker compose --profile core --profile app watch` | run the app in a container instead of `bootRun`          |
| `ai`    | Whisper (STT)                 | `docker compose --profile core --profile ai up -d`  | only when developing AI — see [LOCAL_AI.md](LOCAL_AI.md) |

- **Datasource auto-wiring**: the `db` container carries the
  `org.springframework.boot.service-connection=postgres` label, so Spring Boot's Docker Compose support
  wires the `dev` datasource automatically (no URL needed).
- **Mailpit**: SMTP on `1025`, inbox UI at `http://localhost:8025`.
- **`app` vs `bootRun`**: the default dev loop is `./gradlew bootRun` (DevTools hot reload); the `app`
  profile is the containerized alternative with `watch` rebuild.
- **`ai` is opt-in**: devs not working on AI omit it — the app's AI beans stay dormant and nothing breaks.

## Adding profile-specific config

Override only the keys that differ in `application-<profile>.yml`; everything else inherits from
`application.yml`. Keep every value env-overridable (`${VAR:default}`) so the same image runs in any
environment without rebuilds.
