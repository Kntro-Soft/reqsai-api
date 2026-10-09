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
| Tables | tenant `code_repositories`, `code_modules` (pgvector 768); `suggestions.evidence_*`/`code_*`; `user_stories.origin_*`/`code_refs` |

## Indexing a repository

`POST /api/projects/{projectId}/code/repositories` (`INTEGRATION_WRITE`) with `{repository, branch?, accessToken?}`.

1. **Check GitHub first.** The repository and its branch are checked against GitHub before anything is stored, so a typo or a rejected token is answered right away.
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
3. **Reindex** with `POST …/{id}/reindex`. It downloads the branch again, but only modules whose files changed are described again (`content_hash`).
4. **Stalled runs.** A run silent for 20 minutes (for example, the server restarted) reads as failed and can be retried.

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
- `QuoteLocator` places the quote on its transcript segment.

The suggestion stores both, the live `SUGGESTION_GENERATED` message carries them, and an accepted story keeps them as `origin` and `codeReferences`. The code never decides the type of a suggestion: the backlog rules still choose NEW_STORY, UPDATE_STORY or EDGE_CASE.

## Privacy and security

- **No raw code is stored.** Code is read once while indexing; only summaries, rules, symbol names and the profile are kept.
- **Secrets never reach the model.** Secret files are never read, and secrets inside code are removed before the model sees anything.
- **Code is treated as data.** It goes to the model inside `<code>` tags, with look-alike tags neutralized, and the prompt tells the model to treat it as data.
- **Tokens are encrypted and never returned.**
  - Private repositories need a fine-grained token with read-only **Contents** access to the selected repositories.
  - It is stored AES-256-GCM encrypted (`INTEGRATIONS_ENCRYPTION_KEY`, the same key as the Jira secrets) and never returned.
- **Disconnecting forgets everything.** It deletes the repository, its token and every module.

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `CODEBASE_GITHUB_API_URL` | `https://api.github.com` | GitHub REST API (tests and E2E point it at a local fake) |
| `CODEBASE_MAX_REPOSITORIES` | `3` | Repositories per project |
| `CODEBASE_MAX_MODULES` | `80` | Modules described per repository |
| `CODEBASE_SUMMARY_LANGUAGE` | `Spanish` | Language of module summaries |
| `INTEGRATIONS_ENCRYPTION_KEY` | — | Needed only to store tokens of private repositories |

The summaries use the same model as requirement generation (`GENERATION_PROVIDER`), and their tokens are metered against the organization's plan.

Unauthenticated GitHub calls are limited to 60 per hour per IP. Indexing a public repository takes 3 calls: metadata, branch head and zipball.
