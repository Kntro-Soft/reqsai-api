# Code-aware copilot

The copilot reads the client's connected code, so that during a meeting it knows what the product already does. A suggestion card can then say "already built" or "contradicts what is implemented", for example:

> "El código permite cancelar hasta 2 h antes; el cliente pide 24 h"

The card links to the module it means, and every suggestion keeps the verbatim fragment of the conversation it comes from.

## Pieces

| Piece | Where |
|---|---|
| Connected repositories and their module map | `codebase` module (`com.kntro.reqsai.codebase`) |
| Read API for other modules | `codebase::api` → `CodebaseModuleApi` |
| Prompt section and code findings | `discovery`: `GenerationContext.CodeContext`, `AbstractLlmGenerationAdapter` (EXISTING SYSTEM, CODE AWARENESS, EVIDENCE) |
| Evidence → transcript segment | `discovery`: `QuoteLocator` |
| GitHub App (install, tokens, webhooks) | `codebase`: `GitHubAppPort` → `GitHubAppAdapter`, `GitHubWebhookService` |
| Tables | public `code_host_installations`; tenant `code_repositories`, `code_modules` (pgvector 768); `suggestions.evidence_*`/`code_*`; `user_stories.origin_*`/`code_refs` |

## Indexing a repository

`POST /api/projects/{projectId}/code/repositories` (`INTEGRATION_WRITE`) with `{repository, branch?, installationId?}`.

1. **Check GitHub first.** The repository and its branch are checked against GitHub before anything is stored, so a typo or a repository ReqsAI cannot read is answered right away.
   - With `installationId` (picked from `GET /api/projects/{projectId}/code/github/repositories`) it is read through that installation of the organization's GitHub App, which must share it.
   - Without it, a typed repository is read through the organization's installation that shares it, if any; otherwise anonymously, which only works for a public repository.
2. **Run in the background** (`CodeIndexLauncher`, after the commit, bound to the tenant). The run then:
   1. Downloads the zipball of the branch's head into a temporary file, capped at 150 MB.
   2. Keeps source files, manifests and READMEs (`SourceFilter`). It drops `node_modules`, build output, lockfiles, binaries, `.env`, keys and other secret files, and skips files over 256 KB.
   3. Removes credentials that slipped into code (`SecretRedactor`): private keys, cloud, payment and GitHub tokens, JWTs, URL passwords, and values assigned to `password`, `apiKey`, `token`, …
   4. Detects the stack (`StackDetector`): languages, frameworks, databases and platforms.
   5. Groups the sources into modules, one per folder (`ModuleGrouper`). Small folders fold into their parent, with at most 80 modules per repository.
   6. Describes every module.
      - Endpoints, routes and entities come from the code (`SymbolExtractor`).
      - With an AI model, the name, summary, capabilities and **implemented business rules** come from the model (`LlmCodeSummaryAdapter`), four modules at a time.
      - Each module also gets an embedding.
      - Without a model, modules get a structural description (`summarized: false`).
   7. Writes a short overview of the product, from the README and the modules.
3. **Reindex** with `POST …/{id}/reindex`, or automatically on every push for a repository read through the GitHub App. It downloads the branch again, but only modules whose files changed are described again (`content_hash`). A push that arrives during a run is kept (`pending_commit`) and indexed as soon as the run ends.
4. **Stalled runs.** A run silent for 20 minutes (for example, the server restarted) reads as failed and can be retried.

## Connecting GitHub (GitHub App)

Like Jira, GitHub is connected once per organization, by an owner or admin, from **Settings → Integrations**. It uses ReqsAI's GitHub App, not a personal token:

| | GitHub App | Personal token or OAuth App |
|---|---|---|
| What ReqsAI can read | Only the repositories the account picks, read-only | Every repository the person can read (`repo` scope also allows writing) |
| Who it belongs to | The GitHub account (organization) | One person; it breaks when they leave |
| Credentials kept by ReqsAI | None: tokens are minted per use, valid 1 hour | The token |
| Rate limit | 5,000 calls per hour per installation | 5,000 per person; 60 per hour per IP without a token |
| Updates | GitHub sends a webhook on every push | Manual reindex only |

### Flow

1. `POST /api/organizations/{orgId}/code/github/install` returns the GitHub install URL with a signed `state` (organization + user, 30 minutes).
2. On GitHub the user installs the App on their account or organization and picks the repositories.
3. GitHub redirects to the web callback (`/settings/integrations/github/callback`) with `installation_id`, `setup_action`, `state` and an OAuth `code`.
4. The web posts them to `POST /api/organizations/{orgId}/code/github/installations`. The API checks two things before linking the installation to the organization (public table `code_host_installations`):
   - the `state` was issued here for this organization and user;
   - the `code`, exchanged for a user token that is dropped right after, shows the GitHub user can access that installation (`GET /user/installations`). An installation id typed into the URL is refused.
5. `setup_action=request` (a GitHub organization member asked an owner to approve) answers `REQUESTED`. `update` (the account changed its repositories) refreshes an installation already linked.
6. `DELETE …/installations/{installationId}` unlinks it. Its repositories keep their module map but stop updating. Uninstalling the App is done on GitHub.

The project picks repositories with `GET /api/projects/{projectId}/code/github` (connection) and `GET …/code/github/repositories` (what the installations share).

### Tokens

