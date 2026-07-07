#!/usr/bin/env bash
# Creates a new Flyway migration file with a commit-timestamp version
# (V<yyyyMMddHHmmss>__description.sql), so two branches adding a migration
# never collide on the same version number. See docs/MIGRATIONS.md and
# docs/adr/0022-flyway-timestamp-based-migration-versions.md.
#
# Usage: scripts/new-migration.sh <common|tenant> <short_description>
# Example: scripts/new-migration.sh tenant add_project_tags
set -euo pipefail

usage() {
  echo "Usage: $0 <common|tenant> <short_description>" >&2
  echo "Example: $0 tenant add_project_tags" >&2
  exit 1
}

[ $# -eq 2 ] || usage

PLACE="$1"
DESCRIPTION_RAW="$2"

case "$PLACE" in
  common|tenant) ;;
  *) echo "Error: first argument must be 'common' or 'tenant', got '$PLACE'" >&2; usage ;;
esac

DESCRIPTION="$(echo "$DESCRIPTION_RAW" | tr '[:upper:] ' '[:lower:]_' | tr -cs 'a-z0-9_' '_' | sed -E 's/_+/_/g; s/^_//; s/_$//')"
[ -n "$DESCRIPTION" ] || { echo "Error: description resolves to empty after sanitizing ('$DESCRIPTION_RAW')" >&2; exit 1; }

MIGRATION_DIR="$(cd "$(dirname "$0")/.." && pwd)/src/main/resources/db/migration/$PLACE"
mkdir -p "$MIGRATION_DIR"

LOCK_DIR="$MIGRATION_DIR/.new-migration.lock"

# Serializes concurrent runs on this machine (mkdir is atomic on every POSIX
# filesystem, unlike flock which isn't available on macOS by default) so two
# invocations in the same second can never pick the same version.
for _ in $(seq 1 50); do
  if mkdir "$LOCK_DIR" 2>/dev/null; then
    break
  fi
  sleep 0.1
done
trap 'rmdir "$LOCK_DIR" 2>/dev/null || true' EXIT

VERSION="$(date -u +%Y%m%d%H%M%S)"
while compgen -G "$MIGRATION_DIR/V${VERSION}__*.sql" > /dev/null; do
  VERSION=$(( VERSION + 1 ))
done

FILE="$MIGRATION_DIR/V${VERSION}__${DESCRIPTION}.sql"
touch "$FILE"
echo "Created: ${FILE#"$(cd "$(dirname "$0")/.." && pwd)/"}"
