# Billing & Subscription Setup — Stripe Integration

Guide to configuring the commercial billing environment, enabling plans, and setting up Stripe in **Test Mode** for local development.

## Overview

Reqs-AI manages subscription plans (`FREE`, `PRO`, `ENTERPRISE`) inside the **`billing`** bounded context. The integration uses **Stripe Checkout** for hosted payments and **Webhooks** to listen for plan updates or cancellations.

---

## Local Configuration (Test Mode)

Follow these steps to obtain your Stripe credentials and run the local development stack:

### Step 1: Create a Stripe Account & Enable Test Mode
1. Register for a free developer account at [dashboard.stripe.com/register](https://dashboard.stripe.com/register). (No bank details or account verification are needed to use Test Mode).
2. Once logged in, ensure the **"Test mode"** toggle (top right of the dashboard) is **activated**. 
3. Verify that all keys display with prefixes `sk_test_` or `pk_test_`.

> [!WARNING]
> Never use live keys (`sk_live_`) in a local or development environment. Test mode is completely free and simulates transactions without real money.

### Step 2: Get Secret API Key (`STRIPE_API_KEY`)
1. In the dashboard, navigate to **Developers** ▸ **API keys** (with Test Mode ON).
2. Copy the **Secret key** (looks like `sk_test_51Ab...`).
3. Add it to your local `.env` or application configuration:
   ```env
   STRIPE_API_KEY=sk_test_51Ab...
   ```
   *(Note: We use hosted Stripe Checkout, so the publishable key `pk_test_` is not required by the backend).*

### Step 3: Configure Products and Prices (`BILLING_*_STRIPE_PRICE_ID`)
Create the plans in Stripe to map them to your system:
1. Go to **Product catalog** ▸ **+ Create product**.
2. Create **"ReqsAI Pro"**:
   - Pricing: **Recurring**
   - Interval: **Monthly**
   - Amount: **USD 49.00**
   - Save the product and copy the **Price ID** (looks like `price_1AbPro...`).
3. Create **"ReqsAI Enterprise"**:
   - Pricing: **Recurring**
   - Interval: **Monthly**
   - Amount: **USD 149.00**
   - Save and copy the **Price ID** (looks like `price_1AbEnt...`).
4. Set these in your `.env` file:
   ```env
   BILLING_PRO_STRIPE_PRICE_ID=price_1AbPro...
   BILLING_ENTERPRISE_STRIPE_PRICE_ID=price_1AbEnt...
   ```

### Step 4: Configure the Webhook (`STRIPE_WEBHOOK_SECRET`) with Stripe CLI
Webhooks are essential to listen to Stripe events (like successful payments or cancellations) and update the organization's subscription state in the database. In local development, Stripe cannot reach your `localhost`, so you must use the Stripe CLI to forward events.

1. **Install the Stripe CLI**:
   *   **Windows (via npm)**: `npm i -g @stripe/cli`
   *   **Other Platforms**: See the [Stripe CLI Installation Guide](https://docs.stripe.com/stripe-cli).
2. **Log in to Stripe CLI**:
   ```bash
   stripe login
   ```
   *(This will open a browser tab to authorize the CLI with your developer account).*
3. **Forward Webhook Events**:
   Run the listener in a separate terminal window and keep it running while developing:
   ```bash
   stripe listen --forward-to localhost:8080/api/billing/webhooks/stripe
   ```
4. **Copy the Signing Secret**:
   The CLI output will display:
   ```text
   Ready! Your webhook signing secret is whsec_1Ab...
   ```
   Copy that secret (starts with `whsec_`) and add it to your `.env`:
   ```env
   STRIPE_WEBHOOK_SECRET=whsec_1Ab...
   ```

### Step 5: Activate Stripe Provider and URLs
Add the following settings to your local `.env`:
```env
BILLING_PAYMENT_PROVIDER=stripe
WEB_APP_URL=http://localhost:4200
```

---

## Testing the Checkout Flow

1. Restart the backend service to load the new `.env` settings.
2. Ensure both the frontend (running on `:4200`) and `stripe listen` are active.
3. In the application UI, go to **Settings** ▸ **Billing** and click **Upgrade to Pro**.
4. The API will generate a session and redirect you to the Stripe Checkout page.
5. Use the standard Stripe test card number:
   *   **Card Number**: `4242 4242 4242 4242`
   *   **Expiry**: Any future date (e.g., `12/30`)
   *   **CVC**: Any 3 digits (e.g., `123`)
   *   **ZIP/Postal Code**: Any code (e.g., `90210`)
6. On success, Stripe redirects you back to `/billing/success`, triggers `checkout.session.completed`, the CLI forwards the event to your webhook, and your organization plan flips to **Pro / Active**.

> [!NOTE]
> Refer to the [Stripe Testing Guide](https://docs.stripe.com/testing) for additional test card numbers simulating card failures, 3D Secure verification, and other transaction scenarios.

---

## Deployed Test Environment (Demos / Presentations)

For university presentations, demo environments, or staging deployments, **you should keep Test Mode enabled**. This allows you to showcase the full checkout flow live to your audience using the test card `4242 4242 4242 4242` without spending real money.

Since your deployed application has a public domain name, **you do not need the Stripe CLI (`stripe listen`) running on your server**. Stripe can send the webhook events directly to your public URL:

1. In the Stripe Dashboard (with **Test Mode** turned ON), go to **Developers** ▸ **Webhooks** and click **Add endpoint**.
2. Set the **Endpoint URL** to the public domain of the app. The web and the API share one origin
   (Caddy proxies `/api/*` to the API on the MVP host), so the webhook lives under the same domain:
   ```text
   https://reqsai.tech/api/billing/webhooks/stripe
   ```
3. Click **Select events** and subscribe to:
   *   `checkout.session.completed`
   *   `customer.subscription.deleted`
   *   `invoice.payment_failed`
4. Click **Add endpoint** to save.
5. Under the endpoint details page, click **Reveal** under the **Signing secret** section.
6. Copy this secret (starts with `whsec_...`) into the deployment's secrets. On the MVP host it is
   `vault_stripe_webhook_secret` in the ansible vault of
   [reqsai-infra](https://github.com/Kntro-Soft/reqsai-infra), next to `vault_stripe_api_key`. The
   provider and the price ids go in the host's `app_api_settings` (`BILLING_PAYMENT_PROVIDER: stripe`,
   `BILLING_*_STRIPE_PRICE_ID`). Redeploy so the API picks them up.

---

## Production Deployment Checklist

When deploying to production, apply the following changes:
> [!NOTE]
> This will require a live Stripe account with verified payment methods and real money transactions. Only use for real live use with real clients.  

1. **API Keys**: Replace `sk_test_` keys with live production keys (`sk_live_`).
2. **Products & Prices**: Create the production products and prices in the live dashboard catalog and update `BILLING_*_STRIPE_PRICE_ID` environment variables.
3. **Webhook Registration**:
   - Go to **Developers** ▸ **Webhooks** ▸ **Add endpoint**.
   - Set the URL to `https://reqsai.tech/api/billing/webhooks/stripe` (same origin as the web app).
   - Subscribe to these events:
     *   `checkout.session.completed` (handles purchase / upgrade activation).
     *   `customer.subscription.deleted` (handles cancellation/lapse).
     *   `invoice.payment_failed` (handles past-due payment failure alerts).
   - Copy the live signing secret (`whsec_`) to your production `STRIPE_WEBHOOK_SECRET`.

---

## AI usage metering and the monthly allowance

Every AI call is reported once, with what the provider returned, and the same number feeds the plan's monthly
counter and the usage page.

### The ledger

Each successful provider call is one row of `ai_usage_events` in the organization's tenant schema (append-only,
`V20261012100000__ai_usage_events.sql`): feature, project, model, input / output / cached input tokens, the
tokens it added to the quota (`quota_tokens`) and, for speech-to-text, `audio_seconds`. `idempotency_key` is
the provider's call id, so a call reported twice is stored and counted once. The feature is one of
`LIVE_SUGGESTIONS`, `CHAT`, `FINAL_STORIES` (the pass that flushes a meeting when it stops), `CODE_INDEX`,
`CODE_DEEP_SEARCH`, `DOCUMENT_CLASSIFICATION`, `EMBEDDINGS`, `TRANSCRIPTION` or `OTHER`.

The caller names the feature and project with `AiUsageScope.call(feature, projectId, ...)`; adapters report
through the shared `AiUsageReporter`, which writes on a small worker pool so the AI call never waits for, or
fails because of, the database. A new AI feature meters itself by opening a scope and calling a model through
`ChatUsage.report(...)` (chat models) or `TokenUsage` (anything else).

### The counter and how a call weighs

`public.subscriptions.token_quota_used` is raised with one `UPDATE ... SET token_quota_used = token_quota_used + n`
in the same transaction as the ledger insert, so parallel calls never lose an update and the counter equals the
sum of the ledger's `quota_tokens` for the period. When the stored period has elapsed it is rolled forward first,
under a row lock, and the call is counted in the new period.

A call adds `uncached input + 50 % of cached input + output`. A prompt prefix the provider serves from its cache
costs the provider half as much (OpenAI bills cached input at 50 %), so it counts half. Embeddings count at
10 % (about a tenth of generation per token); their tokens come from the provider's usage, or are estimated as
one per 4 characters when it returns none. Speech-to-text is metered in seconds (`audio_seconds`) and never
counts as tokens.

### Limits and warnings

- At 80, 90 and 100 % of the plan's `maxTokensPerMonth` the usage response carries `warningLevel`
  (`WARNING_80`, `WARNING_90`, `EXHAUSTED`) and a `TOKEN_QUOTA_WARNING` message is pushed to
  `/topic/organizations/{orgId}/usage`, once per level per period (the level reached is stored in
  `subscriptions.quota_warning_percent` with a conditional UPDATE and zeroed with the counter).
- At 100 % AI generation stops and nothing else does: the chat, "Analizar ahora" and other explicit AI requests fail
  with `AI_QUOTA_EXCEEDED` (HTTP 402; `plan`, `limit`, `used` and `resetsAt` in the body); live suggestions pause
  quietly, once telling the session with an `AI_QUOTA_PAUSED` message on `/topic/sessions/{id}` and picking the
  transcript up again when the allowance renews; code indexing keeps the structural description of the modules;
  an uploaded document is stored unclassified. Reading, editing by hand and exporting are never blocked.
- The check fails open: with no subscription, no tenant or a database error the AI call goes ahead.

### Reducing what a meeting costs

The live prompt puts everything fixed (rules, output schema) first and everything that changes (project context,
backlog, transcript) last, so the provider's automatic prefix cache (OpenAI: prefixes of 1,024 tokens or more)
serves the rules from the second pass on, also across live passes and the chat. The glossary and constraints sent
on each pass are capped (40 and 20 entries, 400 characters each) and a client document summary is cut at 1,000
characters. `reqsai.ai.generation.live-model` (`GENERATION_LIVE_MODEL`) names a cheaper model for live passes
only; unset, every call uses the provider's default model.

Measured on the 24.7 s reference recording and one chat requirement (OpenAI `gpt-4o-mini`):

| | before | after, cold cache | after, warm cache |
|---|---|---|---|
| E2E meeting (2 passes + embeddings), tokens counted | 13,819 | 11,090 | 7,649 |
| Chat "hola" | 820 | 819 | 819 |
| Chat requirement (converse + extraction) | 7,938 | 5,480 | 5,480 |

The provider-reported total is unchanged (about 6,800 input per pass); what changed is that 6,000 of them are
served from the cache and count half.

### Meeting minutes left

The usage page estimates `minutesLeft = remaining tokens / tokens per meeting minute`. The rate is the
organization's own: its `LIVE_SUGGESTIONS` and `FINAL_STORIES` quota tokens over its transcribed audio minutes
in the last 90 days, once it has at least 2 minutes of audio; before that `MeetingEstimate.DEFAULT_TOKENS_PER_MINUTE`
(10,000) is used and the response says `measured: false`.
