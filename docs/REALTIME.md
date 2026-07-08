# Realtime (WebSocket / STOMP)

Reqs-AI pushes live updates (e.g. a discovery capture session) over **STOMP-over-WebSocket**. Business
code never touches the messaging API — it depends on the `RealtimeNotifier` **port** in
`shared.application.notification`; the `StompRealtimeNotifier` adapter does the rest. The broker is
**switchable** (in-memory for one instance, external relay for many) by configuration only.

```
┌─ bounded context (e.g. discovery) ─┐      ┌─ shared/infrastructure ───────────────┐
│  inject RealtimeNotifier           │      │  StompRealtimeNotifier (adapter)       │
│  notifier.send("/topic/…", payload)│ ───▶ │   → SimpMessagingTemplate              │
└────────────────────────────────────┘      │   → broker: SIMPLE (in-mem) | RELAY    │
        depends ONLY on the port             └────────────────────────────────────────┘
```

## 1. Emit from the server

Inject the port and send. Payloads are serialized to JSON automatically; a failed send is logged, never
thrown, so it can't break the caller's transaction.

```java
@Service
@RequiredArgsConstructor
class CaptureSessionService {

    private final RealtimeNotifier notifier;

    void onRequirementAdded(UUID sessionId, RequirementView req) {
        notifier.send("/topic/sessions/" + sessionId, req);          // broadcast to subscribers
        notifier.sendToUser(userId.toString(), "/queue/alerts", a);  // one user's private queue
        notifier.broadcast("sessions/" + sessionId, req);            // shortcut for /topic/<x>
    }
}
```

## 2. Receive client → server messages (optional)

Only needed if clients send messages (not just subscribe). A `@Controller` with `@MessageMapping`:

```java
@Controller
class TypingController {
    @MessageMapping("/sessions/{id}/typing")  // client sends to /app/sessions/{id}/typing
    @SendTo("/topic/sessions/{id}")           // re-broadcast to subscribers
    TypingEvent typing(@DestinationVariable UUID id, Principal user) {
        return new TypingEvent(id, user.getName());
    }
}
```

## 3. Connect from the client

The handshake hits `/ws` (`reqsai.websocket.endpoint`). Authentication happens on the **STOMP CONNECT
frame**, not the HTTP handshake: send the JWT as a native `Authorization` header.

```javascript
import { Client } from '@stomp/stompjs';

const client = new Client({
  brokerURL: 'ws://localhost:8080/ws',
  connectHeaders: { Authorization: `Bearer ${accessToken}` }, // verified on CONNECT
});

client.onConnect = () => {
  client.subscribe('/topic/sessions/123', (msg) => console.log(JSON.parse(msg.body)));
  client.subscribe('/user/queue/alerts',  (msg) => console.log('private', JSON.parse(msg.body)));
};
client.activate();
```

## Authentication flow

1. The HTTP handshake to `/ws/**` is **permitted** in `SecurityConfiguration` (no token yet).
2. The client sends `Authorization: Bearer <jwt>` as a native STOMP header on **CONNECT**.
3. `StompAuthChannelInterceptor` verifies it with the same `TokenVerifier` as the HTTP filter and binds
   the user `Principal` to the CONNECT frame's accessor, and — separately — stashes `userId`/`orgId` in
   the STOMP **session attributes**.
4. CONNECT with no token → anonymous; with an invalid token → rejected (the verifier throws).

> **The `Principal` does not persist past CONNECT.** Empirically (verified against a running instance),
> a later frame's `accessor.getUser()` on the same STOMP session comes back `null` — only the session
> attributes carry over to every subsequent frame. Anything that needs the caller's identity outside the
> CONNECT handler itself (a session-lifecycle listener, a future `@MessageMapping` handler) must read
> `StompAuthChannelInterceptor.USER_ID_ATTRIBUTE`/`ORG_ID_ATTRIBUTE` from `accessor.getSessionAttributes()`,
> not `accessor.getUser()`. This is why presence resolves identity this way (see below). `sendToUser`
> is expected to be unaffected — Spring resolves it via a username registry populated from the CONNECT
> frame itself, not by re-reading `accessor.getUser()` on later frames — but it has no caller in this
> codebase yet, so that has not been directly exercised.

