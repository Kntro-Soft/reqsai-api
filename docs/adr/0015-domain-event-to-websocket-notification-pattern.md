# 0015. Domain event → WebSocket notification pattern

- Status: Accepted
- Date: 2026-06-18
- Deciders: Kntro-Soft team

## Context

The frontend needs live status updates for discovery sessions (recording started/paused/resumed/stopped,
AI processing progress, generated stories) without polling. ADR-0007 established STOMP over WebSocket
as the transport. This ADR decides *how* application code produces those realtime messages and where
each piece lives.

Two delivery patterns were considered:

1. **Direct call** — the command handler injects `RealtimeNotifier` and calls it after `sessions.save()`.
2. **Domain event → `@ApplicationModuleListener`** — the domain method registers a domain event;
   after the transaction commits, Spring Modulith dispatches it to a dedicated listener.

## Decision

Use **pattern 2**: domain event → `@ApplicationModuleListener` → mapper → `RealtimeNotifier`.

### Why not the direct call

- Mixes orchestration and notification responsibilities in the handler.
- If `RealtimeNotifier` is called *before* the transaction commits and then the commit fails,
  clients receive a notification for a state that never persisted.
- Each new notification target (email, push, analytics) requires touching every handler.

### How it works

```
Handler
  ├─ session.startRecording(now)           // registers DiscoverySessionRecordingStartedEvent
  └─ sessions.save(session)
         │
         └── TRANSACTION COMMITS
                   │
                   └── @ApplicationModuleListener fires (async, own transaction)
                             │
                             └── SessionNotificationMapper.toMessage(event)
                                       │
                                       └── RealtimeNotifier.broadcast(SessionTopics.of(id), message)
                                                 │
                                                 └── /topic/sessions/{id} → WebSocket client
```

### `@ApplicationModuleListener` guarantees

`@ApplicationModuleListener` is Spring Modulith's composite annotation:
`@Async + @Transactional + @TransactionalEventListener(AFTER_COMMIT)`. It ensures:

- The listener runs **after** the originating transaction commits — the DB is already consistent
  when the client receives the notification.
- A slow or failed WS push **cannot roll back** the DB write.
- Failed publications are retried by the Event Publication Registry (no lost events on restart).

### Topic design

A single topic per session carries all event kinds:
`SessionTopics.of(sessionId)` → `"sessions/{id}"` → `/topic/sessions/{id}` (notifier adds prefix).

The client subscribes once per session page and discriminates on the `type` field (`SessionEventType`).
Never one topic per event type — that would require the client to manage N subscriptions and break
if a new event type is added.

`SessionTopics` is the **single source of truth** for topic strings. Never hand-concatenate
`"sessions/" + id` at a call site.

### Message design

Messages are grouped by **payload structure**, not by semantic event name:

| Message class                    | Covers                                                        | Extra fields                           |
|----------------------------------|---------------------------------------------------------------|----------------------------------------|
| `SessionStatusChangedMessage`    | All simple transitions (recording, processing, reset, upload) | none — `{sessionId, type, occurredAt}` |
| `SessionProcessingFailedMessage` | `FAILED` transition                                           | `reason` (non-nullable)                |
| `SessionStoryGeneratedMessage`   | `STORY_GENERATED`                                             | `storyId`                              |

This avoids both a single class with `@Nullable` fields (smell) and 10 nearly-identical classes
with the same three fields. Each class has a distinct payload structure.

### File locations

```
application/notification/
  RecordingNotificationListener.java    ← recording events (start, pause, resume, stop, reset)
  ProcessingNotificationListener.java   ← AI processing events (upload, processing, completed, failed)
  StoryNotificationListener.java        ← story generation events
  SessionTopics.java                    ← central topic builder (not wire format)

interfaces/notification/
  SessionEventType.java                 ← wire format discriminator (sent to client)
  messages/
    SessionRealtimeMessage.java         ← sealed contract (same package as permits classes)
    SessionStatusChangedMessage.java    ← payload for simple status transitions
    SessionProcessingFailedMessage.java ← payload for FAILED (carries reason)
    SessionStoryGeneratedMessage.java   ← payload for STORY_GENERATED (carries storyId)
  mappers/
    SessionNotificationMapper.java      ← domain event → message, static methods
```

