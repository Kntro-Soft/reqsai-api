# Quality indicators for the pilots (US71)

A panel for the project lead with three indicators that validate the success criteria of a pilot:

1. the share of AI-generated stories **accepted without major rewriting**;
2. the **post-processing time** of each session (average and median);
3. the **clarification meetings** held.

`GET /api/projects/{projectId}/quality-metrics?from=YYYY-MM-DD&to=YYYY-MM-DD` (header `Api-Version: 1`). Both dates are optional: the period defaults to the last 30 days up to today. Requires `STORY_READ` and `SESSION_READ` on the project (organization owners and admins pass), otherwise `403 QUALITY_ACCESS_DENIED`; an unknown project is `404`. A period with `from` after `to`, or longer than 366 days, is `400 QUALITY_INVALID_PERIOD`.

Nothing is invented: an indicator that cannot be computed for the period is `null` in the response, never `0`, and `hasData` is `false` when nothing at all happened in it.

## Period

`from` and `to` are UTC days and both are included: the period is `[from 00:00Z, to + 1 day 00:00Z)`.

| What | Belongs to the period when |
|---|---|
| A meeting (session) | it **ended** in the period: `discovery_sessions.ended_at` (set when the recording stops or a transcript is uploaded). A session that never ended (draft, still recording) is not a held meeting |
| A story decision | the story's **current review decision** (approved, exported or rejected) was taken in the period: `user_stories.reviewed_at` |

## Where the data comes from

| Need | Source |
|---|---|
| When a story was approved or rejected | `user_stories.reviewed_at`, set by `UserStory.changeReviewStatus` (approve or reject), cleared when the story goes back to draft. Repeating the same decision keeps the first instant. Decisions taken before this feature have no instant and are **not** back-filled |
| What the AI proposed | `user_stories.generated_text`, frozen when the story is created from the AI: the batch generation (`StoryExtractionService`) and the acceptance of a `NEW_STORY` suggestion (the suggestion's draft, **before** the analyst's edits replace its criteria). Manual, imported and earlier stories have none |
| What the story says now | `UserStory.currentText()` |
| When a meeting ended | `discovery_sessions.ended_at` |
| Whether a meeting is a clarification | `discovery_sessions.purpose` (`DISCOVERY` default, `CLARIFICATION`), chosen when the session is created (`POST /api/projects/{projectId}/sessions`, optional `purpose`) |

The existing model had none of the three: a story kept only its current status and an `updated_at` that any edit moves; the text the AI generated was not stored once an analyst edited it; and a session could not be told from a clarification meeting. Migration `V20261118140000__quality_metrics.sql` adds `reviewed_at`, `generated_text` and `purpose`, the smallest mechanism that makes the three indicators honest.

### Story text

`StoryText` composes the text the same way for the proposal and for the current story: title, role, action and benefit, one per line, then one line per acceptance criterion (`scenario: given | when | then`), with the criteria **sorted**, so reordering them is not a change. Priority and story points are not part of the text: they are estimates, not wording.

## 1. Accepted without major rewriting

Over the **AI-generated stories decided in the period** (those with a `generated_text` and a decision in the period):

- `accepted` = decided with status `APPROVED` or `EXPORTED`; `rejected` = `REJECTED`;
- an accepted story is **kept** (accepted as generated) when `similarity(generatedText, currentText) >= 0.80`, otherwise **rewritten**;
- `rate = kept / decided`, between 0 and 1; `null` when `decided = 0`.

The rejected stories count in the denominator whatever their text and never in the numerator.

`similarity(a, b) = 1 - levenshtein(normalize(a), normalize(b)) / max(len(normalize(a)), len(normalize(b)))`, and 1 for two empty texts. `normalize` lower-cases, removes accents and turns every run of non-alphanumeric characters into one space, so case, accents, punctuation and line breaks are not rewrites. The Levenshtein distance counts single-character insertions, deletions and substitutions. The threshold is inclusive: with 100 characters, 20 changed is kept and 21 is a rewrite (`QualityIndicatorsCalculator.SIMILARITY_THRESHOLD`).

Decisions on stories without a baseline (`withoutBaseline`) are counted apart and left out of every other figure.

Accepting an `UPDATE_STORY` or `EDGE_CASE` suggestion changes an existing story. When that story came from the AI and the analyst accepted the proposal as is, its baseline moves with it (the AI's own change is not a rewrite). If the analyst edited anything in the same acceptance, the baseline is left alone and the edit counts as a change. Only suggestions that became stories count: a dismissed suggestion never was a story and is not in the denominator.

## 2. Post-processing time per session

The time from the **end of the meeting** (`ended_at`) to the **last review decision on the stories it produced** (`user_stories.session_id` = the session).

A meeting is **measured** only when its review is finished:

- it produced at least one story that was approved, rejected or exported;
- none of its stories is still `DRAFT` (merged stories are ignored);
- every decided story has its `reviewed_at`.

For a measured meeting, `seconds = max(0, max(reviewed_at) - ended_at)`. It is never negative: a story approved live, before the recording ended, counts as zero. The response lists the measured meetings (newest first) with their seconds.

- `averageSeconds` = mean of the measured meetings, rounded to the second;
- `medianSeconds` = middle value of the sorted seconds; for an even count, the mean of the two middle values, rounded;
- both are `null` when no meeting is measured. `unmeasured` counts the meetings of the period that are not (stories still in draft, no story, or no recorded decision time).

This is a proxy: the model does not record when the analyst *started* reviewing, so the span includes any waiting before the review began.

## 3. Clarification meetings

`meetings` = meetings of the period created with `purpose = CLARIFICATION`; `sessions` = every meeting of the period; `share = meetings / sessions`, `null` when there was no meeting. The purpose is a property of the session chosen by the analyst when creating it; nothing infers it. Existing sessions are `DISCOVERY`.

## `hasData`

`true` when at least one meeting ended in the period or at least one story was decided in it (including stories without a baseline). The panel shows its no-data message otherwise.

## Where it lives

| Piece | Where |
|---|---|
| Endpoint, permission check, period | `overview` module: `QualityMetricsController(Impl)`, `ProjectQualityService` |
| Formulas (pure) | `overview`: `QualityIndicatorsCalculator`, `TextSimilarity` |
| Data | `discovery::insights`: `DiscoveryInsightsPort.qualityEvidence` → `QualityEvidence` (`DecidedStory`, `HeldSession`) |
| Baseline, review time | `discovery`: `UserStory` (`generatedText`, `reviewedAt`), `StoryText`, `StoryExtractionService`, `AcceptSuggestionCommandHandler` |
| Purpose | `discovery`: `SessionPurpose`, `DiscoverySession.purpose` |
| Web | `reqsai-web`: `/projects/:projectId/quality` ("Indicadores") |

The evidence loads the decided stories of the period with their criteria to compare texts in memory (a Levenshtein distance cannot be computed in SQL over long texts); a period is capped at 366 days.