## Destination prefixes

| Prefix   | Direction         | Use                                                   |
|----------|-------------------|-------------------------------------------------------|
| `/app`   | client → server   | routed to your `@MessageMapping` handlers             |
| `/topic` | server → clients  | broadcast (many subscribers)                          |
| `/user`  | server → one user | per-user queue (`sendToUser`, resolved per principal) |

## Live presence (who is viewing a session)

Discovery tracks who is currently viewing a **live** session and broadcasts the roster so every
participant sees the others. It is built entirely on the STOMP lifecycle — there is **no** extra
subscription and **no** client→server message:

- **Signal:** a client's SUBSCRIBE to `/topic/sessions/{id}` *is* "I am present". `SessionPresenceTracker`
  listens to `SessionSubscribeEvent` / `SessionUnsubscribeEvent` / `SessionDisconnectEvent`.
- **State:** `SessionPresenceRegistry` holds, per session, which connections are present (a user across
  two tabs counts once). It is in-process — **not** Redis — and, like the `SIMPLE` broker, per-JVM.
- **Broadcast:** on any real roster change the tracker sends a `PRESENCE_STATE` message
  (`SessionPresenceMessage`: full participant snapshot + `count`) back on the same `sessions/{id}` topic.
  The client keeps its one subscription and switches on `type` (see the single-topic pattern below).
- **Identity:** the CONNECT interceptor stashes `userId` and `orgId` in the STOMP session attributes
  (see the callout above — not the frame `Principal`); the tracker resolves each `userId` to a display
  name via `WorkspaceModuleApi.findMemberDisplayName` (Caffeine-cached) and a deterministic `avatarUrl`
  (`/api/users/{userId}/avatar`).

> Multi-instance caveat: because the registry is per-JVM (same as the `SIMPLE` broker), a global roster
> across several ECS tasks needs the shared `RELAY` broker's state — acceptable at single-instance scale.

## Scaling: SIMPLE vs. RELAY (important for ECS)

The default **`SIMPLE`** broker is in-memory and only knows connections on the **local JVM**. With more
than one instance (several ECS Fargate tasks), a broadcast from instance A never reaches clients on
instance B. For multi-instance production, switch to an external STOMP broker — **no code change**:

```yaml
reqsai:
  websocket:
    broker:
      mode: RELAY                 # default is SIMPLE
      host: ${MQ_HOST}            # Amazon MQ (RabbitMQ/ActiveMQ), or self-hosted
      port: 61613
      login: ${MQ_USER}
      passcode: ${MQ_PASS}
```

Every instance then relays through the shared broker and fan-out works across all of them. RELAY
requires `io.projectreactor.netty:reactor-netty` on the classpath. See
[ADR-0007](adr/0007-realtime-stomp-switchable-broker.md).

## Configuration reference (`reqsai.websocket.*`)

| Property             | Default                 | Notes                                             |
|----------------------|-------------------------|---------------------------------------------------|
| `endpoint`           | `/ws`                   | STOMP handshake path                              |
| `allowed-origins`    | `http://localhost:4200` | CORS for the handshake                            |
| `topic-prefix`       | `/topic`                | server → client broadcasts                        |
| `application-prefix` | `/app`                  | client → server (`@MessageMapping`)               |
| `user-prefix`        | `/user`                 | per-user destinations                             |
| `enable-sock-js`     | `false`                 | SockJS fallback (native WS only by default)       |
| `broker.mode`        | `SIMPLE`                | `SIMPLE` (in-memory) or `RELAY` (external broker) |

## Testing

Mint a token with `TestJwtFactory` and set it as the CONNECT `Authorization` header to test the
interceptor end-to-end (same throwaway test key as the HTTP filter — see
[CONTRIBUTING](../.github/CONTRIBUTING.md#testing-secured-endpoints)).
