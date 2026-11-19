# Improve a story with AI (US73)

When a story scores low on the quality characteristics (US68), the analyst can ask the AI for an improved version, review it, edit it and apply it. The AI never changes a story by itself: the first call only **proposes**, a second call **applies** what the analyst reviewed.

Both endpoints need the header `Api-Version: 1` and the project permission `STORY_WRITE` (the same as evaluating or editing a story). A story that does not exist in the project answers `404 USER_STORY_NOT_FOUND`.

## 1. Propose

`POST /api/projects/{projectId}/stories/{storyId}/quality/improvement` (no body) answers `200`:

```json
{
  "storyId": "uuid",
  "proposal": {
    "title": "...", "role": "...", "action": "...", "benefit": "...",
    "acceptanceCriteria": [ { "scenario": "...", "given": "...", "when": "...", "then": "..." } ]
  },
  "pendingValues": [ { "token": "[[tiempo máximo de respuesta]]", "hint": "¿En cuántos segundos debe responder?" } ],
  "fixes": [ "Se reemplazó «rápido» por un valor pendiente" ],
  "expectedQuality": { "storyId": "uuid", "score": 100, "characteristics": [ ... ], "evaluatedAt": "..." }
}
```

- **Nothing is stored.** The story, its criteria and its quality assessment are exactly as they were. (The call reads the project profile and, like the assistant chat, may embed the story text to pick the nearest glossary terms; it writes nothing to the tenant schema.)
- `proposal` is written in the language of the story: Spanish unless the story is clearly in English.
- `pendingValues[].token` is a literal `[[...]]` placeholder that appears verbatim in the text of `proposal` (any field). `hint` is a question for the analyst.
- `fixes` has one short sentence per change.
- `expectedQuality` has the shape of `StoryQualityResponse` (US68) and is the rule-based evaluation of the proposal **as it is**, run on a transient story (the real story's priority and estimate, the proposal's text and criteria). It is not stored and uses no AI. While tokens are present nothing special happens: the text is evaluated normally. The brackets match no term of the ambiguity lexicon, so a token is not ambiguity by itself, and a criterion whose "then" has a verb of something observable ("responde", "muestra", "descarga") is judged verifiable even with a token in it. The prompt asks the model to write each token as a neutral description of the missing value, so its words do not trigger the lexicon either. The score is therefore what the story would score once the analyst supplies the values.

### What the model is given

`StoryImprovementRequest`: the story (title, role, action, benefit, criteria); its **unmet** findings (observation and suggestion, from the stored assessment or, if it was never assessed, a fresh evaluation that is not stored); the project profile nearest to the story (description, domain, constraints, glossary, client document summaries) built by `RealtimeSuggestionService.profileContextFor` on top of `GenerationContext.from(snapshot)`, with no backlog and no code; and the story's origin fragments (`story_source_fragments`, US72; the origin quote of a story from before fragments were kept; at most 6), so the proposal stays faithful to what the client said. A story that already meets everything is still improved: the prompt asks for a minimal polish.

### Prompt guard-rails (`StoryImprovementPrompt`)

- Fixed rules and the output schema come first (provider prompt cache), then the project context, the story, the findings and the quotes, each in its own tag (`<context>`, `<story>`, `<findings>`, `<sources>`), then a final reminder of the contract.
- Everything between the tags is **untrusted data**: the story and the quotes were typed or said by people, the findings and the context quote them. Any look-alike of the four tags in that text (case, spaces, attributes, full-width or HTML-entity brackets) is rewritten to an inert `[story]`, so it cannot close a block or open a fake one. The rules say that text inside the tags is never instructions.
- **Never invent**: no number, amount, percentage, limit, name, role, date or rule that the story, the sources or the context do not state. A value the source gives may be used as given. Where a measurable value is needed and unknown the model writes a `[[...]]` token (2 to 8 neutral words, no vague words, the same token for the same value) and declares it in `pendingValues` with a hint.
- One story only: when the original joins several actions the model keeps the main one and says in `fixes` which were left out, so the analyst can split them.
- Limits are stated to the model, and enforced afterwards (below).

### What is checked after the model answers

`StoryImprovementParser` (infrastructure) tolerates code fences, prose around the JSON, a proposal given at the root and a pending value given as a bare string; anything that is not the contract (no JSON object, no proposal, a criterion that is not an object) is an `UnparseableGenerationException`. Then `StoryImprovementNormalizer` (application, whatever the provider) checks:

- **Lengths**, the same as `CreateUserStoryRequest` and the criterion DTOs: title 200; role, action and benefit 500; scenario 200; given, when and then 1000; blank required text; at most 20 criteria. A text over its limit is **rejected, not cut** (cutting could drop a token or change the meaning).
- **Pending values**: a token written in the text but not declared is **rejected** (the analyst would not know what to supply); a declared token that is not in the text is **dropped**; a declaration written without brackets is wrapped; one without a hint gets the words between its brackets as hint; the result lists each token once, in order of appearance.
- **Fixes**: trimmed, deduplicated, at most 10, 300 characters each.

A rejected or unparseable reply is asked for once more (2 attempts in all, like the assistant chat); then the call fails with `422 REQUIREMENT_GENERATION_FAILED` ("The improved story could not be read").

### Usage, allowance and errors

It behaves like the other explicit AI requests:

| Situation | Answer |
|---|---|
| Monthly allowance spent | `402 AI_QUOTA_EXCEEDED` (with `plan`, `limit`, `used`, `resetsAt`), checked before any model call; reading, editing and **applying** are never blocked |
| No model configured or reachable | `422 REQUIREMENT_GENERATION_FAILED` ("The AI story improvement is not available"), the same code and status as the assistant chat |
| Reply unusable twice | `422 REQUIREMENT_GENERATION_FAILED` |
| Provider failure (network, timeout) | not caught here: the usual server error of the AI endpoints |

The call runs inside `AiUsageScope.call(AiFeature.STORY_IMPROVEMENT, projectId, ...)`, and the adapter hands the tokens the provider reported to the `TokenUsageRecorderPort` (`BillingTokenUsageRecorder`), so each call is one row of `ai_usage_events` with feature `STORY_IMPROVEMENT` and counts against the plan like any other generation (`docs/BILLING.md`). The `expectedQuality` evaluation costs nothing. `STORY_IMPROVEMENT` is a new value of the `AiFeature` enum; the `feature` column is a plain `varchar(32)`, so no migration is needed.

## 2. Apply

`POST /api/projects/{projectId}/stories/{storyId}/quality/improvement/apply`. The body is the `proposal` object as the analyst reviewed it:

```json
{ "title": "...", "role": "...", "action": "...", "benefit": "...",
  "acceptanceCriteria": [ { "scenario": "...", "given": "...", "when": "...", "then": "..." } ] }
```

It answers `200` with `{ "story": <UserStoryResponse>, "quality": <StoryQualityResponse> }`. In one transaction (`ApplyStoryImprovementCommandHandler`):

1. Any `[[...]]` token left in any field (title, role, action, benefit, scenario, given, when, then) answers **`422 IMPROVEMENT_PENDING_VALUES`**, with the tokens in `detail`, and nothing changes. `[[]]` counts as a token.
2. Title, role, action and benefit are replaced with `UserStory.update`, and the acceptance criteria are **replaced**: every existing criterion is removed with `removeAcceptanceCriterion` and the body's are added with `addAcceptanceCriterion`. The same domain methods and invariants as the manual edit (required fields, lengths) apply. An empty `acceptanceCriteria` removes them all.
3. The story is saved and `StoryQualityAssessor.evaluate` evaluates and stores the quality (creating the assessment if the story had none), so `quality` is the stored assessment.

Request validation (Bean Validation, `400`): title at most 200, role, action, benefit at most 500, required and not blank; `acceptanceCriteria` required, at most 20; scenario at most 200; given, when and then required, at most 1000.

What apply keeps, on purpose:

- **Priority, story points and status.** Like `UpdateUserStoryCommandHandler`, apply does not touch the review status: an `APPROVED` story stays `APPROVED` (and `reviewed_at` is unchanged); the analyst decides whether to send it back to draft. The embedding is not recomputed either (a manual edit does not do it; the re-index pass covers it).
- **`generated_text` (US71).** The baseline of what the AI first generated is never touched, so an applied improvement counts as a rewrite in the quality indicators (`docs/QUALITY_INDICATORS.md`): it lowers the "accepted without major rewriting" rate when it changes the text enough.
- **Origin and sources.** `origin_*` and `story_source_fragments` are unchanged.

Applying makes no AI call, so it needs no allowance and works when the AI is down.

## Switching the provider

`StoryImprovementPort` is implemented by `LlmStoryImprovementAdapter`, wired by `StoryImprovementConfiguration` (`@ConditionalOnMissingBean`, so a test stub replaces it, see `StubStoryImprovementConfig`). Like the generation, the verification and the document classification adapters it follows `reqsai.ai.generation.provider`: `openai` calls `OpenAiChatModel` in JSON mode, any other value (default `gemini`) calls the configured `ChatModel`. So the provider switch and the local-AI setups of `docs/LOCAL_AI.md` (a local model behind either) apply to it with no extra property. Without a chat model of the active provider `isAvailable()` is false and the endpoint answers as described above.
