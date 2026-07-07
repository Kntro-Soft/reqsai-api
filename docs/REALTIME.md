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
   the user `Principal` to the session — so `/user/**` queues and per-message security work.
4. CONNECT with no token → anonymous; with an invalid token → rejected (the verifier throws).

## Destination prefixes

| Prefix   | Direction         | Use                                                   |
|----------|-------------------|-------------------------------------------------------|
| `/app`   | client → server   | routed to your `@MessageMapping` handlers             |
| `/topic` | server → clients  | broadcast (many subscribers)                          |
| `/user`  | server → one user | per-user queue (`sendToUser`, resolved per principal) |

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
