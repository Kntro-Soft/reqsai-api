# Changelog — Reqs-AI API (Backend)

All notable changes to this backend are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versioning
follows [Semantic Versioning](https://semver.org/).

---

## [Unreleased]

_Bounded-context implementation (iam, billing, workspace, discovery, gateway) in progress._

### Fixed (Suggestion quality — `feature/discovery-session-control`)

- **Accepted-twin duplicates now converge to `UPDATE_STORY`** — a `NEW_STORY` draft that
  near-duplicated an already-accepted, indexed story was persisted as a standalone duplicate: the
  server deduped only against still-`PENDING` suggestions and only upgraded to `UPDATE_STORY` at the
  strict 0.85 duplicate-story gate. It now runs `findMostSimilar` against the accepted backlog for
  every `NEW_STORY` draft and downgrades to `UPDATE_STORY` at the dedup threshold
  (`discovery.realtime.dedup-similarity-threshold`, 0.84) applied consistently, recording the match
  similarity (previously always null) so accepted-twin paraphrases become the update the analyst wants.
- **`UPDATE_STORY` reachable for still-`PENDING` twins** — the prompt listed pending suggestions by
  title only, so the model could not target one and overlapping mentions spawned near-duplicate
  `NEW_STORY`s. `GenerationContext.alreadySuggested` now carries each pending suggestion's id
  (`PendingSuggestion(id, summary)`), rendered as `id | summary` with an instruction to target a
  pending item by id when the conversation refines it. When the model targets a still-`PENDING`
  suggestion, the draft is dropped (converges onto the queued item) instead of persisting a second
  near-identical suggestion.
- **Realtime passes serialized per session** — overlapping `REQUIRES_NEW` passes (~2 s apart) both
  read the `PENDING` set and watermark before the earlier pass committed (read-committed), so the
  earlier draft was invisible to the later pass's dedup and both burst out near-duplicates. A new
  `SessionLockPort` backed by a Postgres `pg_advisory_xact_lock` keyed on the session is taken at the
  start of the pass (before the watermark/`PENDING` reads, released on commit), so the later pass sees
  the previous pass's committed suggestions and advanced watermark. Chosen over an in-JVM lock because
  the passes commit in separate transactions (and it holds across app instances).
- **Targetless `EDGE_CASE` no longer minted as a standalone story** — an `EDGE_CASE` whose target
  could not be resolved had no similarity floor on the embedding fallback (it attached to the nearest
  story however weak) and, worse, `acceptAsEdgeCase` fell back to creating a granularity-violating
  one-criterion standalone story. The embedding fallback now applies the 0.84 dedup floor, and accept
  with no resolvable target is rejected with a clear `EDGE_CASE_WITHOUT_TARGET` error (422, suggestion
  kept `PENDING`) so the analyst assigns a target or reclassifies it.
- **Over-long edge-case scenario capped, not fatal** — a refactor dropped the 200-char cap on the
  criterion `scenario`, so a long LLM label failed the whole accept on `AcceptanceCriterion`'s
  `maxLength(scenario, 200)`. `Suggestion.sanitizeCriteria` now truncates the scenario to 200 on both
  creation and the analyst's edit-on-accept.
- **Prompt language-consistency rule** — a mistranscribed off-language fragment could become a garbage
  story. Both extraction prompts now instruct the model to omit any fragment in a clearly different
  language than the transcript and never mix languages within a story (prompt-only: no clearly-safe
  lightweight server-side language detector is available).

### Tests (Suggestion quality — `feature/discovery-session-control`)

- **End-to-end suggestion-quality integration coverage against real pgvector + advisory lock** — a new
  `SuggestionQualityRedDefectIntegrationTest` (tag `integration`, on the singleton `pgvector/pgvector:pg16`
  Testcontainer via `AbstractIntegrationTest`) proves the red-defect fixes against a REAL Postgres, stubbing
  only the two non-deterministic externals: a test-programmable LLM `RequirementGenerationPort`
  (`ProgrammableGenerationConfig`, which also captures the `GenerationContext` handed to it) and a
  concept-tagged deterministic `EmbeddingPort` (`ProgrammableEmbeddingConfig`, `[[concept]]` markers so
  paraphrases score cosine ≈ 0.97 ≥ 0.84 and distinct texts ≈ 0, with no OpenAI/Gemini calls). It asserts
  real DB state and the returned suggestions for: accepted-twin `NEW_STORY` → `UPDATE_STORY` downgrade with
  the similarity recorded and no duplicate story row; advisory-lock serialization of two overlapping
  concurrent `REQUIRES_NEW` realtime passes (exactly one suggestion — the machine-proof the mocked unit test
  cannot give); a pending-twin paraphrase dropped with the pending id carried into the generation context;
  the short-utterance char-trigger hold vs. force/stop flush release; the targetless `EDGE_CASE` accept
  rejected `EDGE_CASE_WITHOUT_TARGET` (422) with no standalone story minted; and the over-long edge-case
  scenario capped at 200. A sibling `SuggestionBroadcastIntegrationTest` connects a real STOMP client to
  `/ws/stomp` (JWT CONNECT via `StompAuthChannelInterceptor`) and asserts the `SUGGESTION_GENERATED` message
  is broadcast on `/topic/sessions/{id}` with its payload intact.
- **Real-LLM behavioral E2E probe for the suggestion core** — a new `RealLlmSuggestionBehaviorE2ETest`
  (tag `llm`) exercises the REAL generation pipeline end to end: REAL OpenAI generation + REAL OpenAI
  embeddings + REAL pgvector (Testcontainers via `AbstractIntegrationTest`), fed crafted transcripts at
  the segment/text level (audio/STT bypassed) and driven through `RealtimeSuggestionService`. Because
  the LLM is non-deterministic it is a behavioral PROBE, not a pass/fail gate: each scenario prints a
  grep-able `[LLM-E2E]` report block (suggestion type(s), draft title(s), targetStoryId, recorded
  similarity, outcome) and asserts only tolerant invariants (a force flush yields ≥1 suggestion; two
  distinct capabilities yield ≥2 story drafts; no two persisted stories exceed cosine ~0.90). Scenarios
  cover short-utterance cadence (char-trigger hold vs. force flush), exact duplicate, slight paraphrase
  near the 0.84 bar, distinct-but-related false-positive guard (login vs. reset-password), UPDATE on an
  accepted+indexed capability, the four suggestion types across a rich transcript, and hard cases
  (near-threshold paraphrase, incremental refinement across two passes, ambiguous → clarifying question).
  It wires the OpenAI adapters via `@TestPropertySource` (`spring.ai.model.chat/embedding=openai`,
  `reqsai.ai.{generation,embedding}.provider=openai`; the key flows from `OPENAI_API_KEY` via
  `application.yml`, never read from a file) and does NOT import the Stub/Programmable configs. Kept out
  of every normal lane by a dedicated `llmTest` Gradle task (and `excludeTags("llm")` on the default
  `test`/`unitTest` lanes); `@EnabledIfEnvironmentVariable("OPENAI_API_KEY")` plus an in-body
  `assumeTrue` make it SKIP (never FAIL) when no key is present. Run with
  `./gradlew llmTest --max-workers=1`.
- **Real-LLM behavioral E2E probe through the streaming `/ws/stt` WebSocket** — a second `llm`-tagged
  suite `RealLlmStreamingWsSuggestionE2ETest` drives the ACTUAL streaming path (the real product
  target), not the direct `suggest()` call: JWT `?token=` handshake → tenant resolution → binary handler
  → `AppendTranscriptSegmentCommandHandler` → `TranscriptSegmentAppendedEvent` →
  `RealtimeSuggestionListener` → `RealtimeSuggestionService` → REAL OpenAI generation + embeddings →
  persistence → STOMP broadcast. Only the STT vendor is faked: `EchoStreamingSttConfig` (a `@Primary`
  `StreamingTranscriptionPort` overriding the `@ConditionalOnMissingBean` `StreamingSttRouter`) decodes
  each binary frame as UTF-8 and echoes it back as a FINAL `TranscriptEvent`, so the WS client sends the
  transcript sentence AS the frame bytes and controls content exactly (matching how
  `handleBinaryMessage` hands frames to `recognizer.sendAudio(byte[])`). A `StandardWebSocketClient`
  streams utterances as binary frames; a STOMP client on `/topic/sessions/{id}` asserts live segment
  broadcasts. Same tolerant-invariant + rich-log matrix as the in-process probe (short-utterance cadence
  where `POST /stop` flushes; exact duplicate; near-0.84 paraphrase; distinct-but-related; UPDATE on an
  indexed capability; four types; hard cases) plus a streaming-only lifecycle scenario: segment
  broadcast, `POST /pause` closes the WS, `POST /resume` lets a new WS connect, `POST /stop` closes and
  flushes. Note: the code publishes both segments and suggestions on the single per-session topic
  `/topic/sessions/{id}` discriminated by the `type` field — not `/topic/discovery/sessions/{id}/segments`
  as `docs/WEBSOCKET_STT.md` states. Same wiring/skip/run rules; also runs under `./gradlew llmTest
  --max-workers=1`.
- **Real-LLM behavioral MATRIX probe (~90 data-driven cases)** — a third `llm`-tagged suite
  `RealLlmBehaviorMatrixE2ETest` widens the in-process probe into a `@ParameterizedTest` over ~88
  hand-authored Spanish cases run under ONE Spring context boot (`@TestInstance(PER_CLASS)` provisions
  the tenant once). It calls `RealtimeSuggestionService.suggest()` directly (not the WS — behavior is
  identical and this must stay fast/cheap for ~90 real-generation calls) against REAL OpenAI generation
  + REAL OpenAI embeddings + REAL pgvector. Each case seeds its own `Project` (so backlogs never
  cross-contaminate), optionally seeds accepted+indexed backlog stories, persists the transcript as
  final segments, runs one pass, and — crucially — for every case with a seeded backlog re-embeds each
  produced `NEW_STORY`/`UPDATE_STORY`/`EDGE_CASE` draft via the REAL `EmbeddingPort` and computes the
  cosine directly against each seeded story's stored embedding, so the report shows the ACTUAL number
  vs the 0.84 dedup bar (never `null`) — mapping exactly where dedup catches vs misses. Every case
  prints one greppable, stable line
  `[MATRIX][<category>][<id>] seeded=… input=… => produced=… rawTopCosineToSeed=0.xx converged=… expectation=… outcome=PASS|FAIL|OBSERVE`.
  Coverage spans 15 categories (A exact/near-exact duplicate, B mild paraphrase, C synonym-heavy
  paraphrase incl. regional es-PE/es-419/es-ES variants, D distinct-but-related false-positive guards,
  E update-a-detail, F edge cases, G clarifying questions, H cadence/timing — the only non-`force`
  cases, I multi-story transcripts, J mistranscription/garbage, K language, L incremental refinement,
  M contradiction/negation, N long 6+-capability transcripts, O threshold-boundary pairs). Because the
  LLM is non-deterministic almost every case is `OBSERVE` (logged, never failing) so the whole matrix
  always completes; hard assertions fire only for the unambiguous invariants (exact duplicate ⇒ ≤1
  story draft; genuinely-distinct capabilities ⇒ ≥2 story drafts; no two persisted stories exceed
  cosine ~0.97). Same OpenAI/pgvector wiring, `@EnabledIfEnvironmentVariable("OPENAI_API_KEY")` +
  in-body `assumeTrue` skip, and `llmTest` lane as the sibling probes; ~one generation call per case.

### Added (Suggestion acceptance model — `feature/discovery-session-control`)

- **Scenario labels on every generated criterion** — the extraction prompt now asks the model for a
  concise `scenario` label (in the transcript language) for each Given/When/Then it proposes, for the
  NEW_STORY criteria list AND the single EDGE_CASE criterion. It stays optional (dropped, never
  fabricated, when the model omits it) and round-trips through `DraftCriterion.scenario` onto the
  accepted story / criterion.
- **EDGE_CASE carries a real Given/When/Then criterion** — an EDGE_CASE suggestion previously reused
  the NEW_STORY draft fields and `acceptAsEdgeCase` twisted them into a criterion (`given = "the user
  is <role>"`, `when = action`, `then = benefit`, `scenario = "Edge case: <title>"`). It now carries a
  proper single `DraftCriterion { scenario, given, when, then }` (stored as the sole entry of the
  existing `draft_criteria` JSONB list — no migration) plus its `targetStoryId` and `relatedTopic`;
  the prompt asks the model for the boundary rule as one Given/When/Then entry and which existing story
  it belongs to. On accept the criterion is added verbatim via `UserStory.addAcceptanceCriterion`; the
  no-target fallback now builds a genuine standalone story from the story fields instead of the twisted
  mapping. The criterion is exposed on `SuggestionResponse.draftAcceptanceCriteria` (a 1-element list)
  and on the `SUGGESTION_GENERATED` / `SUGGESTION_ACCEPTED` WebSocket messages.
- **Full edit-before-accept** — `AcceptSuggestionRequest` / `AcceptSuggestionCommand` now optionally
  carry the whole edited payload applied on accept: `editedTitle/editedRole/editedAction/editedBenefit/
  editedPriority/editedStoryPoints` plus `editedAcceptanceCriteria` (a list of `{ scenario?, given,
  when, then }`). For NEW_STORY the edited criteria REPLACE the draft when sent; for EDGE_CASE the first
  edited criterion replaces the draft criterion added to the (unchanged) target story; for UPDATE_STORY
  the edited story fields are applied to the target. Edited fields carry the same `@Size`/`@NotBlank`
  constraints as creation (a criterion missing given/when/then is rejected with 400 via cascaded
  validation). Backward-compatible: an empty body (`{}`) accepts the raw draft exactly as before.

### Added (Realtime suggestion quality iteration — `feature/discovery-session-control`)

- **Char-or-time streaming cadence** — realtime suggestions only fired once the accrued past-watermark
  transcript crossed a 300-char threshold, so a burst of short segments (a meeting's back-and-forth)
  piled up and arrived as one late batch after the analyst had moved on. A pass now runs when EITHER
  ~180 new chars have accrued (`discovery.realtime.min-transcript-chars`, default lowered 300 → 180)
  OR ~22s have elapsed since the last pass with new transcript still waiting
  (`discovery.realtime.max-transcript-age-seconds`, new, default 22) — whichever first, never with zero
  new content; the stop-flush still bypasses both. Records `last_suggested_at` on the session (tenant
  migration V19) to drive the elapsed-time decision; the first pass still waits for the char threshold.
  Each suggestion is still persisted in its own `REQUIRES_NEW` transaction and pushed over WS the
  moment it commits, so the lower trigger directly shortens time-to-first-suggestion.
- **Two-layer near-duplicate dedup** — the guard compared only trim+lowercase titles, so identical
  drafts differing by an accent ("Recuperar contraseña" vs "…contrasena") slipped through and
  paraphrases across passes ("Autenticación de dos factores" vs "Soporte para 2FA en inicio de sesión")
  were invisible. `normalize()` now accent-, punctuation- and whitespace-folds the exact-match key, and
  each draft is embedded once and compared by cosine against the session's PENDING drafts plus drafts
  already accepted earlier in the same pass, dropping anything at/above a configurable
  `discovery.realtime.dedup-similarity-threshold` (0.84, just under the 0.85 strict-duplicate gate to
  catch paraphrases). The single draft embedding is reused by classification instead of embedding twice.
- **`UPDATE_STORY` reliably chosen** — the prompt now lists the bilingual verbal cues that signal a
  revisit/extension of an existing story ("volviendo a…", "además … debe…", "también quiero que …
  soporte…", "going back to…", "also it should…") and maps them to `UPDATE_STORY`; an explicit
  `UPDATE_STORY` whose target is missing/hallucinated resolves to the closest existing story by
  embedding, demoting to `NEW_STORY` only when the backlog is genuinely empty (was demoting too
  eagerly). A debug log records the raw LLM type + `targetStoryId` before/after validation so "never
  chosen" is diagnosable.
- **Quality bar and granularity guidance** — garbled speech-to-text ("inicio de sesión" → "inicio de
  decisión") produced nonsense like "Secure Decision Start". A QUALITY-BAR prompt rule tells the model
  to emit nothing for garbled/truncated fragments, and a minimal, clearly-safe server check drops any
  draft missing a core field (title/role/action/benefit) rather than letting the factory throw a false
  "failure". A GRANULARITY rule with bilingual examples routes session-maintenance / error-message /
  validation / security-constraint facets ("mantener la sesión activa") to `EDGE_CASE`/`UPDATE_STORY`
  of the parent capability instead of standalone stories. The ALREADY-SUGGESTED block is now a hard
  "do not repeat anything equivalent in any wording or language" constraint.
- **Draft acceptance criteria carried to the accepted story** — the LLM proposes Given/When/Then
  criteria per story but the realtime path dropped them, so the accepted story had none. `Suggestion`
  now stores the proposed criteria as a structured `List<DraftCriterion>` (`{ scenario?, given, when,
  then }`) in a JSONB column (tenant migration V20), mapped with `@JdbcTypeCode(SqlTypes.JSON)` mirroring
  `UserStory`'s pgvector embedding; a criterion missing given/when/then is dropped, never fabricated.
  The `NEW_STORY` prompt asks for 2–4 explicit Given/When/Then criteria in the transcript language. The
  criteria flow onto `SuggestionResponse` and the `SUGGESTION_GENERATED` WebSocket message
  (`draftAcceptanceCriteria: [{ scenario, given, when, then }]`, empty for other types); on accept the
  `NEW_STORY` handler adds each as a real `AcceptanceCriterion` via the existing `UserStory` API.

### Added (Realtime suggestion grounding — `feature/discovery-session-control`)

- **Backlog-grounded generation context** — the realtime suggestion prompt previously carried only project
  metadata and glossary terms; the LLM never saw a single existing story, so every requirement came out as
  `NEW_STORY` and near-duplicates flooded the backlog. `GenerationContext` now always includes an
  `existingStories` slice (id + title + role/action/benefit): preferred source is pgvector top-K stories
  nearest to the recent transcript, merged with the newest project stories (so stories accepted seconds ago
  are visible even before they rank), capped at 15; when the embedding provider is unavailable/failing or
  nothing is indexed, a plain most-recent query keeps the backlog visible (embedding-independent fallback).
  The context also lists the session's own `PENDING` suggestions (`alreadySuggested`) so the model does not
  re-suggest what the analyst has not reviewed yet — the root cause of several same-capability variants
  being suggested within one session. The retrieved context (story ids/titles, pending suggestions) is
  logged at debug level per generation pass for diagnosability.
- **LLM classifies against the backlog (`UPDATE_STORY` in the prompt)** — the contextual extraction prompt
  never offered `UPDATE_STORY` as a type and had no story ids, so the model could not point at an existing
  story; the only path to `UPDATE_STORY` was the post-hoc embedding upgrade at cosine ≥ 0.85, which misses
  paraphrased or partially-overlapping requirements. The prompt now instructs: on overlap with an existing
  story, emit `UPDATE_STORY` (or `EDGE_CASE` for boundary scenarios) with that story's `targetStoryId`;
  `NEW_STORY` only for genuinely new capabilities. The returned target is validated server-side (must be a
  story of the same project; hallucinated ids are dropped) and, when valid, wins over embedding search —
  and now also works when no embedding model is configured. The embedding near-duplicate upgrade is kept as
  a backstop. Prompts remain bilingual-safe (all text fields mirror the transcript language).
- **Lazy re-indexing of un-indexed stories** — a story persisted while the embedding provider hiccupped
  (e.g. the best-effort embed inside accept) stayed invisible to similarity search forever. New
  `UserStoryReindexService` embeds up to 10 un-indexed stories (oldest first) in a lazy, batched,
  best-effort pass invoked by the realtime pipeline right before each vector search; a failing provider
  aborts the pass and the next generation trigger retries. No scheduler, no new failure mode.
- **Project-level session lifecycle topic** — realtime messages only flowed on per-session topics
  (`/topic/sessions/{id}`), so a viewer on the project's discovery page could never learn that someone else
  created or started a session. New topic `/topic/projects/{projectId}/sessions` broadcasts
  `SESSION_CREATED`, `RECORDING_STARTED/PAUSED/RESUMED/STOPPED` with a self-describing payload
  `{ sessionId, projectId, type, status, title, language, startedAt, occurredAt }` — including the meeting
  language so other viewers' UIs can show it without a fetch. Subscription auth mirrors the per-session
  topics (JWT-authenticated STOMP CONNECT via `StompAuthChannelInterceptor`; no per-destination gate exists
  for either family). The five session lifecycle domain events now carry `title`/`language`/`startedAt`.
- **Suggestion resolution events carry the draft payload** — `SUGGESTION_ACCEPTED`/`SUGGESTION_DISMISSED`
  WebSocket messages sent every draft field as `null`, so a viewer whose feed received the push before (or
  instead of) a REST response rendered a blank decision entry — which read as a duplicate/corrupted history
  item. The events and messages now carry the full draft payload (title, role, action, benefit, priority,
  points, relatedTopic, targetStoryId, question).

### Added (Discovery session control — `feature/discovery-session-control`)

- **Discovery authorization at the edge** — every discovery REST endpoint (6 controllers) is now gated by
  Spring `@PreAuthorize`. Project-scoped routes use `@authz.projectPermission(#projectId, '…', authentication)`
  (org resolved from the JWT tenant, since these routes carry no `orgId` path variable); session-scoped
  routes (`/api/sessions/{sessionId}/…`) use a new `@Component("discoveryAuthz")` facade that resolves the
  session to its project and delegates to `WorkspaceModuleApi`. Six new `Permission` values —
  `SESSION_READ/RUN/DECIDE`, `STORY_READ/WRITE` — gate reads (`SESSION_READ`/`STORY_READ`), lifecycle and
  transcript actions (`SESSION_RUN`), suggestion accept/dismiss (`SESSION_DECIDE`), and story/criteria
  writes (`STORY_WRITE`). Owners/org-admins bypass, as in workspace. `project_roles.permissions` is text, so
  no migration is needed for the new values. `WorkspaceModuleApi.callerHasProjectPermission(projectId,
  userId, permission)` resolves the project permission from the currently-bound tenant.
- **WebSocket STT authorization** — opening a live STT stream (`/ws/stt`) now requires `SESSION_RUN` on the
  session's project. The handshake already authenticated the JWT and bound the tenant, but any active org
  member could previously stream audio into any project's session. `StartSttStreamCommandHandler` resolves
  the handshake user and checks the permission before connecting upstream; denial closes the socket with
  `1008 POLICY_VIOLATION` (new `SESSION_ACCESS_DENIED` error).
- **Single active session per project** — starting or resuming a session while another session of the same
  project is `RECORDING` or `PAUSED` now returns `409 SESSION_ALREADY_ACTIVE`, whose message carries the
  offending active session id. Checked inside the start/resume `@Transactional` handlers; a partial unique
  index `uq_sessions_project_active` (tenant migration `V18__discovery_single_active_session.sql`) is the
  concurrency backstop so two simultaneous starts cannot both win.
- **Session history-table stats** — `DiscoverySessionResponse` gains `storiesGenerated`, `storiesAccepted`,
  `suggestionsPending`, `questionsAsked` and `durationSeconds`. The four counts come from a new
  `SessionStatsRepository` computing them with two grouped queries (stories, suggestions) over the whole
  page of session ids — no N+1 on the list endpoint. `durationSeconds` is derived from `startedAt`/`endedAt`
  (null until a session has both). Populated on the get/list session endpoints; lifecycle-transition
  responses leave the counts null.
- **Project-level pending suggestions** — `GET /api/projects/{projectId}/suggestions?status=PENDING` (gated
  `SESSION_READ`, paginated, default status `PENDING`) lists a project's suggestions across all sessions, so
  the frontend can show "N pending from previous sessions". Reuses the existing suggestion response mapper.
- **Structured transcript segments (timeline replay)** — new `GET /api/sessions/{sessionId}/segments` (gated
  `SESSION_READ`) returns a session's **final** transcript segments as structured records for rebuilding a
  past session's chat timeline. Each item is `{ sequence, text, speakerLabel, startMs, endMs, occurredAt }`,
  where `occurredAt` is an **absolute** instant derived as `session.startedAt + startMs` (falling back to the
  segment's persisted `created_at`, then `session.createdAt + startMs`). The existing string `/transcript`
  endpoint is untouched. The endpoint is **cursor-paginated** so an hours-long session never returns thousands
  of segments at once: query params `beforeSequence` (exclusive cursor; omit for the newest page) and `limit`
  (default 50, capped 200) select the newest finals with `sequence < beforeSequence`; the response envelope
  `TranscriptSegmentPageResponse` returns `segments` **ascending** by sequence for rendering plus `hasMore`
  (older chunk remains) and `totalFinalSegments` (session-wide count). To page older, pass the first item's
  `sequence` as the next `beforeSequence`.
- **Session suggestions filterable by status (past decisions)** — `GET /api/sessions/{sessionId}/suggestions`
  gains an optional `status` query param (`PENDING` | `ACCEPTED` | `DISMISSED`), defaulting to `PENDING` so the
  live review queue stays backward-compatible. Frontends can now fetch a completed session's resolved decisions
  to render past-decision markers. `SuggestionResponse` already carries `updatedAt` (`@LastModifiedDate`), which
  is the resolution timestamp — the entity has no separate `resolvedAt`/`decidedAt` column, so no migration was
  added; the frontend reads `updatedAt` as "when the decision happened".
- **Post-stop suggestion decisions** — accept/dismiss of a `PENDING` suggestion works after the session has
  `STOPPED`/`COMPLETED` (post-meeting triage). No status coupling existed in the decision handlers, so this
  was verified (unit + integration) and preserved rather than added; the `SUGGESTION_ALREADY_RESOLVED` guard
  remains the only gate.

### Fixed (Realtime suggestion grounding — `feature/discovery-session-control`)

- **Accepting an `EDGE_CASE` with a near-max-length title failed server-side** — `acceptAsEdgeCase` prefixes
  the draft title with `"Edge case: "` before using it as the acceptance-criterion scenario, but both the
  title and the scenario column cap at 200 chars, so a title over 189 chars overflowed the scenario and the
  whole accept failed with a validation error the analyst could not fix. The composed label is now truncated
  to the column limit.
- **Realtime notification integration test dialed the old STOMP path** — the endpoint was namespaced under
  `/ws/stomp` (`fc01ae0`) but the test still connected to `/ws`, failing every HTTP upgrade with 404.

### Fixed (Discovery session control — `feature/discovery-session-control`)

- **Past sessions showed an empty conversation after reload** — `GET /api/sessions/{sessionId}/transcript`
  returned the session's `transcript` text field, which live-captured (streaming STT) sessions never
  populate: the conversation lives only as persisted `TranscriptSegment` rows. `GetSessionTranscriptQueryHandler`
  now assembles the transcript from the session's **final** segments in ascending `sequence` order (one
  segment per line) when the stored field is blank, and returns the stored text verbatim for
  batch/processed sessions. Read-only, single ordered query; the response field/shape is unchanged.
- **Accepting an AI suggestion could fail with a transient 500 on the first try** — `AcceptSuggestionCommandHandler`
  called the embedding provider inline inside the accept transaction; a transient provider failure (cold model,
  timeout, refused connection — common on the first call) aborted the whole transaction, so the analyst's
  explicit accept was lost and only a retry succeeded. The embedding step is now best-effort: on failure the
  story is persisted **un-indexed** (`UserStory.isIndexed() == false`, the same state as when no embedding model
  is configured) and the accept still commits. Similarity search skips the story until it is re-indexed. The
  accept remains idempotent/safe to retry (a failure rolls nothing forward; the suggestion stays `PENDING`).

### Changed / Removed (Discovery session control — `feature/discovery-session-control`)

- **Removed the session reset capability** (`refactor(discovery)!`) — sessions are immutable once finished, so
  the `POST /{sessionId}/reset` endpoint, its command/handler, `DiscoverySession.reset()`, the reset domain
  event and the `SESSION_RESET` realtime event type are gone.
- **No session deletion** — sessions are permanent immutable history (who did what, when) and can never be
  deleted; there is no delete endpoint and no `SESSION_DELETE` permission.
- Module boundaries unchanged: discovery references the workspace `@authz`/`WorkspaceModuleApi` beans by SpEL
  name only (no compile-time dependency on workspace internals); `architectureTest` and `verifyModularity`
  stay green.

### Added (Project access control — `feature/project-access-control`)

- **Granular project permissions** — replaced the coarse `MANAGE_*` permissions with a `resource:action`
  `Permission` catalog (`PROJECT_UPDATE/ARCHIVE/DELETE`, `MEMBER_READ/INVITE/UPDATE_ROLE/REMOVE`,
  `ROLE_READ/CREATE/UPDATE/DELETE`, `DOCUMENT_*`, `GLOSSARY_READ/TERM_WRITE/TERM_DELETE`,
  `CONSTRAINT_READ/WRITE`). Stored as text on `project_roles.permissions`, so no migration is needed for
  the new values.
- **Authorization at the edge** — every project/member/role/document/glossary/constraint endpoint is now
  gated by Spring `@PreAuthorize` against a single `@Component("authz")` facade
  (`orgOwner`/`orgOwnerOrAdmin`/`orgMember`/`projectAccess`/`projectPermission`). Permissions are
  **resolved from the database per request** (the JWT only carries `sub`/`orgId`/`role`); redundant
  handler-level authorization was removed. Project delete is gated by `PROJECT_DELETE`.
- **Invite people directly to a project** — batch `POST /api/organizations/{orgId}/projects/{projectId}/members/invite`
  (**owner/admin**), body `{ invitations: [{ email, displayName, roleId }] }` → `201` `MemberResponse[]`.
  Invitations now carry an optional project target (`target_project_id`/`target_role_id`, Flyway
  `common/V11__invitations_project_scope.sql`); on acceptance the project assignment is materialized in
  the invited org's tenant schema (`ProjectAssignmentMaterializer`, `REQUIRES_NEW`), skipping gracefully
  if the role was deleted.
- **Access-notification emails** (event-driven) — three distinct templates in `SmtpEmailAdapter`:
  org-only invite (unchanged); an invite that also names the target project and role; and a
  notification sent when an existing active member is assigned directly to a project — published as a
  `ProjectMemberAssignedEvent` (AFTER_COMMIT via the Modulith event-publication registry) from the
  direct-assignment handler only, so people who accept a project invitation are not double-notified.
- **Safe role deletion** — deleting a `ProjectRole` that members are still assigned to now returns
  `409 PROJECT_ROLE_IN_USE` (with the assigned count) instead of a foreign-key `500`, so callers can
  reassign first.
- Module boundaries unchanged: cross-module email uses `iam::ports`; `architectureTest` and
  `verifyModularity` stay green.

### Added (Organization invitations — `feature/org-invitations`)

- **Invitation lifecycle** — inviting a member (single `POST /api/organizations/{orgId}/members`,
  batch `.../members/batch`) now also issues a tokenized `Invitation` (new `public.invitations` table,
  Flyway `V10__invitations.sql`) alongside the existing PENDING member, and emails a tokenized
  acceptance link. Only the SHA-256 hash of the token is stored; the raw token travels only in the
  `MemberInvitedEvent`. One active (PENDING) invitation per member is enforced by a partial unique index.
- **Accept** — `POST /api/invitations/accept` (`Api-Version: 1`, **JWT-auth**), body `{ "token": string }`
  → `200` `{ organizationId: UUID, organizationName: string, memberId: UUID, role: string }`. Requires
  the caller's account email to match the invited email (exact, case-insensitive). `404` unknown token,
  `410` expired (and the invitation is marked `EXPIRED`), `403` on email mismatch, idempotent `200` when
  already accepted. On success the member is linked to the caller and activated.
- **Public lookup** — `GET /api/invitations/{token}` (`Api-Version: 1`, **public**, no auth) → `200`
  `{ organizationName, role, email, invitedByName: string|null, status, expired: boolean }` for the
  accept/signup screen; `404` if the token is unknown. Returns no sensitive fields.
- **Resend** — `POST /api/organizations/{orgId}/members/{memberId}/resend` (`Api-Version: 1`,
  **owner/admin**) → `200` `MemberResponse`. Supersedes the member's current invitation and issues a new
  token/expiry, re-sending the email. Only valid while the member is `PENDING` (`409` otherwise).
- **Revoke on removal** — `DELETE /api/organizations/{orgId}/members/{memberId}` now also marks a
  removed PENDING member's active invitation `REVOKED`, so the emailed link stops working.
- **Link-on-signup safety net** — a workspace listener reacts to IAM's
  `AccountVerifiedIntegrationEvent` and auto-accepts any PENDING invitation addressed to the
  just-verified (proven) email, covering invitees who sign up without clicking the link.
- **Config** — `reqsai.invitation.expiry` (`Duration`, default `7d`, env `INVITATION_EXPIRY`).
- Module boundary: the email listener consumes IAM's `EmailNotificationPort` (`iam::ports`) and the
  link-on-signup listener consumes `AccountVerifiedIntegrationEvent` (relayed from IAM's internal
  `AccountVerifiedEvent` into a new `iam::api` named interface, keeping the domain layer Spring-free);
  the email-match check resolves the caller's email via a new `AccountLookupPort` (`iam::ports`).
  `verifyModularity` stays green. See [ADR-0021](docs/adr/0021-organization-invitations.md).

### Added (Global search — `feature/global-search`)

- **Global search** — `GET /api/search?q={term}&limit={n}` (`Api-Version: 1`, JWT-auth) → `200`
  `SearchHitResponse[]` where `SearchHitResponse = { type:
  "PROJECT"|"USER_STORY"|"ORGANIZATION"|"MEMBER"|"GLOSSARY_TERM"|"DOCUMENT",
  id: UUID, title: string, subtitle: string|null, projectId: UUID|null }`. Powers the frontend command
  palette. `limit` defaults to `8` and is clamped to `[1, 20]`; a blank/whitespace `q` returns `200 []`.
  The tenant (organization) is resolved from the JWT `orgId`, like sibling endpoints. Results are the
  top matches merged across types (top-K per type, then merged and capped). For `GLOSSARY_TERM` the
  `title` is the term and `subtitle` is its definition; for `DOCUMENT` the `title` is the document name
  and `subtitle` is its `documentType`; both carry the owning `projectId`. See
  [ADR-0020](docs/adr/0020-global-search-postgres-trigram.md).
- **Lexical primitive (`pg_trgm`)** — new `pg_trgm` extension (created `WITH SCHEMA public`, mirroring
  `vector`) plus trigram GIN indexes: tenant migration `V17__search_trgm_indexes.sql` on
  `projects.name`, `user_stories.title`, `glossary_terms.term`, `project_documents.name`; common
  migration `V9__search_trgm_indexes.sql` on `organizations.name`, `organizations.slug`,
  `members.display_name`, `members.email`.
- **Search aggregator module** — new Spring Modulith `search` bounded context
  (`allowedDependencies = { shared, workspace::search, discovery::search }`) fans out to each context's
  `@NamedInterface("search")` port, which runs its own trigram query returning value snapshots
  (`shared.application.search.SearchHit`) — no JPA entity crosses a module boundary. Authorization
  reuses the existing rules: `ProjectAccessService` scopes projects/stories to what the caller can see
  (owner/admin see all; members see only assigned projects), and organization/member searches are scoped
  to the caller's own organizations/membership. The `workspace::search` port also covers **glossary
  terms** (`glossary_terms`, joined to its project via `glossaries`) and **project documents**
  (`project_documents`), filtered by the same accessible-project scope so no inaccessible-project row
  leaks.
### Added (Organization management — `feature/org-management-endpoints`)

- **Transfer ownership** — `POST /api/organizations/{orgId}/transfer-ownership` (body
  `{ "newOwnerMemberId": UUID }`) hands the organization to another **active** member and returns the
  updated `OrganizationResponse`. Owner-only (`403` otherwise); the previous owner is demoted to an
  `ADMIN` member row (existing row updated, or created if none existed). Invalid transfers (target
  already owner, member without a linked user) → `409 INVALID_OWNERSHIP_TRANSFER`.
- **Delete organization** — `DELETE /api/organizations/{orgId}` → `204`. Owner-only. Soft-deletes the
  registry row (`OrgStatus.DELETED`), evicts the tenant-schema cache and **drops the `tenant_<slug>`
  schema `CASCADE`** via the new `ProvisioningService.deprovisionTenant` (the inverse of provisioning).
  The handler is intentionally non-`@Transactional` (DDL must not run inside a JPA transaction), so the
  soft-delete commits first and a drop failure leaves a harmless `DELETED` org rather than an active org
  with no schema.
- **Leave organization** — `DELETE /api/organizations/{orgId}/members/me` → `204`. Deactivates the
  caller's own membership. The owner cannot leave (`409 ORGANIZATION_OWNER_CANNOT_LEAVE`) — they must
  transfer ownership first. `/me` is matched before `/{memberId}` by literal-segment precedence.
- **Batch invite** — `POST /api/organizations/{orgId}/members/batch` (body
  `{ "invitations": [ { "email", "displayName", "role" } ] }`) → `201 MemberResponse[]` (the created
  `PENDING` members, in order). Owner/Admin-only, same rules as the single invite. Atomic
  (`@Transactional`): any invalid entry, in-batch duplicate or already-present email fails the whole
  request (`409 MEMBER_ALREADY_EXISTS` naming the offending email; `400` for an OWNER role or empty list).
- **Activate / deactivate a member** — `PATCH /api/organizations/{orgId}/members/{memberId}/status`
  (body `{ "status": "ACTIVE" | "INACTIVE" }`) → `200 MemberResponse`. Same RBAC as the role change
  (owner over any non-owner; admin only over `MEMBER` rows, never self / another admin / owner). Uses a
  distinct `/status` sub-path so it does not collide with the existing role `PATCH /{memberId}`.
- **Multitenancy** — `ProvisioningService.deprovisionTenant(slug)` drops a tenant schema and evicts it
  from the schema cache, supporting organization deletion. No Flyway migration (reuses existing
  `public.organizations` / `public.members` tables).

### Added (Quality Gates — `feature/tooling-quality-gates`)

- **Spotless** (`com.diffplug.spotless:8.6.0`): Java code formatter using Eclipse formatter.
  `./gradlew spotlessCheck` enforced in CI before `compileJava`; `./gradlew spotlessApply` for
  local auto-format. Kotlin Gradle DSL files formatted with `ktfmt`. See [ADR-0016](docs/adr/0016-spotless-eclipse-java-formatting.md).
- **JaCoCo 0.8.13**: Code coverage reports (XML + HTML). `./gradlew jacocoTestReport` generates
  `build/reports/jacoco/`; minimum 50% coverage enforced via `jacocoTestCoverageVerification`.
  Codecov upload added to CI (`codecov/codecov-action@v5`). See [ADR-0017](docs/adr/0017-jacoco-codecov-coverage.md).
- **OWASP Dependency-Check**: CVE scanning via `org.owasp.dependencycheck:12.2.2`. Runs in a
  separate weekly workflow (`owasp.yml`) to avoid blocking PRs; fails only on CVSS ≥ 9.
  `owasp-suppressions.xml` for known false positives. See [ADR-0018](docs/adr/0018-owasp-dependency-check.md).
- **ArchUnit 1.4.2**: Architecture fitness functions (`ArchitectureTests.java`) enforcing domain
  purity, hexagonal layer isolation, and bounded context boundaries. Tagged `@Tag("architecture")`.
  See [ADR-0019](docs/adr/0019-archunit-architecture-fitness-functions.md).
- **Lefthook** pre-commit hook: runs `./gradlew spotlessApply` on staged `*.java` files before
  every commit.

### Added

- **Discovery — Upload Transcript + Process Transcript use cases** (full STT → LLM pipeline):
  `POST /api/sessions/{id}/upload` (multipart `file`) transcribes the audio via the configured STT
  provider and transitions the session `DRAFT → STOPPED` with the transcript and audio duration stored.
  `POST /api/sessions/{id}/process` runs the LLM requirement-generation pipeline on the stored
  transcript, extracting and persisting user stories, and transitions `STOPPED → PROCESSING → COMPLETED`
  (or `FAILED` on error, with `processingError` persisted for retry). `GET /api/sessions/{id}/transcript`
  returns the raw transcript text.
  Application layer: `UploadTranscriptCommand` + handler (load session, call STT port, save),
  `StartDiscoveryProcessingCommand` + handler (load+validate, call generation port, extract stories via
  `StoryExtractionService`, complete or fail), `GetSessionTranscriptQuery` + handler.
  Domain: `DiscoverySession.uploadTranscript(transcript, audioDurationMs)` (`DRAFT → STOPPED`, sets
  `endedAt` + `audioDurationMs`); `startedAt` now set in constructor; `startProcessing()` /
  `complete()` / `fail(reason)` transitions already present; `processingError` cleared on retry via
  `startProcessing()`.
  STT infrastructure — `TranscriptionPort` + `TranscriptionResult` record (rich: `text`,
  `detectedLanguage`, `durationMs`, `confidence`, `segments` with speaker labels; optional fields are
  null when the provider does not support them). `SttRouter` selects the adapter at runtime via
  `STT_PROVIDER` env var. Three adapters: **`WhisperAdapter`** (Spring AI `OpenAiAudioTranscriptionModel`,
  OpenAI-compatible protocol — works with `faster-whisper-server` locally and OpenAI cloud unchanged;
  returns text + language + duration from response metadata, no diarization);
  **`AssemblyAiAdapter`** (pure `RestClient` three-step async flow: upload → submit → poll; speaker
  diarization with labels "A"/"B"; `BASE_URL = https://api.assemblyai.com/v2`; `detectLanguage`
  auto-detected); **`DeepgramAdapter`** (official Deepgram Java SDK, synchronous, speaker labels "0"/"1",
  `detectLanguage(true)` required for non-English audio). All adapters throw `InfrastructureException`
  on empty transcript instead of surfacing a domain error.
  Generation infrastructure — `RequirementGenerationPort` + `GenerationResult` (list of
  `GeneratedStory` with title, role, action, benefit, priority, storyPoints, acceptance criteria).
  `RequirementGenerationRouter` selects adapter via `GENERATION_PROVIDER`. Two adapters via
  `AbstractLlmGenerationAdapter` (shared prompt construction, JSON extraction, `stripMarkdown`):
  `GeminiRequirementGenerationAdapter` and `OpenAiRequirementGenerationAdapter`.
  `StoryExtractionService` iterates generated stories: constructs `UserStory`, calls
  `UserStoryDeduplicationService.embedAndGuardDuplicates()` (skips and publishes
  `UserStoryNearDuplicateDetectedEvent` on `DUPLICATE_USER_STORY`), skips silently on construction
  validation failure, persists on success.
  Shared: `FileUploadUtils.readBytes(MultipartFile)` (shared kernel, throws
  `ResponseStatusException(422)` on I/O failure); `UNPROCESSABLE_REQUEST` added to `CommonError`;
  `GlobalExceptionHandler` now handles `ResponseStatusException` with consistent `ProblemDetail` +
  correlation ID. Integration tests (`ProcessTranscriptIntegrationTest`): full upload → process flow
  with stub STT + stub generation, GET transcript, and 422 when session is not STOPPED.
- **ADR-0013 — Exception handling strategy**: documents the two-layer pattern — domain layer
  (`XxxError` enum + `XxxExceptions` factory → `DomainException`) and infrastructure layer
  (`XxxInfrastructureError` + `XxxInfrastructureExceptions` + provider subclasses →
  `InfrastructureException`). Defines the golden rule (adapters never throw `DomainException`),
  `GlobalExceptionHandler` routing table (`DomainException` → message exposed WARN; `InfrastructureException`
  → "A server error occurred" ERROR with stacktrace; `ResponseStatusException` → reason exposed WARN),
  and package layout per bounded context.

### Fixed

- **Realtime AI suggestions pipeline (`bugfix/discovery-realtime-suggestions`)**: the live
  suggestion flow was effectively broken and several robustness gaps were closed.
  - **Similarity lookup (critical)**: `UserStoryJpaRepository.findClosest` was declared
    `Optional<Object[]>` for a two-column native query; under the current Spring Data/Hibernate the
    `Object[]` is the **row set**, not a single `(id, dist)` row, so `findMostSimilar` *always* threw
    (`IndexOutOfBounds` on an empty project, `Invalid UUID string: [Ljava.lang.Object;…` otherwise).
    `SuggestionCreationService` swallowed it, so **every** realtime suggestion was silently dropped.
    Changed to `List<Object[]>` + `findFirst()`.
  - **Duplicate suppression**: overlapping context windows re-surfaced the same idea every trigger.
    `SuggestionCreationService` now deduplicates generated items against the session's **PENDING**
    suggestions (and within the same batch) by normalized title/question.
  - **Failure visibility**: unexpected errors during suggestion creation are logged at **ERROR with
    the full stack** (not a `getMessage()` WARN), and the summary log reports
    `created / duplicate-skipped / failed` counts.
  - **WebSocket discriminator**: `SessionTranscriptSegmentMessage`, `SessionStoryGeneratedMessage`
    and `SessionProcessingFailedMessage` declared `type()` as a plain override (not a record
    component), so Jackson serialized them **without** a `type` field and the client could not
    discriminate live transcript/story/failed events. Added `@JsonProperty("type")`.
  - **Audio upload size**: `spring.servlet.multipart` was unset, so Spring's 1 MB default rejected
    real session audio (413). Raised to `${MAX_UPLOAD_FILE_SIZE:50MB}` / `${MAX_UPLOAD_REQUEST_SIZE:55MB}`.
  - **Logging**: fixed a broken `log.debug` that used SLF4J `{}`/`{:.2f}` placeholders inside Java
    `String.formatted()` (which never interpolated).
  - **Watermark-based trigger + flush (`V13`)**: realtime suggestions no longer fire on a rigid
    every-5-final-segments cadence (which cut topics mid-stream, re-processed overlapping windows,
    and could skip the tail). `discovery_sessions.last_suggested_sequence` records how far a session
    has been processed; the service generates only the not-yet-processed tail when enough new
    transcript has accrued, advances the watermark **only on success** (a transient failure is
    retried, never lost), and a final **flush on stop** processes the remaining tail so the end of a
    short meeting is not dropped.
  - **Duplication Alert (`V14`)**: batch `/process` near-duplicates were silently dropped (the
    `UserStoryNearDuplicateDetectedEvent` had no consumer), contradicting the architecture's
    "warn on >80% similarity / Display & Solve Duplication Alert" use case. They are now surfaced as
    `UPDATE_STORY` suggestions targeting the matched story, carrying a new `similarity` score
    (`suggestions.similarity`, exposed on `SuggestionResponse`), so the analyst can merge/update or
    dismiss them through the existing review flow.
- **Session reset left orphans**: resetting a discovery session to `DRAFT` deleted its user stories
  but **kept the suggestions and transcript segments**, so the "fresh" session still showed stale
  PENDING suggestions and old segments. `ResetDiscoverySessionCommandHandler` now also clears the
  session's suggestions and transcript segments.
- **Cross-project suggestion target (integrity)**: accepting an `UPDATE_STORY`/`EDGE_CASE`
  suggestion whose `targetStoryId` pointed at a story in a **different project of the same tenant**
  mutated that other project's backlog (the target was loaded by id with no project check). The
  accept handler now verifies the target story belongs to the suggestion's project; otherwise it
  falls back to creating a new story in the correct project instead of touching the other one.
- **Concurrent suggestion accept (idempotency)**: two simultaneous `accept` calls on the same
  suggestion both passed the `PENDING` guard (each in its own snapshot) and **each created a user
  story** — a double-click/retry produced duplicates. `AcceptSuggestionCommandHandler` and
  `DismissSuggestionCommandHandler` now load the suggestion with a `PESSIMISTIC_WRITE` row lock
  (`findByIdAndSessionIdForUpdate`), so concurrent requests serialize: the first wins, the rest
  re-read the resolved row and the domain guard rejects them (one `200`, the rest `409`, one story).
- **Project-listing tenant authorization**: `GET /organizations/{orgId}/projects` only checked that
  the org existed, so passing an org the caller cannot access returned an empty page (200) instead of
  a clear authorization failure. `ListProjectsQueryHandler` now verifies the requester is the owner
  **or** an active member of the org (else 403), consistent with the realtime member-access rules.
- **Member organization access (`bugfix/iam-member-org-access`)**: a non-owner active member could
  see an organization in the switcher but could not switch into it — the JWT `orgId` claim was only
  ever set to an org the user *owns*, so `PATCH /users/me/preferences` rejected member orgs and
  `/auth/refresh` kept the user's own org, resolving the tenant to the wrong schema (member orgs
  showed zero projects). The IAM `OrganizationLookupPort` now exposes `findDefaultOrganizationId` /
  `canAccess` (owner **or** active member, via `MemberRepository`); login/refresh fallback and the
  preferences validation use them, making member organizations fully navigable.
- **OpenAPI/Swagger toggle per environment (`bugfix/openapi-toggle-per-env`)**: `springdoc.api-docs`
  and `springdoc.swagger-ui` are env-var driven (`SPRINGDOC_API_DOCS_ENABLED` /
  `SPRINGDOC_SWAGGER_UI_ENABLED`). Production now defaults them to **`false`** (secure by default,
  still flippable via env var without a redeploy); the base profile keeps the dev-friendly default
  `true`. The `test` profile (`application-test.yml`) enables them so `SmokeTest`'s
  `servesOwnOpenApiDocument` serves the OpenAPI document. (CI sets no `SPRINGDOC_*` env var; note an
  OS-level env var would still outrank the profile file — unset it locally when running tests.)
- **Timestamp timezone offset (`bugfix/timestamp-timezone`)**: audit timestamps (`created_at`,
  `updated_at`) were stored with a UTC-5 offset because the Dockerfile set `TZ=America/Lima`,
  causing Hibernate to use the JVM's local timezone when writing to PostgreSQL. Fixed by changing
  `ENV TZ=UTC` in the Dockerfile and adding `hibernate.jdbc.time_zone: UTC` under
  `spring.jpa.properties.hibernate.jdbc` in `application.yml`. All migrations already used
  `TIMESTAMPTZ` columns, so no schema change was required.

### Changed

- **Test-suite performance (`feature/test-suite-performance`)**: faster local + CI test runs.
  (1) The Testcontainers Postgres data directory now lives in a RAM-backed tmpfs
  (`AbstractIntegrationTest`), so the per-tenant Flyway migrations are no longer disk-bound.
  (2) New `./gradlew unitTest` task runs only fast tests — excludes the `integration`, `architecture`
  and `modularity` tags — giving a Docker-free, sub-second feedback loop (80 of 104 test classes).
  (3) `gradle.properties` adds `org.gradle.caching` / `parallel` / `daemon` so unchanged tasks (e.g.
  `:test`) are skipped on re-runs.
  (4) New `./gradlew integrationTest` and `architectureTest` tasks (by JUnit tag), and
  `jacocoTestReport` now reports over whichever test task produced execution data — so coverage works
  per stage and is merged in Codecov by flag.
  (5) CI (`.github/workflows/ci.yml`) is now a 3-stage pipeline with fail-fast `needs`:
  **unit** (`unitTest`, no Docker) → **integration** (`integrationTest`, Testcontainers) →
  **verify** (`architectureTest` + `verifyModularity` + `assemble`). The Dockerfile comment was
  corrected to note layer caching is used on purpose (no BuildKit cache mount → builds on Kaniko/Railway).
  No production code changed.
- **AI provider configuration — `local-ai` profile eliminated**: the `application-local-ai.yml`
  overlay is deleted. AI providers are now activated exclusively via env vars (`SPRING_AI_MODEL_CHAT`,
  `SPRING_AI_MODEL_EMBEDDING`, `SPRING_AI_MODEL_AUDIO_TRANSCRIPTION`) and provider selectors
  (`GENERATION_PROVIDER`, `EMBEDDING_PROVIDER`, `STT_PROVIDER`) — no profile change needed.
  `AiPingController` and `SttPingController` moved from `@Profile("local-ai")` to `@Profile("dev")`
  with `ObjectProvider<ChatModel>` / `ObjectProvider<EmbeddingModel>` / `ObjectProvider<TranscriptionModel>`
  — respond `{"ok":false,"reason":"..."}` when the provider bean is absent, so they load in dev
  regardless of AI configuration. `OpenApiConfiguration.devToolsApi` simplified from
  `@Profile({"dev","local-ai"})` to `@Profile("dev")`. `.env.example` rewritten with commented
  per-provider sections (Gemini, OpenAI, Ollama, Whisper, AssemblyAI, Deepgram). `docs/PROFILES.md`
  and `docs/LOCAL_AI.md` updated to reflect env-var-only approach.

### Fixed

- **`AssemblyAiAdapter` — correct API base URL**: `BASE_URL` changed from
  `https://api.assemblyai.com` to `https://api.assemblyai.com/v2`; the unversioned path returns 404
  on all current AssemblyAI accounts.
- **`AssemblyAiAdapter` — isolated `RestClient`**: the adapter now receives `RestClient.create()`
  instead of the shared `RestClient.Builder`, preventing Spring AI's Whisper `baseUrl` from being
  inherited and corrupting absolute URLs.
- **`DeepgramAdapter` — language detection**: added `detectLanguage(true)` to the transcription
  request; without it Deepgram defaults to the English model and returns an empty transcript for
  non-English audio.
- **`DiscoverySession.startedAt`** was never populated — now set in the constructor so the field
  is non-null from creation.
- **`DiscoverySession.audioDurationMs`** was never written — `uploadTranscript` signature extended
  to `uploadTranscript(String transcript, long audioDurationMs)`; the STT handler now passes
  `result.durationMs()` from the provider.
- **`AbstractLlmGenerationAdapter`** — removed three dead null checks (`response == null`,
  `output == null`) that the IDE flagged as always-false; replaced with a single safe guard on
  `getResult()` which can legitimately be null.

---

- **Discovery — GET endpoints for Sessions and User Stories (three scopes)**:
  `ProjectSessionController` — `GET /api/projects/{projectId}/sessions/{sessionId}` (get by id, 404 on missing
  or project mismatch) and `GET /api/projects/{projectId}/sessions` (paginated list, sorted `createdAt DESC`,
  sortable by `title`/`status`/`createdAt`).
  `ProjectStoryController` — `GET /api/projects/{projectId}/stories/{storyId}` and
  `GET /api/projects/{projectId}/stories` (paginated backlog, sortable by `title`/`priority`/`status`/`createdAt`).
  `SessionStoryController` — `GET /api/sessions/{sessionId}/stories/{storyId}` (only AI-generated stories;
  manual stories with null `sessionId` return 404) and `GET /api/sessions/{sessionId}/stories` (paginated;
  unknown `sessionId` → 404 `SESSION_NOT_FOUND`, not an empty page).
  Application layer: `GetProjectSessionQuery`, `ListProjectSessionsQuery`, `GetProjectStoryQuery`,
  `ListProjectStoriesQuery`, `GetSessionStoryQuery`, `ListSessionStoriesQuery` + their handlers; port read
  methods (`findById`, `findAllByProjectId`, `findAllBySessionId`) added to both `DiscoverySessionRepository`
  and `UserStoryRepository`; Spring Data `Page`-backed adapters; `SESSION_NOT_FOUND` / `USER_STORY_NOT_FOUND`
  error codes (404 via `EntityNotFoundException`). `UserStoryResponse` now includes `embeddingIndexed: boolean`
  (true = embedding model was available at creation, story was dedup-checked and not a duplicate; false = model
  unavailable, dedup skipped, story not yet searchable by similarity). Integration and unit tests cover
  happy-path, 404, scope isolation, manual-story exclusion from session scope, and unauthenticated access.
- **ADR-0012 — REST route design and controller naming convention**: documents the `{scope}{Resource}Controller`
  convention (controller name reflects the URL parent context, not the bounded context); full table of all three
  discovery controllers with their routes and resources; rationale for two orthogonal scopes for `UserStory`;
  consequences including the divergence from the academic report's original session-only model.
- **Discovery — Create Discovery Session use case** (first discovery vertical slice): `DiscoverySession`
  aggregate root with the full domain model — `projectId`, `title`, `language`, `status`, `transcript`,
  `startedAt`, `endedAt`, `audioDurationMs`, `lastSequence`, `processingError`; `SessionStatus` enum with
  all lifecycle states (`DRAFT → RECORDING → PAUSED → STOPPED → PROCESSING → COMPLETED / FAILED`) and
  inline comments mapping the streaming vs batch paths and the live-assistance loop. Transition methods
  (`startRecording`, `appendSegment`, `stopRecording`, `startProcessing`, `complete`, `fail`, `reset`)
  stashed — arrive with their own use-case slices. `CreateDiscoverySessionCommand` + handler validates
  input, persists in `DRAFT`, raises `DiscoverySessionCreatedEvent`. REST `POST /api/projects/{p}/sessions`
  with swagger interface + controller + request/response mappers. Tenant-scoped migration
  `V2__discovery_sessions.sql` (complete schema including nullable temporal and error fields).
  Unit tests (domain + handler) and integration test (full multitenant flow with Testcontainers).
- **Discovery — Create User Story (manual) use case**: `UserStory` aggregate (`sessionId` nullable for
  manually-created stories, `projectId`, `title`, `role`, `action`, `benefit`, `priority`,
  `storyPoints?`, `status = DRAFT`, 768-dim `embedding`) + `Priority` / `StoryStatus` enums;
  `CreateUserStoryCommand` + handler raising `UserStoryCreatedEvent`; repo port + adapter; REST
  `POST /api/projects/{projectId}/stories` (swagger interface + controller + request/response mappers);
  tenant migrations `V3__user_stories.sql` + `V4__user_stories_embedding.sql`. For teams that already
  have a backlog and want to upload stories directly — a product extension beyond the report's
  AI-generated stories. Acceptance criteria and the external tracker ref are deferred to their own use
  cases. 11 tests (domain + handler + multitenant E2E incl. duplicate rejection).
- **Discovery — embedding-based duplicate detection** (manual create now, AI-generation later): on
  create the story text is embedded via `EmbeddingPort` → Spring AI `EmbeddingModel` (Ollama
  `nomic-embed-text` locally / Gemini in prod, selected by `ai.model.embedding`; dedup is skipped, and
  the app still boots, when none is configured) and compared to the project's existing stories with
  pgvector cosine distance (`<=>`); a similarity ≥ `0.85` is rejected `409 DUPLICATE_USER_STORY`. Vectors
  map via `hibernate-vector` (`@JdbcTypeCode(VECTOR)` → `vector(768)`); the `vector` extension is enabled
  in `public`. Integration tests run on a `pgvector/pgvector:pg16` Testcontainer (`AbstractIntegrationTest`)
  with a deterministic embedding stub — no external AI needed.
- **ADR-0011 — API response field-selection strategy**: documents the decision to use one shared DTO
  per resource (not one per use case), the rules for which fields to include (`createdAt`/`updatedAt`
  yes; `createdBy`/`updatedBy` no; large fields in separate endpoints), the security rationale
  (OWASP API3:2023), and the future Summary/Detail split trigger. Rejected alternatives:
  `@JsonView`, sparse fieldsets (JSON:API / Google AIP-157), GraphQL.

### Changed

- **Test infrastructure — `AbstractIntegrationTest` now provides `client()`**: `@LocalServerPort` and
  `protected RestClient client()` moved from every integration test class into `AbstractIntegrationTest`.
  Removes 3 fields and 6 methods across the 6 existing test classes; new tests inherit `client()` for free.
- **Test infrastructure — `StubEmbeddingConfig` extracted to shared `testsupport` package**: the
  deterministic embedding stub (same seed → same vector, dedup exercised without Ollama/Gemini) was
  duplicated as an inner `@TestConfiguration` in three test classes. Moved to
  `com.kntro.reqsai.testsupport.StubEmbeddingConfig`; each test now uses `@Import(StubEmbeddingConfig.class)`.
- **OpenAPI annotations — requests and responses enriched**: all request DTOs now carry `@Schema`
  per field with `description`, `example`, `minLength`/`maxLength`, and `requiredMode`
  (`REQUIRED` / `NOT_REQUIRED`) so Swagger UI shows constraints and examples instead of bare `string`.
  All response DTOs carry `@Schema` per field with `description`, `example`, `nullable`, and
  `allowableValues` for string-encoded enums (`status`). Swagger interfaces updated with `@Parameter`
  on every `@PathVariable` and `@ApiResponse(201)` now includes `@Content` + `@ExampleObject`
  showing the full JSON response body. Affected: `CreateDiscoverySessionRequest`,
  `CreateOrganizationRequest`, `DiscoverySessionResponse`, `OrganizationResponse`,
  `DiscoverySessionController` (swagger), `OrganizationController` (swagger).
- **`USE_CASE_PLAYBOOK.md` Step 4 updated**: documents the OpenAPI annotation conventions for
  request DTOs (`@Schema` fields, `requiredMode`), response DTOs (ADR-0011 rules + `allowableValues`,
  `nullable`), and swagger interfaces (`@Parameter`, `@Content`, `@ExampleObject`).
- **`OrganizationResponse`** now includes `createdAt` and `updatedAt` — aligns with ADR-0011 rule
  that all response DTOs expose these two audit timestamps consistently.

### Added

- **Workspace — Create Organization use case** (first business vertical slice, domain → controller):
  `Organization` aggregate in the global `public.organizations` registry with `Slug`, `GenerationSettings`
  and `PlanLimits` value objects (the last two mapped `@Embedded`; `Slug` via `@Convert`); `OrgStatus`
  adds `PENDING` for the provisioning window. `CreateOrganizationCommand` + handler validates slug
  uniqueness, persists `PENDING`, provisions the `tenant_<slug>` schema, then `activate()`s (→ `ACTIVE`,
  raising `OrganizationCreatedEvent`). REST `POST /api/organizations` split into a documented swagger
  interface + clean `@RestController` impl + dedicated request/response mappers; common migration
  `V2__organizations.sql`. This is what unblocks any tenant-scoped endpoint (creating an org provisions
  its schema). `LanguageCode` promoted to the **Shared Kernel** (shared by workspace `meetingLanguage`
  and discovery), with an `autoApply` `LanguageCodeConverter`.
- **Header-based API versioning** (Spring Framework 7 / Boot 4 native): `ApiVersioning` (base `/api`,
  `Api-Version` header, default `V1`) + `ApiVersioningConfig`; endpoints use `@PostMapping(version = V1)`.
- **Test data builders & parallel execution** ([ADR-0009](docs/adr/0009-test-data-builders-and-parallel-execution.md)):
  Datafaker-backed fluent **Builders** + **Object Mothers** (+ command mothers) in `mothers/` packages;
  Gradle `maxParallelForks` runs test classes in parallel across forked JVMs (each fork reuses its own
  Testcontainers DB + cached context), cutting the suite to ~20s.
- **Foundation regression tests**: `ApplicationContextTest` (full-context boot over a Testcontainers
  PostgreSQL — fails CI if any bean fails to wire) and `SmokeTest` (`RANDOM_PORT`, no port collisions;
  asserts health UP, readiness probe UP, anonymous requests rejected, `X-Request-ID` emitted, own
  OpenAPI served, secured actuator). These automate the previously-manual `bootRun` smoke checks so the
  boot bugs (servlet-filter auto-registration, pgvector demanding an `EmbeddingModel`, mail health) can
  only regress through a red build.

### Changed

- **API versioning moved from path to header**: routes are now `/api/<resource>` (e.g.
  `/api/organizations`) selected by the `Api-Version` header, instead of `/api/v1/<resource>` in the
  path; `SecurityConfiguration` public matchers updated (`/api/auth/**`). `Created` responses now build
  their `Location` via `ServletUriComponentsBuilder` instead of a hardcoded path.
- **Health groups**: `readiness` now includes only `readinessState` + `db`; `liveness` only
  `livenessState`. External/optional services (mail, Gemini) no longer gate readiness, so an
  unconfigured or down SMTP/AI provider cannot pull the app out of Cloud Run rotation.

### Fixed

- **`gradlew` executable bit** restored in the Git index (`100644` → `100755`) so fresh clones and CI
  runners can execute the wrapper without `chmod`.
- **Test datasource driver**: `application-test.yml` now sets
  `driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver`, overriding the production
  `org.postgresql.Driver` which rejected the Testcontainers `jdbc:tc:` URL.

---

## [0.1.0] - 2026-06-08

First infrastructure foundation — no business logic yet. Provides the cimientos every bounded
context builds on.

### Added

- **Build & tooling**: `build.gradle.kts` for Java 25 / Spring Boot 4 with Spring Modulith, Spring AI
  (Gemini + pgvector), Flyway, JJWT, Caffeine, UUID v7 (`uuid-creator`); `verifyModularity` Gradle
  task; JPA static-metamodel generation.
- **Shared Kernel**: `AuditableEntity` base (native `UUID` v7 PK, JPA auditing, identity-based
  `equals`/`hashCode`) for entities, and `AggregateRoot extends AuditableEntity` adding domain events
  for roots; soft-delete is opt-in per aggregate (not global). `Assert` (fluent validation),
  `IdGenerator`, `Email` value object, `DomainEvent`.
- **Multitenancy (schema-per-tenant)**: `TenantContext`, `CurrentTenantIdentifierResolverImpl`,
  `MultiTenantConnectionProviderImpl`, `TenantSchemaResolver` (Caffeine-cached), `ProvisioningService`,
  `TenantMigrationRunner`.
- **Security**: stateless Spring Security with RS256 JWT, split into a `TokenVerifier` port +
  `JjwtTokenVerifier` adapter (public key only, in `shared`) consumed by `JwtAuthenticationFilter`;
  token issuance (private key) is left to the `iam` context. `JwtProperties`, `SecurityConfiguration`,
  `SecurityBeans`; dev key generation script.
- **Error handling**: `ErrorCatalog` interface (per-context error codes; HTTP status per code) with
  shared `CommonError` for cross-cutting codes, minimal exception hierarchy + cross-cutting
  `Exceptions` factory, `GlobalExceptionHandler` producing RFC 9457 `ProblemDetail` (extends
  `ResponseEntityExceptionHandler`), and `CorrelationFilter` (MDC `correlationId` + `tenantId`,
  `X-Request-ID`).
- **Cross-cutting infrastructure**: JPA auditing (`AuditorAware<UUID>`), externalized CORS, and
  pagination (`PageResponse`, `PaginationProperties` with named constants, `PageRequestFactory` that
  clamps page size). OpenAPI/Swagger (`OpenApiConfiguration`, Bearer scheme, `@ConditionalOnProperty`)
  with reusable composite response annotations (`@ApiStandardErrorResponses`, `@ApiResponse*`) that
  document the RFC 9457 ProblemDetail contract.
- **Shared infrastructure layout**: organized into `cache/`, `documentation/openapi/{,annotations}`,
  `persistence/{auditing,multitenancy}`, `security/`, `web/{,error}`, and `interfaces/pagination/`,
  each with a `package-info.java`.
- **Query & pagination**: `PageResponse` (nested page metadata), `PageCriteria`, `PageRequestFactory`
  (size clamping), `SortPolicy` (whitelisted sort + `id` tie-breaker for stable paging), and
  `Specifications` (functional, null-safe JPA Specification composition — no per-entity subclass).
- **Real-time**: STOMP-over-WebSocket infra — `WebSocketConfig` + `WebSocketProperties` with a
  **switchable broker** (in-memory `SIMPLE` with heartbeats, or `RELAY` to an external broker for
  multi-instance — config only), `StompAuthChannelInterceptor` (authenticates CONNECT via the shared
  `TokenVerifier`), and the `RealtimeNotifier` port + `StompRealtimeNotifier` adapter. See ADR-0007.
- **Modules**: `shared` (OPEN) plus the `iam`, `billing`, `workspace`, `discovery`, `gateway`
  bounded-context skeletons, with boundaries verified by `ModularityTests`.
- **Configuration**: `application.yml` + `dev`/`prod`/`test` profiles under a single `reqsai.*`
  namespace; Java 25 virtual threads (`spring.threads.virtual.enabled`); no real secrets in VCS
  (prod `DB_PASSWORD` has no default); native structured logging (`logging.structured.format=ecs` in
  prod); actuator limited to `health,info,metrics,modulith` (only health public, mail health indicator
  disabled until SMTP is configured); pgvector vector-store auto-config excluded until the `discovery`
  context wires embeddings; `compose.yaml` (pgvector); Flyway migrations (`common`: event_publication,
  organizations; `tenant`: baseline). Local `compose.yaml` uses a `core` profile (PostgreSQL/pgvector
  + Mailpit for dev email) with healthchecks; Spring Boot starts it via `docker.compose.profiles.active`.
- **Verified**: boots against PostgreSQL/pgvector (Flyway applies the common migrations, security
  chain orders CorrelationFilter → JwtAuthenticationFilter, `/actuator/health` UP, protected routes
  return 401/403, Swagger/OpenAPI served). Security filters are wired into the chain (not registered
  as standalone servlet filters).
- **CI/CD & docs**: GitHub Actions (`ci`, `codeql`, `deploy` → AWS ECR/ECS Fargate), multi-stage
  `Dockerfile` (layered jar extraction for cache-friendly redeploys, non-root, `JarLauncher` exec
  entrypoint, container-aware JVM flags, `TZ=America/Lima`), Dependabot, repository governance
  (`CONTRIBUTING`, `CODEOWNERS`, `SECURITY`, templates), ADRs (`docs/adr/`) and contributor docs (the
  architecture overview lives in the README; the rationale in the ADRs).
- **AI autoconfig**: Spring AI (Gemini chat/embeddings + pgvector) auto-configurations are excluded
  so the app boots in every profile without AI credentials; the `discovery` context removes the
  exclusions and provides the Gemini key when it implements the AI pipeline.

**Author:** Gutiérrez Soto, Jhosepmyr Orlando

---

## [0.0.0] - 2026-06-06

### Added

- Initial Spring Boot project scaffold (Spring Initializr).

**Author:** Gutiérrez Soto, Jhosepmyr Orlando
