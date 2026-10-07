# WebSocket STT — /ws/stt

Live speech-to-text over a binary WebSocket. The client streams raw PCM audio frames;
the server forwards them to the configured STT provider (Deepgram by default) and persists
each transcript segment via `AppendTranscriptSegmentCommandHandler`.

---

## Endpoint

```
ws://<host>/ws/stt?session=<SESSION_UUID>&token=<JWT>
```

| Parameter | Description                                                                                                                                |
|-----------|--------------------------------------------------------------------------------------------------------------------------------------------|
| `session` | UUID of a `DiscoverySession` in **RECORDING** status. The server closes with `1008 Policy Violation` if the session is in any other state. |
| `token`   | JWT bearer token. See [Why query param?](#why-query-param) below.                                                                          |

---

## REST lifecycle

Before opening the WebSocket, the session must be in **RECORDING** status. Use these REST
endpoints to drive the lifecycle. All require `Authorization: Bearer <token>` and `Api-Version: 1`.

```
POST /api/projects/:projectId/sessions                     → DRAFT
POST /api/projects/:projectId/sessions/:id/start           → RECORDING   (open WS here)
POST /api/projects/:projectId/sessions/:id/pause           → PAUSED      (WS closes automatically)
POST /api/projects/:projectId/sessions/:id/resume          → RECORDING   (open a new WS)
POST /api/projects/:projectId/sessions/:id/stop            → STOPPED     (WS closes automatically)
```

State machine:

```
                ┌─────────────────────────────────────────────────────┐
                │                                                     │
 POST /start    │  POST /pause          POST /resume                  │
DRAFT ─────▶ RECORDING ─────────▶ PAUSED ───────────────▶ RECORDING  │
                │                                                     │
                │  POST /stop                       POST /stop        │
                └──────────────────────────────▶ STOPPED ◀───────────┘
```

---

## WebSocket session lifecycle

### Normal flow (start → stream → stop)

```
Client                     Server                        STT Provider
  │                           │                               │
  │  POST .../start           │                               │
  │──────────────────────────▶│ (session → RECORDING)         │
  │                           │                               │
  │  GET /ws/stt?session=…    │                               │
  │──────────────────────────▶│── open provider stream ──────▶│
  │◀─── 101 Switching ────────│                               │
  │                           │                               │
  │── binary PCM frames ─────▶│── binary PCM frames ─────────▶│
  │                           │◀── transcript JSON ───────────│
  │                           │   AppendTranscriptSegment      │
  │                           │   → segment persisted to DB    │
  │                           │                               │
  │  POST .../stop            │                               │
  │──────────────────────────▶│ (session → STOPPED)           │
  │                           │  SttSessionLifecycleListener   │
  │◀─── 1000 "session stopped"│── sendClose ─────────────────▶│
```

### Pause / resume flow

```
Client                     Server                        STT Provider
  │                           │                               │
  │── binary PCM frames ─────▶│── forwarding ────────────────▶│
  │                           │                               │
  │  POST .../pause           │                               │
  │──────────────────────────▶│ (session → PAUSED)            │
  │                           │  SttSessionLifecycleListener   │
  │◀─── 1000 "session paused" │── sendClose ─────────────────▶│
  │                           │                               │
  │      ··· (paused) ···     │                               │
  │                           │                               │
  │  POST .../resume          │                               │
  │──────────────────────────▶│ (session → RECORDING)         │
  │                           │                               │
  │  GET /ws/stt?session=…    │  ← client must reconnect      │
  │──────────────────────────▶│── open new provider stream ──▶│
  │◀─── 101 Switching ────────│                               │
  │                           │                               │
  │── binary PCM frames ─────▶│── forwarding ────────────────▶│
```

**Key point**: the server never reopens the WebSocket on resume — the client is responsible for
opening a new connection after `POST .../resume`. The server accepts it because the session is
back in RECORDING status.

### Provider drops the stream (keepalive and reconnect)

The provider connection behind `/ws/stt` can drop while the client keeps recording. Deepgram,
for example, closes a stream after 10 s without audio (`NET-0001`), and a long JVM pause is
enough to cause that. The server handles it without the client noticing:

- **Keepalive.** While the provider socket is idle, the server sends the provider's keepalive
  every 4 s. Deepgram gets `{"type":"KeepAlive"}` as a text frame (its docs recommend every
  3–5 s). AssemblyAI needs none, because it only ends idle sessions when `inactivity_timeout`
  is set, and we don't set it. WhisperLive needs none either.
- **One frame at a time.** Frames go to the provider one at a time, because the JDK WebSocket
  rejects a send while another is pending. A failed send counts as a broken stream.
- **Reconnect.** When the provider closes the stream or fails, or a send fails, the server logs a
  WARN with the session id and close code. It then opens a new provider connection after 0.5 s,
  1 s and 2 s (3 attempts). While it reconnects, it buffers up to 30 s of audio, dropping the
  oldest audio first, and sends that audio on the new connection. WhisperLive gets its config
  handshake again before any audio.
- **Transcript continuity.** Provider offsets restart at 0 on each connection, so the server adds
  the duration of the audio that came before it. `startMs`/`endMs` stay on one timeline, and
  segment order is unchanged.
- **Giving up.** If all 3 attempts fail, the server closes the client socket with
  `1011` / `transcription stream lost`. The client shows its "connection closed" error, instead
  of recording audio that nobody transcribes. The attempt budget refills once a new connection
  delivers a transcript. So a provider that accepts the connection and then drops it straight
  away cannot cause an endless loop of reconnects.
- **Cleanup.** Pausing, stopping or disconnecting cancels the keepalive and any pending
  reconnect, and closes the provider socket.

The policy values live in `StreamResilience` (provider adapters, `streaming/strategy`).

---

## Audio format

The server expects raw PCM audio. **Do not send a WAV/MP3/OGG container** — the bytes
go directly to the STT provider.

| Parameter   | Value                                         |
|-------------|-----------------------------------------------|
| Encoding    | `linear16` (signed 16-bit PCM, little-endian) |
| Sample rate | `16 000 Hz`                                   |
| Channels    | `1` (mono)                                    |

```bash
# From any format → raw PCM
ffmpeg -i input.mp3 -ar 16000 -ac 1 -f s16le output.raw

# Record from microphone (macOS, 10 s)
rec -r 16000 -c 1 -e signed-integer -b 16 -t raw /tmp/mic.raw trim 0 10
```

---

## Chunk (frame) size

Send audio in chunks. The recommended chunk size is **4 096 bytes** (128 ms at 16 kHz/16-bit/mono).

| Provider        | Min recommended  | Max safe frame  | Notes                                                               |
|-----------------|------------------|-----------------|---------------------------------------------------------------------|
| **Deepgram**    | 100 ms (3 200 B) | ~1 MB           | Smaller chunks reduce latency                                       |
| **AssemblyAI**  | 100 ms (3 200 B) | 1 MB            | Recommends 100 ms chunks                                            |
| **WhisperLive** | 250 ms (8 000 B) | ~1 MB per frame | Processes in 30-second windows; higher latency than cloud providers |

The Spring buffer is set to **512 KB** (`setMaxBinaryMessageBufferSize`). Never send the
entire file as a single frame — always stream in chunks.

---

## Testing with scripts

The scripts are bash scripts and run on **macOS**, **Linux**, and **Windows via Git Bash**.

| Tool            | macOS                   | Linux                    | Windows (Git Bash)                                                              |
|-----------------|-------------------------|--------------------------|---------------------------------------------------------------------------------|
| `websocat`      | `brew install websocat` | `cargo install websocat` | [GitHub releases](https://github.com/vi/websocat/releases) `.exe` — add to PATH |
| `curl` / `jq`   | pre-installed / `brew`  | `apt install curl jq`    | included in Git Bash                                                            |
| `pv` (optional) | `brew install pv`       | `apt install pv`         | not needed — Python 3 fallback used instead                                     |
| `python3`       | pre-installed           | pre-installed            | [python.org](https://www.python.org/downloads/) — used as rate-limit fallback   |

### Interactive controller — `ws-stt-test.sh`

Full lifecycle in one interactive terminal UI. Press keys without Enter.

```bash
export REQSAI_TOKEN="eyJhbG..."
export REQSAI_PROJECT_ID="019ee12b-..."

# Without audio (lifecycle-only test)
./scripts/ws-stt-test.sh

# With an audio file
./scripts/ws-stt-test.sh --audio /tmp/audio_5min_en.raw --lang en

# With real-time pause: audio position advances while paused (simulates real recording gap)
./scripts/ws-stt-test.sh --audio /tmp/audio_5min_en.raw --realtime-pause

# Attach to an existing session (skips create + start)
./scripts/ws-stt-test.sh --session <SESSION_ID> --audio /tmp/audio.raw
```

| Key | Action                                              |
|-----|-----------------------------------------------------|
| `s` | Create session + start recording + begin streaming  |
| `p` | Pause — stops streaming, server closes WS           |
| `r` | Resume — reopens WS, continues from paused position |
| `x` | Stop — session permanently closed                   |
| `v` | Verify — show saved transcript segments from DB     |
| `q` | Quit — stops session if active                      |

### Lifecycle scripting — `ws-stt-session.sh`

For CI or automated scripts that need to drive the lifecycle without interaction.

```bash
export REQSAI_TOKEN="eyJhbG..."
export REQSAI_PROJECT_ID="019ee12b-..."

SESSION=$(./scripts/ws-stt-session.sh create)
./scripts/ws-stt-session.sh start  "$SESSION"
./scripts/ws-stt-session.sh pause  "$SESSION"
./scripts/ws-stt-session.sh resume "$SESSION"
./scripts/ws-stt-session.sh stop   "$SESSION"
```

Each command prints the action taken and the confirmed new state from the server.

---

## Error close codes

| Code                    | Reason                          | Cause                                                 |
|-------------------------|---------------------------------|-------------------------------------------------------|
| `1002 Protocol Error`   | missing/invalid `session` param | `session` absent or not a UUID                        |
| `1008 Policy Violation` | session not in RECORDING status | Session is DRAFT / PAUSED / STOPPED                   |
| `1000 Normal`           | session paused                  | `DiscoverySessionRecordingPausedEvent` fired          |
| `1000 Normal`           | session stopped                 | `DiscoverySessionRecordingStoppedEvent` fired         |
| `1009 Message Too Big`  | audio frame > 512 KB            | Client sent a frame larger than the configured buffer |
| `1006 Abnormal`         | provider unreachable            | Network error connecting to the STT provider          |
| `1011 Server Error`     | transcription stream lost       | Provider stream dropped and 3 reconnect attempts failed |

---

## Why query param?

The browser `WebSocket` API does **not** allow setting custom HTTP headers during the
handshake. The JWT is therefore passed as `?token=`, which `WebSocketJwtHandshakeInterceptor`
validates before the connection is established.

```js
// ✅ Works in browser
const ws = new WebSocket(`wss://api.example.com/ws/stt?session=${id}&token=${jwt}`)

// ❌ Not possible — browser WebSocket has no header support
new WebSocket(url, { headers: { Authorization: `Bearer ${jwt}` } })
```

For production hardening, issue a short-lived WS ticket instead of sending the full JWT in
the URL (the ticket is a single-use nonce that expires in 30 seconds):

```
POST /api/ws/ticket    Authorization: Bearer <long-lived JWT>
← { "ticket": "<nonce>", "expiresIn": 30 }

ws://.../ws/stt?session=…&ticket=<nonce>
```

---

## STOMP subscription (live transcript events)

The server publishes each persisted segment as a STOMP message. Subscribe over the main
STOMP WebSocket (`/ws`):

```
/topic/discovery/sessions/<SESSION_UUID>/segments
```

A typical UI opens both connections: the binary `/ws/stt` for sending audio, and the STOMP
subscription for receiving live transcript updates.
