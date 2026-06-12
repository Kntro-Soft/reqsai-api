# Local & cloud AI — provider-swappable setup

Reqs-AI's intelligence lives in the **`discovery`** bounded context. Per the product report it needs
**three independent AI capabilities** — and they're separate ports, so each one can run locally or in
the cloud, in any combination:

| Capability                | What it does in Reqs-AI                                              | Spring AI abstraction      |
|---------------------------|----------------------------------------------------------------------|----------------------------|
| **Generation (LLM/chat)** | suggest user stories, propose edits, detect edge cases, Gherkin AC   | `ChatClient` / `ChatModel` |
| **Embeddings (768-dim)**  | semantic search over stories, dedup, enrich generation (pgvector)    | `EmbeddingModel`           |
| **Speech-to-Text (STT)**  | incremental live transcription + diarization of elicitation sessions | `AudioTranscriptionModel`  |

## The principle (how real projects do it)

Don't bake a provider into the code, and **don't create a profile per provider combination** (that
explodes combinatorially). Instead:

1. **Program against the Spring AI abstraction** (`ChatClient`, `EmbeddingModel`,
   `AudioTranscriptionModel`) — never a concrete vendor type.
2. **Pick the provider per capability with one property each** — `spring.ai.model.chat`,
   `spring.ai.model.embedding`, `spring.ai.model.audio.transcription`.
3. **Profiles set sensible defaults**; individual env vars override one capability when you want to
   **mix** (e.g. local LLM but cloud STT). Same jar, no rebuild.

This gives you full variety from three switches rather than a maze of profiles.

## Provider options per capability

| Capability | Local (free, offline)                                                      | Cloud (managed)                                      |
|------------|----------------------------------------------------------------------------|------------------------------------------------------|
| Generation | **Ollama** — `llama3.1:8b`, `qwen2.5:7b/14b`                               | **Gemini** `gemini-2.0-flash` (prod), OpenAI, Claude |
| Embeddings | **Ollama** — `nomic-embed-text` (**768-dim**)                              | **Gemini** `text-embedding-004` (**768-dim**)        |
| STT        | **Whisper** via an OpenAI-compatible server (faster-whisper / WhisperLive) | OpenAI `whisper-1`, Google STT, Deepgram, AssemblyAI |

> **The one rule that keeps swaps painless:** the **embedding dimension is baked into the pgvector
> column**. Gemini `text-embedding-004` → **768**; Ollama `nomic-embed-text` → **768**. They match, so
> the same `vector(768)` column + HNSW index serve both and you never re-embed when switching. Re-check
> any new embedding model's dimension — a mismatch silently breaks similarity search. (The report fixes
> the `embedding` column at **768d**.)

---

## 1. Install the local engines

### Ollama (generation + embeddings) — Mac / Windows / Linux

| OS                     | Install                                                                         |
|------------------------|---------------------------------------------------------------------------------|
| **macOS** (Apple sil.) | `brew install ollama` — uses the **Metal** GPU automatically                    |
| **Windows**            | Download the installer from ollama.com (native; uses NVIDIA CUDA if present)    |
| **Linux**              | `curl -fsSL https://ollama.com/install.sh \| sh` (NVIDIA/AMD GPU auto-detected) |

```bash
ollama serve                         # daemon at http://localhost:11434 (or the menubar/tray app)
ollama pull llama3.1:8b              # generation
ollama pull nomic-embed-text         # embeddings (768-dim)
ollama run llama3.1:8b "say hi"      # smoke test
```

**Sizing the chat model** (quantized `q4`, leave headroom for Postgres + JVM + frontend):

| RAM / VRAM | Chat model                   | ~Model RAM | Notes                                |
|------------|------------------------------|------------|--------------------------------------|
| 16 GB      | `llama3.1:8b` / `qwen2.5:7b` | 5–6 GB     | fine alongside the rest of the stack |
| 24–32 GB   | `qwen2.5:14b`                | 9–10 GB    | better reasoning, still comfortable  |
| 36 GB+     | `qwen2.5:32b` (q4)           | 19–22 GB   | closest to cloud quality locally     |