`GitHubAppAdapter` signs a JWT (RS256, 9 minutes, `iss` = client id) with the App's private key and exchanges it for an installation token (`POST /app/installations/{id}/access_tokens`). That token is cached in memory until 5 minutes before it expires, never stored and never logged. An installation is used only while it is linked to the organization of the current tenant and not suspended (`RepositoryAccess`), so one organization never reads through another's installation.

### Webhooks

`POST /api/code/webhooks/github` is public and authenticated by the `X-Hub-Signature-256` HMAC of the raw body. `GitHubWebhookService` finds the organizations the installation serves and works in each one's schema:

| Event | Effect |
|---|---|
| `push` to the tracked branch | Reindex at the new commit (queued if a run is in progress) |
| `installation_repositories` removed | Those repositories stop updating, with the reason |
| `installation` deleted | The link is removed; its repositories stop updating |
| `installation` suspend / unsuspend | Paused / resumed |

### Setting up the App (once, by Kntro-Soft)

Create it at github.com → organization **Kntro-Soft** → Settings → Developer settings → GitHub Apps → New GitHub App:

| Field | Value |
|---|---|
| GitHub App name | `ReqsAI` (its slug goes to `CODEBASE_GITHUB_APP_SLUG`) |
| Homepage URL | `https://reqsai.tech` |
| Callback URL | `https://reqsai.tech/settings/integrations/github/callback` |
| Request user authorization (OAuth) during installation | **On** (GitHub then sends the `code` the API verifies; the callback URL also receives the install redirect) |
| Webhook | Active; URL `https://reqsai.tech/api/code/webhooks/github`; content type `application/json`; a random secret |
| Repository permissions | **Contents: Read-only**, **Metadata: Read-only** (nothing else) |
| Subscribe to events | **Push** (installation events are always sent) |
| Where can this GitHub App be installed? | **Any account** |

Then generate a client secret and a private key (`.pem`) and put them, with the client id and the webhook secret, in the vault like the Jira credentials.

## What the copilot does with it

Each realtime pass (and each requirement typed in the assistant chat) adds an **EXISTING SYSTEM** section to the prompt:
- the overview;
- the 4 modules nearest to the conversation, keyed `C1…C4`, found by embedding with a 0.20 similarity floor, or by shared words when there is no embedding.

The model answers per story:
- `codeFinding`: `ALREADY_EXISTS` or `CONFLICTS_WITH_CODE`;
- `codeNote`;
- `codeRefs`: the keys of the modules it relates to.

Every story and question also carries `evidence`, a verbatim fragment of the conversation. The adapter then post-processes the answer:
- Keys are resolved to the modules that were shown; unknown keys are dropped.
- A finding is ignored when the prompt had no code.
- `QuoteLocator` places the quote on the transcript segment where it starts. A live transcriber cuts a sentence into short segments, so the quote is looked for across up to 4 consecutive ones, first verbatim and then by shared words (at least 60% of the quote's words).

The suggestion stores both, the live `SUGGESTION_GENERATED` message carries them, and an accepted story keeps them as `origin` and `codeReferences`. The code never decides the type of a suggestion: the backlog rules still choose NEW_STORY, UPDATE_STORY or EDGE_CASE.

## Privacy and security

- **No raw code is stored.** Code is read once while indexing; only summaries, rules, symbol names and the profile are kept.
- **Secrets never reach the model.** Secret files are never read, and secrets inside code are removed before the model sees anything.
- **Code is treated as data.** It goes to the model inside `<code>` tags, with look-alike tags neutralized, and the prompt tells the model to treat it as data.
- **No credential is stored.** Private repositories are read through the GitHub App with installation tokens minted per use (1 hour), and the App only has read-only Contents and Metadata permissions on the repositories the client picked.
- **Webhooks are verified.** Unsigned or wrongly signed deliveries are refused with 401.
- **Disconnecting a repository forgets everything.** It deletes the repository and every module.

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `CODEBASE_GITHUB_API_URL` | `https://api.github.com` | GitHub REST API (tests and E2E point it at a local fake) |
| `CODEBASE_GITHUB_WEB_URL` | `https://github.com` | GitHub web (install page, OAuth code exchange) |
| `CODEBASE_GITHUB_APP_SLUG` | — | The App's slug (`github.com/apps/<slug>`) |
| `CODEBASE_GITHUB_APP_CLIENT_ID` | — | The App's client id (`Iv…`), also the JWT issuer |
| `CODEBASE_GITHUB_APP_CLIENT_SECRET` | — | The App's client secret (OAuth code exchange; key of the install `state`) |
| `CODEBASE_GITHUB_APP_PRIVATE_KEY` | — | The App's private key: the PEM, the PEM with `\n` escapes, or the PEM in base64 |
| `CODEBASE_GITHUB_APP_WEBHOOK_SECRET` | — | The webhook secret |
| `CODEBASE_MAX_REPOSITORIES` | `3` | Repositories per project |
| `CODEBASE_MAX_MODULES` | `80` | Modules described per repository |
| `CODEBASE_SUMMARY_LANGUAGE` | `Spanish` | Language of module summaries |

The summaries use the same model as requirement generation (`GENERATION_PROVIDER`), and their tokens are metered against the organization's plan.

The GitHub App is available only when all five `CODEBASE_GITHUB_APP_*` values are set. Without it, only public repositories can be connected, read anonymously: GitHub limits those calls to 60 per hour per server IP, and indexing takes 3 (metadata, branch head and zipball). Through the App, each installation has 5,000 calls per hour.
