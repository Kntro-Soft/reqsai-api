# 0007. Real-time over STOMP with a switchable broker

- Status: Accepted
- Date: 2026-06-08
- Deciders: Kntro-Soft team

## Context

`discovery` needs real-time updates (live capture sessions). Spring's default in-memory ("simple")
STOMP broker is easy but **only knows local connections**: with more than one instance (we deploy
multiple ECS Fargate tasks behind an ALB), a broadcast on one task never reaches clients connected to
another task. This is the most common production WebSocket failure and was a recurring pain in prior
projects. We also want JWT auth on connections and to avoid coupling bounded contexts to the messaging
infrastructure.

Sources: [Spring STOMP performance/broker-relay docs](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/configuration-performance.html),
[Spring token-based WS auth](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authentication-token-based.html),
[Scaling WebSockets in Spring](https://medium.com/javarevisited/scaling-websockets-in-spring-services-27023f59868c).

## Decision

Use **STOMP over WebSocket** with a **switchable broker** behind config (`reqsai.websocket.broker.mode`):

- **`SIMPLE`** (default) — in-memory broker with heartbeats; for dev and single-instance.
- **`RELAY`** — `enableStompBrokerRelay` to an external STOMP broker (RabbitMQ/ActiveMQ; on AWS,
  **Amazon MQ**); for multi-instance production. No code change, just configuration.

Authentication: a `StompAuthChannelInterceptor` validates the JWT on the CONNECT frame using the same
shared `TokenVerifier` port (verify-once, then the user is bound to the session). Bounded contexts
publish through a single `RealtimeNotifier` port (adapter: `StompRealtimeNotifier`) — no per-context
messaging beans.

## Consequences

- Scales horizontally by flipping one property to `RELAY` (no redeploy of code).
- Dev stays dependency-free (in-memory broker); RELAY adds `reactor-netty` + an external broker.
- Single auth path for HTTP and WebSocket (one `TokenVerifier`).
- Caveats when RELAY is enabled: RabbitMQ destinations use dot-delimited routing keys (avoid extra
  slashes in dynamic destination segments), and `@SubscribeMapping` for broker-relayed destinations is
  bypassed by the relay. Documented for when `discovery` turns it on.
