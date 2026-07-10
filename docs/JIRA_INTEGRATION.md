# Jira integration — setup guide

Reqs-AI can push **user stories to Jira Cloud** as issues. The integration lives in the **`gateway`**
bounded context and is designed to be provider-extensible (Jira is the first provider). See
[ADR-0023](adr/0023-third-party-integrations-jira.md) for the design rationale.

- **Connection is organization-level** — credentials are stored once per org, encrypted at rest.
- **Push target is project-level** — each project picks which Jira project + issue type its stories go to.
- **Two ways to connect**, pick either (both can coexist):

| Method              | User experience                               | What you must set up                                    | Best for                                       |
|---------------------|-----------------------------------------------|---------------------------------------------------------|------------------------------------------------|
| **OAuth 2.0 (3LO)** | One click "Connect with Atlassian", no typing | Register an Atlassian app (Client ID/Secret + callback) | The recommended, smoothest flow                |
| **API token**       | Paste site URL + email + API token            | Nothing server-side; each user creates a token          | Quick start, or when you can't register an app |

> The Jira API token / OAuth tokens are **never** stored in the browser and never returned by the API.
> They are encrypted at rest with AES-256-GCM.

---

## 1. Generate the backend secrets

Two secrets are needed. They are **different values in different formats** — do not mix them up.

| Env var                       | Purpose                                  | Format                                                     | Script                                   |
|-------------------------------|------------------------------------------|------------------------------------------------------------|------------------------------------------|
| `INTEGRATIONS_ENCRYPTION_KEY` | Encrypt stored credentials (AES-256-GCM) | **base64**, decodes to 32 bytes (~44 chars, ends with `=`) | `scripts/generate-encryption-key.sh`     |
| `JIRA_OAUTH_STATE_SECRET`     | Sign the OAuth `state` (HMAC-SHA256)     | **hex**, 64 chars (`0-9 a-f`)                              | `scripts/generate-oauth-state-secret.sh` |

Run them in **Git Bash** (they use `openssl`, bundled with Git for Windows):

```bash
bash scripts/generate-encryption-key.sh        # prints INTEGRATIONS_ENCRYPTION_KEY=...
bash scripts/generate-oauth-state-secret.sh    # prints JIRA_OAUTH_STATE_SECRET=...
```

Each prints one line to the terminal — copy it into your `.env`. Nothing is written to the repo.

> **Common mistake:** using the hex value for `INTEGRATIONS_ENCRYPTION_KEY`. A 64-char hex string
> base64-decodes to 48 bytes, and the app fails at startup with
> `INTEGRATIONS_ENCRYPTION_KEY must decode to 32 bytes (AES-256), got 48`. The encryption key **must**
> come from `generate-encryption-key.sh` (base64). Verify with:
> `echo -n "<value>" | base64 -d | wc -c` → must print `32`.

`INTEGRATIONS_ENCRYPTION_KEY` is **required** to run the integration (dev/test carry a default in
`application-dev.yml` / `application-test.yml`). The OAuth vars below are **optional** — without them the
API-token flow still works and the "Connect with Atlassian" button reports *not configured*.

---

## 2. Register the Atlassian OAuth app (only for the OAuth method)

1. Go to **https://developer.atlassian.com/console/myapps** → **Create → OAuth 2.0 integration**.
2. **Name:** e.g. `ReqsAI`. **Access type:** **Resource-level** (least privilege — only the site the
   user selects during authorization). Accept the terms → **Create**.
3. **Permissions → Add → Jira API**, and add these scopes (must match what the backend requests):

   ```
   read:jira-work   write:jira-work   read:jira-user   read:me
   ```

   > `offline_access` is **not** added here — the backend requests it at login time to obtain a refresh
   > token. The full scope string the backend sends is
   > `read:jira-work write:jira-work read:jira-user offline_access read:me`. If the console has *fewer*
   > scopes than this, Atlassian rejects authorization with *invalid scope*.

4. **Authorization → OAuth 2.0 (3LO) → Configure** → set the **Callback URL** (see §3). Save.
5. **Settings → copy the Client ID and Secret** → these become `JIRA_OAUTH_CLIENT_ID` /
   `JIRA_OAUTH_CLIENT_SECRET`.
6. Leave **Distribution = private** for development (only your Atlassian account can authorize it).
   Switch to *Sharing* only when other organizations must connect their own Jira (production) — that
   step asks for vendor name, privacy policy and a personal-data declaration.

### The Callback URL

It is **not** something Atlassian gives you — **you define it**, and it must match, character for
character, both the Atlassian app's *Authorization → Callback URL* and the backend's
`JIRA_OAUTH_CALLBACK_URL`. It is the frontend route that receives the OAuth redirect:

| Environment | Callback URL                                                       |
|-------------|--------------------------------------------------------------------|
| Local dev   | `http://localhost:4200/settings/integrations/jira/callback`        |
| Production  | `https://YOUR-FRONTEND-DOMAIN/settings/integrations/jira/callback` |

