#!/usr/bin/env bash
# Generates a random AES-256 key for encrypting integration secrets at rest (ADR-0022).
#   - 32 random bytes, base64-encoded (~44 chars), used as the AES-256-GCM key.
#
# NOTE: this is base64 (NOT hex) — the backend base64-decodes it and requires exactly
# 32 bytes, so a hex value would fail at startup. The Jira OAuth state secret is a
# DIFFERENT value in a DIFFERENT format (hex) — see scripts/generate-oauth-state-secret.sh.
#
# The key is NOT stored in the repo. Paste the printed value into your .env as
# INTEGRATIONS_ENCRYPTION_KEY; in production mount it as a secret (see the deploy workflow).
set -euo pipefail

KEY="$(openssl rand -base64 32)"

echo "Generated integrations encryption key (AES-256):"
echo
echo "INTEGRATIONS_ENCRYPTION_KEY=$KEY"
echo
echo "Paste the line above into your .env (do not commit it)."
