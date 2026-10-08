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
   - Amount: **USD 29.00**
   - Save the product and copy the **Price ID** (looks like `price_1AbPro...`).
3. Create **"ReqsAI Enterprise"**:
   - Pricing: **Recurring**
   - Interval: **Monthly**
   - Amount: **USD 99.00**
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
2. Set the **Endpoint URL** to your deployed **frontend** domain, not the bare API domain:
   ```text
   https://app.tamci.app/api/billing/webhooks/stripe
   ```
   > [!WARNING]
   > `api.tamci.app` resolves directly to the ALB, which is locked down to CloudFront-only ingress
   > (see [reqsai-infra](https://github.com/Kntro-Soft/reqsai-infra)'s security groups). Stripe's
   > servers are not CloudFront, so a webhook pointed at `api.tamci.app` times out silently — no
   > log entry on the backend at all, since the request never reaches it. Always use
   > `app.tamci.app` (routed through CloudFront) for anything Stripe needs to reach.
3. Click **Select events** and subscribe to:
   *   `checkout.session.completed`
   *   `customer.subscription.deleted`
   *   `invoice.payment_failed`
4. Click **Add endpoint** to save.
5. Under the endpoint details page, click **Reveal** under the **Signing secret** section.
6. Copy this secret (starts with `whsec_...`) and configure the `STRIPE_WEBHOOK_SECRET` environment variable on your deployed server (e.g., in AWS, Render, or ECS).

---

## Production Deployment Checklist

When deploying to production, apply the following changes:
> [!NOTE]
> This will require a live Stripe account with verified payment methods and real money transactions. Only use for real live use with real clients.  

1. **API Keys**: Replace `sk_test_` keys with live production keys (`sk_live_`).
2. **Products & Prices**: Create the production products and prices in the live dashboard catalog and update `BILLING_*_STRIPE_PRICE_ID` environment variables.
3. **Webhook Registration**:
   - Go to **Developers** ▸ **Webhooks** ▸ **Add endpoint**.
   - Set the URL to `https://app.tamci.app/api/billing/webhooks/stripe` — **not** `api.tamci.app`
     (see the warning above: the bare API domain is CloudFront-only and unreachable from Stripe).
   - Subscribe to these events:
     *   `checkout.session.completed` (handles purchase / upgrade activation).
     *   `customer.subscription.deleted` (handles cancellation/lapse).
     *   `invoice.payment_failed` (handles past-due payment failure alerts).
   - Copy the live signing secret (`whsec_`) to your production `STRIPE_WEBHOOK_SECRET`.
