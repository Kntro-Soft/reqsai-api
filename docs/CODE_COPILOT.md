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

## Creating a project with its sources

`POST /api/organizations/{orgId}/projects` takes optional `sources` (`jira`, `github[]`) so a new project is wired to Jira and GitHub from the start. The project is created and returned at once; the rest runs in the background.

1. **Check first.** Before the project exists, `ProjectSetupParticipant.validate` of each source module refuses what cannot work: no Jira connection (404), a repository GitHub cannot confirm or ReqsAI cannot read (404), more repositories than the limit or a repeated one (422/409).
2. **Announce.** `workspace` records one row per requested step in `project_setup_steps` (`JIRA_LINK`, `JIRA_IMPORT`, `GITHUB_REPOS`, with the request, to retry it) and publishes `ProjectSetupRequestedIntegrationEvent` on `workspace::api`. It is delivered after the commit, asynchronously, with the tenant of the request.
3. **Run.** `gateway` saves the Jira target and starts the import job; `codebase` connects each repository (limit and GitHub checks included) and starts indexing. The permissions are checked again for the creator. One repository that fails does not stop the others, and nothing a source does can undo the project.
4. **Follow.** `GET /api/projects/{projectId}/setup` merges the recorded state with what the modules keep (repositories' `status`/`modulesDone`/`moduleCount`, the Jira target, the latest import job). A skip or recorded failure wins over live data; a step a module has not reported as handled never reads as finished. The same object is pushed on `/topic/projects/{projectId}/setup` (spaced at least a second apart) and the web also polls while a step is open. When repositories are ready, `detectedStack` carries the merged languages, frameworks, databases and platforms; the web offers to apply them to the project's profile and never does it by itself.
5. **Skip and retry.** `POST …/setup/steps/{step}/skip` stops following a step (work already started keeps running). `POST …/retry` runs a failed step again: retrying the Jira link retries the import that waited on it, and retrying the repositories connects the missing ones and indexes the failed ones again.

Failure details are error codes (`CODE_REPOSITORY_NOT_FOUND`, per repository as `owner/name: CODE`) so the web translates them; an import that finished with failed issues reports `PARTIAL:<n>`.

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

## Deep search of the pure code

The module index is the fast path: it is a summary of the code, so it can miss a detail, and it can be stale or wrong. For anything the index does not settle, the copilot reads the **pure source code** in GitHub instead of the stored summaries.

| When | What happens |
|---|---|
| The analyst taps **Verificar en el código** on a suggestion | `POST /api/projects/{projectId}/suggestions/{suggestionId}/verify-code` |
| A chat question asks about the code and the index does not answer it | The model sets `searchCode`; the chat searches the code within the request |
| A live suggestion or a final story | The quick path only (the index), as before |

### Retrieve, then read

1. **Fetch.** `DeepCodeSearchService` downloads the repository archive at the **indexed commit** through the same access the indexer uses (an installation token of the GitHub App, or anonymous for a public repository). The file lives in `ArchiveCache` for 10 minutes after its last use, keyed by organization, repository and commit; the cache holds at most 4 files and 400 MB, deletes a file once its last reader is done and deletes everything on shutdown. A caller that runs out of time stops waiting; the download still lands in the cache for the next request.
2. **Search.** Source files are read with the indexer's filter and limits (`SourceFilter`, 4000 files, 256 KB each, 64 MB of text), credentials are removed with `SecretRedactor.redactPreservingLines` (line numbers stay right), and `CodeSearcher` scores windows of 24 lines BM25-like. Words and identifiers meet on a six-letter stem, so "cancelar una reserva" finds `cancelReservation` and `CANCELLATION_LIMIT_HOURS`. The path counts, identifiers and quoted terms weigh double, and files of the modules the index points at score 1.5x. The best passages come back tightened around their hits, merged, with at most 10 snippets and 12000 characters.
3. **Read.** `CodeVerificationService` makes **one** model call (`CodeVerificationPort`, `LlmCodeVerificationAdapter`) with the request and the numbered passages. If the search finds nothing the verdict is `NOT_FOUND` and the model is not called. The whole verification has a 25 second budget (`discovery.code-verification.budget`); the search gets the first part and the model call at least the last 8 seconds.

The model answers `{"verdict", "note", "references": [{"snippet", "lineStart", "lineEnd", "quote"}]}`:

| Verdict | Meaning |
|---|---|
| `ALREADY_EXISTS` | The passages show the code already provides it, as asked |
| `CONFLICTS_WITH_CODE` | An implemented rule, limit or value differs for the same subject |
| `NOT_FOUND` | Nothing in the code relates |
| `UNSURE` | Related code, not enough to tell |

`CodeVerificationParser` checks every citation against the passages the model saw: an unknown key is dropped, lines are clamped into the passage, a quote that is not in the passage is dropped, and the link is built here, never taken from the model. An `ALREADY_EXISTS` or `CONFLICTS_WITH_CODE` claim without a valid citation becomes `UNSURE`.

### References

`CodeReference` now has optional `file`, `lineStart`, `lineEnd` and `quote`; its `url` is `https://github.com/<owner>/<name>/blob/<commitSha>/<path>#L<a>-L<b>` (`CodeLinks.blobUrl`). References stored before deep search have none of them and still read as module references.

### Verifying a suggestion

`POST …/verify-code` needs `SESSION_DECIDE` and `INTEGRATION_READ`. It answers **202** with the suggestion in `code.verifyState = VERIFYING` and runs in the background (`SuggestionCodeVerificationJob`, bound to the tenant, after the commit). **409** `CODE_REPOSITORY_NOT_READY` when no repository is indexed, `SUGGESTION_ALREADY_RESOLVED` when it was decided.

When it ends, the suggestion stores `code_verdict`, `code_verified_at`, the finding, note and references (replacing the quick ones) and `code_verify_state = VERIFIED`. A failure (GitHub down, model down, timeout) stores `FAILED` and a `code.verifyError` instead of throwing; asking again retries. A verification silent for 2 minutes (a restart) can be started again.

Every state change raises `SuggestionCodeVerifiedEvent`, broadcast as `SUGGESTION_CODE_UPDATED` on `/topic/sessions/{sessionId}` and on `/topic/projects/{projectId}/suggestions` (the only topic for chat suggestions, which have no session). It carries the whole `code` object of the card.

### In the chat

The chat prompt asks the model to set `searchCode` when the message asks what the code already does and the EXISTING SYSTEM section does not clearly answer it. The handler then runs the same verification (kind `QUESTION`) within the request; the reply is the answer found in the code plus a line saying the source code was searched, and the message stores `code_refs` and `code_searched` (`codeReferences`, `codeSearched` in the response). Requests that do not need it pay nothing. If there is no ready repository, no model, or the search fails or runs out of time, the chat keeps the model's own short reply.

### Final stories and uploaded recordings

`StartDiscoveryProcessingCommandHandler` builds the same context a live pass gets (`RealtimeSuggestionService.contextFor`: backlog candidates, glossary, constraints, related code modules) from the first 6000 characters of the transcript, and fails open to the plain transcript when it cannot.

### Privacy

- No source code is persisted: the archive is a temporary file for a few minutes and the snippets live for the request. The verdict stores a short quote (at most 200 characters) per reference.
- Snippets are redacted before they reach the model, and go inside `<code>` tags with look-alike tags neutralized.
- Tokens are metered through `TokenUsageRecorderPort`; each deep search logs one line (`Deep code search for project …`) and each verification one more (`Deep verification for project …`).

GitHub's own code search API could replace the download for very large repositories, but it is rate limited per account, covers only the default branch and cannot be pinned to the indexed commit, so it is only a possible later optimisation.

| Variable | Default | Meaning |
|---|---|---|
| `reqsai.codebase.deep-search.cache-ttl` | `PT10M` | How long an archive stays after its last use |
| `reqsai.codebase.deep-search.cache-max-bytes` | `419430400` | Byte budget of the archive cache |
| `reqsai.codebase.deep-search.cache-max-entries` | `4` | Archives kept |
| `reqsai.codebase.deep-search.max-snippets` | `10` | Snippets given to the model |
| `reqsai.codebase.deep-search.max-snippet-chars` | `12000` | Characters of code given to the model |
| `discovery.code-verification.budget` | `PT25S` | Time for one verification |

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
