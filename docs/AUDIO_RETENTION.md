# Audio retention and purge (US15 / US69)

Each organization sets how long its audios are kept (`audio_retention_days` on `public.organizations`,
US15): `N` = days after the recording was stored, `0` = do not keep it, `-1` = keep it forever. A scheduled
job deletes the audios that outlived that period (US69). The transcript, the transcript segments and the
stories of the session are never touched.

## What is stored

- An **uploaded recording** (`POST /api/sessions/{id}/upload`) is kept in `session_audio_contents` (tenant
  schema, `bytea`, apart from the session row so listing sessions never reads it) and the session gets
  `audio_stored_at`. With a retention of `0`, or when the policy cannot be read, nothing is stored.
- A **live capture** is streamed to the transcriber and never stored, so it has nothing to purge.
- The session exposes `audioState` (`NONE`, `STORED`, `PURGED`) and `audioPurgedAt`; the web shows
  "audio deleted" instead of the recording once it is `PURGED`.
- The bytes sit behind `AudioStoragePort` (`DatabaseAudioStorageAdapter`); swap the adapter to move them to
  an object store without touching the use cases.

## The purge

`AudioRetentionPurgeScheduler` calls `PurgeExpiredAudiosService`, which:

1. Reads every organization's policy through `WorkspaceModuleApi.listAudioRetentionPolicies()` (a scheduled
   run has no request, so no tenant is bound).
2. Skips organizations that keep audios forever (`-1`).
3. For each other tenant, runs inside `TenantContext.runWith(...)` so every query lands in its
   `tenant_<slug>` schema, and deletes in batches the audios stored at or before `now - retentionDays`.
4. Never deletes the audio of a session in progress (`RECORDING`, `PAUSED`, `PROCESSING`); it becomes
   eligible on the next run once the session settles.

It is idempotent (a purged audio stops being a candidate), isolates a failing tenant from the others and logs
only counts and tenant ids. Lowering an organization's retention purges the audios that are now older than
the new period on the next run.

## Configuration

| Property (`reqsai.discovery.audio-retention.*`) | Env var                              | Default        |
|-------------------------------------------------|--------------------------------------|----------------|
| `enabled`                                       | `AUDIO_RETENTION_PURGE_ENABLED`      | `true`         |
| `cron` (UTC)                                    | `AUDIO_RETENTION_PURGE_CRON`         | `0 30 3 * * *` |
| `batch-size`                                    | `AUDIO_RETENTION_PURGE_BATCH_SIZE`   | `200`          |

The `test` profile disables the schedule (the integration test calls the service directly) and uses a batch
size of 2 so batching is exercised.
