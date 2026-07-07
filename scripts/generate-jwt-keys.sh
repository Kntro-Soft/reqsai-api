#!/usr/bin/env bash
# Generates a DEV RSA key pair for JWT signing (RS256), in PEM format.
#   - private_key.pem : PKCS#8, used to SIGN access tokens
#   - public_key.pem  : X.509,  used to VERIFY access tokens
#
# These dev keys are git-ignored. In production, mount real keys as secrets
# (see .github/workflows/deploy.yml — Cloud Run --update-secrets).
set -euo pipefail

CERTS_DIR="$(cd "$(dirname "$0")/.." && pwd)/src/main/resources/certs"
mkdir -p "$CERTS_DIR"

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out "$CERTS_DIR/private_key.pem"
openssl rsa -pubout \
  -in "$CERTS_DIR/private_key.pem" \
  -out "$CERTS_DIR/public_key.pem"

echo "Generated dev JWT keys in: $CERTS_DIR"
ls -1 "$CERTS_DIR"