**Why three listeners instead of one:** listeners are grouped by domain area (recording, processing,
story). When a specific area needs different logic (retry strategy, extra repository calls, conditional
routing), that change touches only the relevant listener. The split criterion is *different logic*,
not *number of events*. All three listeners follow the same `broadcast()` helper pattern; when
that ceases to be true for a specific handler, splitting is natural.

**Why not one listener per event:** 10 classes with one `@ApplicationModuleListener` method each
is more ceremony than value when all handlers do the same thing. It adds navigation overhead without
improving cohesion.

**Why `SessionRealtimeMessage` is in `messages/`:** Java sealed classes in an unnamed module
require the sealed interface and all its permitted types to be in the same package.

**Why payload classes are in `interfaces/`:** they are wire format DTOs (the JSON the client
receives), analogous to `interfaces/rest/dto/response/`. Listeners are in `application/` because
they listen to domain events — their role is application-layer orchestration, not transport.

**Why `SessionTopics` stays in `application/`:** it is routing logic used by the listeners, not
a wire format artifact. The client never sees a `SessionTopics` value directly.

### Events covered

| Domain event                               | Transition                         | `SessionEventType`    | Message class                    |
|--------------------------------------------|------------------------------------|-----------------------|----------------------------------|
| `DiscoverySessionRecordingStartedEvent`    | `DRAFT → RECORDING`                | `RECORDING_STARTED`   | `SessionStatusChangedMessage`    |
| `DiscoverySessionRecordingPausedEvent`     | `RECORDING → PAUSED`               | `RECORDING_PAUSED`    | `SessionStatusChangedMessage`    |
| `DiscoverySessionRecordingResumedEvent`    | `PAUSED → RECORDING`               | `RECORDING_RESUMED`   | `SessionStatusChangedMessage`    |
| `DiscoverySessionRecordingStoppedEvent`    | `RECORDING/PAUSED → STOPPED`       | `RECORDING_STOPPED`   | `SessionStatusChangedMessage`    |
| `DiscoverySessionResetEvent`               | `COMPLETED/FAILED/STOPPED → DRAFT` | `SESSION_RESET`       | `SessionStatusChangedMessage`    |
| `DiscoverySessionTranscriptUploadedEvent`  | `DRAFT → STOPPED` (file upload)    | `TRANSCRIPT_UPLOADED` | `SessionStatusChangedMessage`    |
| `DiscoverySessionProcessingStartedEvent`   | `STOPPED/FAILED → PROCESSING`      | `PROCESSING`          | `SessionStatusChangedMessage`    |
| `DiscoverySessionProcessingCompletedEvent` | `PROCESSING → COMPLETED`           | `COMPLETED`           | `SessionStatusChangedMessage`    |
| `DiscoverySessionProcessingFailedEvent`    | `PROCESSING → FAILED`              | `FAILED`              | `SessionProcessingFailedMessage` |
| `UserStoryCreatedEvent` (session-scoped)   | story generated                    | `STORY_GENERATED`     | `SessionStoryGeneratedMessage`   |

## Consequences

**Positive**
- Handlers stay clean — no notification concern leaks in.
- WS delivery is guaranteed to happen *after* commit — no phantom notifications.
- Adding a new notification target means adding a new `@ApplicationModuleListener`, not editing handlers.
- `SessionTopics` centralizes the topic string — a route change requires editing one file.
- Message grouping by structure eliminates `@Nullable` fields and avoids redundant identical classes.
- `SessionNotificationMapper` centralizes all event→message mapping — one place to change if a
  message payload evolves.

**Negative**
- Notifications are delivered asynchronously — small window between HTTP 200 and the WS message.
  Acceptable; the REST response already carries the new state.
- `DiscoverySessionNotificationListener` (application layer) imports from `interfaces/notification/`
  (interfaces layer). Accepted trade-off: the listener IS an outbound adapter.
- Java unnamed module constraint forces `SessionRealtimeMessage` into the `messages/` package
  alongside its permitted types rather than at the `interfaces/notification/` root.

**Neutral**
- The `@ApplicationModuleListener` pattern is already established for processing events. This ADR
  extends the same pattern to recording events.