You may register **multiple** callback URLs (one per line, up to 30). Order does not matter — the
backend sends the exact `redirect_uri` for its environment, and Atlassian only requires it to be in the
list. If it doesn't match, Atlassian errors with `redirect_uri mismatch`.

---

## 3. Configure `.env`

Add these to the backend `.env` (see `.env.example` for the annotated block):

```dotenv
# Required for the integration (base64, 32 bytes)
INTEGRATIONS_ENCRYPTION_KEY=<from generate-encryption-key.sh>

# Optional — only for the OAuth "Connect with Atlassian" flow
JIRA_OAUTH_CLIENT_ID=<from the Atlassian app Settings>
JIRA_OAUTH_CLIENT_SECRET=<from the Atlassian app Settings>
JIRA_OAUTH_CALLBACK_URL=http://localhost:4200/settings/integrations/jira/callback
JIRA_OAUTH_STATE_SECRET=<from generate-oauth-state-secret.sh>
```

These map to `reqsai.integrations.encryption-key` and `reqsai.integrations.jira.oauth.*`. Restart the
backend after editing `.env`.

---

## 4. Alternative: the API-token method (no app registration)

Each user creates a personal API token:

1. Go to **https://id.atlassian.com/manage-profile/security/api-tokens** → **Create API token**.
2. In Reqs-AI → **Org Settings → Integrations → Jira**, expand *"use an API token"* and enter:
   - **Site URL** — `https://your-space.atlassian.net`
   - **Email** — the Atlassian account email that owns the token
   - **API token** — the value you just created (sent encrypted, never shown again)

---

## 5. Use it

1. **Connect** (once per org): Org Settings → **Integrations** → *Connect with Atlassian* (OAuth) or the
   API-token form. On multi-site Atlassian accounts you pick the site.
2. **Map** (per project): Project Settings → **Integrations** → choose the Jira project + issue type.
3. **Push**: from a story's detail (*Push to Jira*, synchronous) or the backlog (*Push all to Jira*).
   The story title becomes the issue summary; the description carries role/action/benefit + acceptance
   criteria (Given/When/Then).
4. **Import**: from the backlog, preview the eligible Jira issues (duplicates flagged) and import them
   as user stories (LLM mapping + dedup).

**Push-all and import run as background jobs** (Spring Batch): the POST returns `202` with a job
snapshot, progress streams on `/topic/projects/{projectId}/integration-jobs`, and after a reload the
client recovers via `GET .../integration/jira/jobs?active=true` (or `.../jobs/{jobId}`). At most one
running job per type and project (`409 INTEGRATION_JOB_ALREADY_RUNNING`). See ADR-0023, "Async sync
jobs on Spring Batch".

Actions are RBAC-gated by new permissions: `INTEGRATION_READ`, `INTEGRATION_WRITE`, `INTEGRATION_DELETE`,
`INTEGRATION_SYNC`. Org-level connection management requires org owner/admin.

---

## Troubleshooting

| Symptom                                                 | Cause & fix                                                                                                                 |
|---------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| Startup: `...must decode to 32 bytes (AES-256), got 48` | `INTEGRATIONS_ENCRYPTION_KEY` holds a hex value. Regenerate with `scripts/generate-encryption-key.sh` (base64).             |
| Button disabled / `JIRA_OAUTH_NOT_CONFIGURED`           | OAuth env vars are missing/blank. Set `JIRA_OAUTH_CLIENT_ID/SECRET/CALLBACK_URL` and restart.                               |
| Atlassian: `redirect_uri mismatch`                      | The app's Callback URL ≠ `JIRA_OAUTH_CALLBACK_URL`. Make them identical.                                                    |
| Atlassian: `invalid scope`                              | The console has fewer scopes than the backend requests. Add all of `read:jira-work write:jira-work read:jira-user read:me`. |
| `JIRA_AUTH_FAILED` on connect/push                      | Bad API token/email, expired OAuth grant, or the token owner lacks access to the Jira project.                              |
| `INTEGRATION_TARGET_NOT_CONFIGURED` on push             | No Jira target set for the project — configure it in Project Settings → Integrations.                                       |

---

## Reference

- **Design:** [ADR-0023](adr/0023-third-party-integrations-jira.md)
- **Module:** `com.kntro.reqsai.gateway`
- **Migrations:** tenant — connections, targets, OAuth columns, `integration_sync_jobs`; common —
  Spring Batch metadata in `public` (`V20260709100001__spring_batch_metadata.sql`)
- **Config keys:** `reqsai.integrations.encryption-key`, `reqsai.integrations.jira.oauth.{client-id,client-secret,redirect-uri,state-secret}`, `spring.batch.job.enabled=false`
- **Endpoints:** `/api/organizations/{orgId}/integrations*` (connection, OAuth authorize-url/callback), `/api/projects/{projectId}/integration/jira*` (target, story push, import preview/import, sync jobs)
- **Realtime:** `/topic/projects/{projectId}/integration-jobs` (job progress snapshots)
