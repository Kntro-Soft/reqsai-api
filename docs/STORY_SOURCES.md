# Story sources (US72)

An analyst or reviewer can see, from a story, the fragments of the meeting it comes from, to check that it reflects what was agreed with the client.

`GET /api/projects/{projectId}/stories/{storyId}/sources` (header `Api-Version: 1`, project permission `STORY_READ`, like the other story reads). A story that does not exist in the project answers `404 USER_STORY_NOT_FOUND`.

## What is stored

A story keeps one `story_source_fragments` row per quote it comes from (tenant table, `StorySourceFragment`, owned by the `UserStory` aggregate and deleted with it):

| Column | Meaning |
|---|---|
| `session_id` | Session the quote was said in (plain UUID, no foreign key) |
| `sequence` | Transcript segment where the quote starts; null when the quote could not be located |
| `quote` | The verbatim fragment (500 characters at most) |

Rows are added by `UserStory.addSource`, which ignores a quote without session, a blank quote and a quote of that session the story already has:

- A story generated from a processed session (`StoryExtractionService`) and a story created by accepting a `NEW_STORY` suggestion get their first row through `recordOrigin`, from the evidence quote the model gave. `QuoteLocator` supplies the segment.
- Accepting an `UPDATE_STORY` or `EDGE_CASE` suggestion adds the suggestion's evidence to the story it refines. A story can therefore have fragments of several sessions.

The moment of the meeting is not stored. The handler reads the final segments the quote runs across (`QuoteLocator.span` over the few segments after `sequence`), so the offset (`startMs`, `endMs`), the wall-clock instant (`occurredAt`, session start plus `startMs`), the transcript text and the speaker (the name the analyst gave, else "Hablante N") always follow the transcript. A fragment whose segment is gone, or that was never located, still returns its quote, without a moment.

## The three origins

The response has an `origin`; none of them is an error.

| `origin` | When | Body |
|---|---|---|
| `SESSION` | The story has at least one fragment | `fragments`, oldest first, each with its session title and start |
| `MANUAL` | No fragments and no session (created by hand, imported, or accepted from the assistant chat) | No fragments; the web says the story does not come from a session |
| `UNAVAILABLE` | No fragments, but the story was generated in a session (`user_stories.session_id`) | `sessionId`, `sessionTitle` and `sessionStartedAt` when the session exists; no fragments |

## Historical stories

The migration `V20261118130000__story_source_fragments.sql` copies the single fragment older stories already had in `user_stories.origin_session_id/origin_sequence/origin_quote` into `story_source_fragments`, so they read as `SESSION`. The `origin_*` columns stay and still feed `origin` of the story response.

A story generated in a session before any quote was kept has nothing to recover: its quote was never stored and cannot be rebuilt reliably from the transcript. It reads as `UNAVAILABLE` ("origen no disponible") and the web still names its session.
