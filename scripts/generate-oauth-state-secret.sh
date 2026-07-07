#!/usr/bin/env bash
# Generates a random secret for signing the Jira OAuth 2.0 (3LO) `state` token (ADR-0022).
#   - 32 random bytes, hex-encoded (64 hex chars), used as the raw HMAC-SHA256 key.
#
# The secret is NOT stored in the repo. Paste the printed value into your .env as
# JIRA_OAUTH_STATE_SECRET; in production mount it as a secret (see the deploy workflow).
set -euo pipefail

SECRET="$(openssl rand -hex 32)"

echo "Generated Jira OAuth state secret:"
echo
echo "JIRA_OAUTH_STATE_SECRET=$SECRET"
echo
echo "Paste the line above into your .env (do not commit it)."