Embeddings (`nomic-embed-text`, ~300 MB) stay loaded always. On an Apple-silicon Pro/Max or an
NVIDIA card you can run a 14B chat model **and** embeddings **and** the full stack at once.

Ollama is **not** in `compose.yaml` on purpose: on macOS it must run natively to use the Metal GPU (a
Mac container is CPU-only). On **Linux/Windows with an NVIDIA GPU** you may prefer it containerized —
add a service yourself and point the app at it with `OLLAMA_BASE_URL=http://ollama:11434`:

```bash
docker run -d --name ollama --gpus all -p 11434:11434 -v ollama:/root/.ollama ollama/ollama
```

### Local STT (Whisper) — Docker Compose `ai` profile

Ollama doesn't do speech, so STT runs as a separate service. It's wired into `compose.yaml` under an
**opt-in `ai` profile** (`whisper`, image [`hwdsl2/whisper-server`](https://github.com/hwdsl2/docker-whisper)
— [`faster-whisper`](https://github.com/SYSTRAN/faster-whisper) behind an **OpenAI-compatible**
`/v1/audio/transcriptions` endpoint, multi-arch so it runs on Apple Silicon, with optional diarization):

```bash
docker compose --profile core --profile ai up -d     # Postgres + Mailpit + Whisper

# smoke test (listens on :9000)
curl -F file=@meeting.wav http://localhost:9000/v1/audio/transcriptions
```

> **Not working on AI? Just omit the `ai` profile** — `docker compose --profile core up -d` is the
> normal dev stack. Nothing breaks without Whisper/Ollama: the app's AI beans are lazy and only invoked
> once `discovery` is built. This keeps the day-to-day setup light for everyone else.

Tune via env (defaults in `compose.yaml`): `WHISPER_MODEL` (`base`…`large-v3-turbo`), `WHISPER_LANGUAGE`
(`es`/`auto`), `WHISPER_DIARIZATION=true` for speaker labels. GPU: macOS uses CPU (fine for `base`/`small`);
on Linux/Windows + NVIDIA use the `hwdsl2/whisper-server:cuda` image + `gpus: all`. For **streaming /
near-live** transcription (the WebSocket capture flow), use
[`WhisperLive`](https://github.com/collabora/WhisperLive) or a streaming cloud API instead of batch — see §3.

---

## 2. Select providers — profiles + per-capability switches

**Dependencies & where it lives.** The Gemini and Ollama starters are already on the classpath
(`build.gradle.kts`); only `spring-ai-starter-model-openai` (for STT) is added later when `discovery`
wires transcription. The base config is in `application.yml` under `spring.ai.*` — the `model.chat`/
`model.embedding` switches (default `ollama`, env-overridable), the `ollama` block (`base-url`, models,
`pull-model-strategy: never` so models are pulled out-of-band, not at boot), the `google.genai` block
(key + models), and `vectorstore.pgvector` (HNSW, cosine, **768** dimensions). `application-prod.yml`
flips the switches to `google-genai`. No manual chat/embedding autoconfig excludes are needed — the
switch keeps the unused provider dormant; only `PgVectorStoreAutoConfiguration` stays excluded until
`discovery` persists embeddings (tests run on plain `postgres:16`, no vector extension). Tweak by env,
not by editing files per environment.

### Recipe A — everything local (`dev`, offline, free)

The chat + embedding half is **already in `application.yml`** (the `dev` default selects `ollama`). The
`audio.transcription` / `openai` block is the **STT add-on** `discovery` will introduce. Effective config:

```yaml
spring:
  ai:
    model:
      chat: ollama
      embedding: ollama
      audio:
        transcription: openai        # talks to the LOCAL OpenAI-compatible Whisper server
    ollama:
      base-url: http://localhost:11434
      init: { pull-model-strategy: when_missing }
      chat:      { options: { model: llama3.1:8b } }
      embedding: { options: { model: nomic-embed-text } }   # 768-dim
    openai:
      base-url: http://localhost:9000     # local Whisper server (compose `ai` profile)
      api-key: not-needed                 # local server ignores it
      audio:
        transcription:
          options: { model: whisper-1 }
    vectorstore:
      pgvector: { initialize-schema: true, dimensions: 768 }
```

### Recipe B — everything cloud (`prod`, or `dev` for cloud testing)

`application-prod.yml` already sets `spring.ai.model.chat`/`embedding: google-genai` (key from
`GEMINI_API_KEY`). The STT line is the future add-on. Effective config:

```yaml
spring:
  ai:
    model:
      chat: google-genai
      embedding: google-genai
      audio: { transcription: openai }    # real OpenAI Whisper API (or swap to Google STT)
    google:
      genai:
        api-key: ${GEMINI_API_KEY}
        chat:      { options: { model: ${GEMINI_CHAT_MODEL:gemini-2.0-flash} } }
        embedding: { options: { model: ${GEMINI_EMBEDDING_MODEL:text-embedding-004} } }  # 768-dim
    openai:
      api-key: ${OPENAI_API_KEY}
      audio: { transcription: { options: { model: whisper-1 } } }
    vectorstore:
      pgvector: { dimensions: 768 }
```

### Recipe C — mix (e.g. local LLM, cloud STT) — no new profile

Start with Recipe A and override just one capability via env:

```bash
SPRING_AI_MODEL_AUDIO_TRANSCRIPTION=openai \
SPRING_AI_OPENAI_BASE_URL=https://api.openai.com \
SPRING_AI_OPENAI_API_KEY=sk-... \
./gradlew bootRun
```

That's the payoff of per-capability switches: any combination from three knobs.

---

## 3. Provider-agnostic code

Inject the abstraction; the same class runs on local or cloud:

```java
@Service
class StoryGenerator {
    private final ChatClient chat;
    private final EmbeddingModel embeddings;
    StoryGenerator(ChatClient.Builder b, EmbeddingModel e) { this.chat = b.build(); this.embeddings = e; }

    UserStory draft(String transcript) {
        String json = chat.prompt().user(transcript).call().content();   // Ollama or Gemini
        float[] vec = embeddings.embed(json);                            // 768-dim either way
        // ... persist + pgvector similarity search for dedup
    }
}

@Service
class Transcriber {
    private final AudioTranscriptionModel stt;                           // local Whisper or cloud
    Transcriber(AudioTranscriptionModel stt) { this.stt = stt; }
    String transcribe(Resource audio) { return stt.call(new AudioTranscriptionPrompt(audio)).getResult().getOutput(); }
}
```

For the **live** WebSocket capture flow, chunk the incoming audio and transcribe segments incrementally
(batch Whisper per chunk), or use a streaming STT (WhisperLive locally / Deepgram / Google streaming in
cloud) and push `TranscriptSegment`s over STOMP — see [REALTIME.md](REALTIME.md).

---

## 4. Run the full stack locally

```bash
docker compose --profile core --profile ai up -d    # 1. Postgres/pgvector + Mailpit + Whisper (STT)
ollama serve                                         # 2. LLM + embeddings (native; models pulled)
./gradlew bootRun                                    # 3. backend (dev → all local)
cd ../reqsai-web && npm start                        # 4. frontend (http://localhost:4200)
```

(Not doing AI work? Drop `--profile ai` and skip step 2 — the rest runs the same.)

Everything offline and free for development; flip to cloud with `--spring.profiles.active=prod` and the
API keys. Behaviour stays consistent because the abstractions and the 768-dim vector column are shared.

## Tips

- **Keep models warm**: Ollama unloads idle models — `OLLAMA_KEEP_ALIVE=-1` keeps them resident during a
  dev session.
- **Determinism in tests**: pin `temperature: 0.0` for AI ITs, or **stub the model beans** — never call
  a live model (local or cloud) from CI.
- **Verify dimensions** on any embedding-model change; mismatch with the pgvector column fails the index
  or returns silently wrong results.
- **Secrets**: `GEMINI_API_KEY` / `OPENAI_API_KEY` are env/secrets, exactly like the JWT signing key —
  never commit them.
