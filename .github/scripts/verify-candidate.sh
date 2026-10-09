#!/usr/bin/env bash
# Verifies a release candidate image of reqsai-api the way production runs it, without deploying anything:
# the prod profile against a fresh PostgreSQL + pgvector (so every Flyway migration runs), then smoke checks.
# There is no second EC2 for a staging environment; this ephemeral run replaces it. Containers are removed at
# the end, and the API log is printed if a check fails.
#
# Usage: verify-candidate.sh <image>   e.g. ghcr.io/kntro-soft/reqsai-api@sha256:<digest>
set -euo pipefail

image="${1:?usage: verify-candidate.sh <image reference>}"
postgres_image="${POSTGRES_IMAGE:-pgvector/pgvector:0.8.7-pg16}"
mailpit_image="${MAILPIT_IMAGE:-axllent/mailpit:v1.29.3}"
port="${VERIFY_PORT:-18080}"
mail_port="${VERIFY_MAIL_PORT:-18025}"
base="http://127.0.0.1:$port"
mail="http://127.0.0.1:$mail_port"
name="reqsai-verify-$$"
work=$(mktemp -d)
summary="${GITHUB_STEP_SUMMARY:-/dev/null}"
passed=false

cleanup() {
  if [[ "$passed" != true ]]; then
    echo "::group::API log"
    docker logs "$name-api" 2>&1 | tail -n 200 || true
    echo "::endgroup::"
  fi
  docker rm -f "$name-api" "$name-db" "$name-mail" >/dev/null 2>&1 || true
  docker network rm "$name" >/dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT

check() {
  echo "| $1 | $2 |" >>"$summary"
  echo "ok: $1 ($2)"
}

fail() {
  echo "| $1 | **failed**: $2 |" >>"$summary"
  echo "::error::$1: $2"
  exit 1
}

# Throwaway secrets: a fresh RSA key pair for the JWTs, a random encryption key and database password, and
# placeholder AI keys (the clients need a value to start; no AI call is made).
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$work/private.pem" 2>/dev/null
openssl pkey -in "$work/private.pem" -pubout -out "$work/public.pem"
pem_body() { grep -v -- '-----' "$1" | tr -d '\n'; }
db_password=$(openssl rand -hex 16)

cat >"$work/api.env" <<EOF
SPRING_PROFILES_ACTIVE=prod
SERVER_FORWARD_HEADERS_STRATEGY=native
APP_URL=http://localhost
FRONTEND_URL=http://localhost
WEB_APP_URL=http://localhost
CORS_ALLOWED_ORIGINS=http://localhost
DB_HOST=$name-db
DB_PORT=5432
DB_NAME=reqsai
DB_USERNAME=reqsai
DB_PASSWORD=$db_password
DB_POOL_SIZE=5
JWT_ISSUER=reqsai
JWT_PRIVATE_KEY_PEM=$(pem_body "$work/private.pem")
JWT_PUBLIC_KEY_PEM=$(pem_body "$work/public.pem")
INTEGRATIONS_ENCRYPTION_KEY=$(openssl rand -base64 32)
EMAIL_PROVIDER=mailpit
MAIL_HOST=$name-mail
MAIL_PORT=1025
MAIL_FROM=verify@reqsai.test
MAIL_USERNAME=verify
MAIL_PASSWORD=verify
GEMINI_API_KEY=verification-placeholder
ASSEMBLYAI_API_KEY=verification-placeholder
BILLING_PAYMENT_PROVIDER=fake
SPRINGDOC_API_DOCS_ENABLED=false
SPRINGDOC_SWAGGER_UI_ENABLED=false
EOF

{
  echo "### Verification of \`$image\`"
  echo
  echo "| Check | Result |"
  echo "| --- | --- |"
} >>"$summary"

docker network create "$name" >/dev/null
docker run --detach --name "$name-mail" --network "$name" --publish "127.0.0.1:$mail_port:8025" \
  --env MP_SMTP_AUTH_ACCEPT_ANY=1 --env MP_SMTP_AUTH_ALLOW_INSECURE=1 "$mailpit_image" >/dev/null
docker run --detach --name "$name-db" --network "$name" \
  --env POSTGRES_DB=reqsai --env POSTGRES_USER=reqsai --env POSTGRES_PASSWORD="$db_password" \
  --health-cmd 'pg_isready -U reqsai -d reqsai' --health-interval 3s --health-retries 40 \
  "$postgres_image" >/dev/null
for _ in $(seq 1 60); do
  [[ "$(docker inspect --format '{{ .State.Health.Status }}' "$name-db")" == healthy ]] && break
  sleep 2
done
[[ "$(docker inspect --format '{{ .State.Health.Status }}' "$name-db")" == healthy ]] || fail "PostgreSQL" "not healthy"

docker run --detach --name "$name-api" --network "$name" --publish "127.0.0.1:$port:8080" \
  --memory 1024m --env-file "$work/api.env" \
  --env JDK_JAVA_OPTIONS='-Xms256m -Xmx512m -XX:MaxMetaspaceSize=256m -Xss512k -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError' \
  "$image" >/dev/null

status=""
for _ in $(seq 1 90); do
  if [[ "$(docker inspect --format '{{ .State.Running }}' "$name-api")" != true ]]; then
    fail "API start" "the container exited"
  fi
  status=$(curl -fsS --max-time 5 "$base/actuator/health/readiness" 2>/dev/null | jq -r '.status // empty' || true)
  [[ "$status" == UP ]] && break
  sleep 5
done
[[ "$status" == UP ]] || fail "Readiness" "/actuator/health/readiness is '${status:-unreachable}' after 7.5 minutes"
check "Readiness (prod profile, Flyway migrations applied)" "UP"

history_table=$(docker exec "$name-db" psql -U reqsai -d reqsai -tAc \
  "select table_schema || '.' || table_name from information_schema.tables where table_name = 'flyway_schema_history' order by 1 limit 1")
[[ -n "$history_table" ]] || fail "Flyway" "no flyway_schema_history table"
applied=$(docker exec "$name-db" psql -U reqsai -d reqsai -tAc "select count(*) from $history_table where success")
failed=$(docker exec "$name-db" psql -U reqsai -d reqsai -tAc "select count(*) from $history_table where not success")
[[ "$failed" == 0 && "$applied" -gt 0 ]] || fail "Flyway" "$applied applied, $failed failed in $history_table"
check "Flyway ($history_table)" "$applied migrations applied, none failed"

code=$(curl -s -o /dev/null -w '%{http_code}' "$base/api/auth/dev-token")
[[ "$code" == 401 || "$code" == 403 ]] || fail "Dev token endpoint closed in prod" "HTTP $code"
check "Dev token endpoint closed in prod" "HTTP $code"

code=$(curl -s -o /dev/null -w '%{http_code}' "$base/api/organizations")
[[ "$code" == 401 ]] || fail "Protected endpoint without a token" "HTTP $code"
check "Protected endpoint without a token" "HTTP $code"

# End to end: sign up, confirm the e-mail with the link the API sends, sign in, read the profile and create an
# organization (which provisions its tenant schema and runs the tenant migrations).
email="verify-$(openssl rand -hex 4)@reqsai.test"
password="Verify-$(openssl rand -hex 8)"
api() { curl -sS --max-time 30 -H 'Content-Type: application/json' "$@"; }

code=$(api -o "$work/register.json" -w '%{http_code}' -X POST "$base/api/auth/register" \
  -d "$(jq -n --arg e "$email" --arg p "$password" '{email: $e, password: $p, firstName: "Release", lastName: "Check"}')")
[[ "$code" == 201 || "$code" == 200 ]] || fail "Sign up" "HTTP $code $(cat "$work/register.json")"
check "Sign up" "HTTP $code"

token=""
for _ in $(seq 1 30); do
  id=$(curl -fsS "$mail/api/v1/search?query=to:$email" 2>/dev/null | jq -r '.messages[0].ID // empty' || true)
  if [[ -n "$id" ]]; then
    token=$(curl -fsS "$mail/api/v1/message/$id" | jq -r '.Text + " " + .HTML' | grep -oE 'token=[A-Za-z0-9_-]+' | head -n 1 | cut -d= -f2 || true)
    [[ -n "$token" ]] && break
  fi
  sleep 2
done
[[ -n "$token" ]] || fail "Verification e-mail" "no message with a token reached $email"
check "Verification e-mail" "delivered"

code=$(api -o "$work/verify.json" -w '%{http_code}' -X POST "$base/api/auth/verify-email" -d "$(jq -n --arg t "$token" '{token: $t}')")
[[ "$code" == 204 || "$code" == 200 ]] || fail "Verify e-mail" "HTTP $code $(cat "$work/verify.json")"
check "Verify e-mail" "HTTP $code"

code=$(api -o "$work/login.json" -w '%{http_code}' -X POST "$base/api/auth/login" \
  -d "$(jq -n --arg e "$email" --arg p "$password" '{email: $e, password: $p}')")
access=$(jq -r '.accessToken // empty' "$work/login.json" 2>/dev/null || true)
[[ "$code" == 200 && -n "$access" ]] || fail "Sign in" "HTTP $code"
check "Sign in" "HTTP $code, access token issued"

code=$(api -o "$work/me.json" -w '%{http_code}' -H "Authorization: Bearer $access" "$base/api/users/me")
[[ "$code" == 200 && "$(jq -r '.email // empty' "$work/me.json")" == "$email" ]] || fail "Profile" "HTTP $code"
check "Profile with the token" "HTTP $code"

code=$(api -o "$work/org.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $access" "$base/api/organizations" \
  -d '{"name": "Release check"}')
[[ "$code" == 201 || "$code" == 200 ]] || fail "Create an organization" "HTTP $code $(cat "$work/org.json")"
check "Create an organization (tenant schema)" "HTTP $code"

passed=true
echo "Candidate verified: $image"
