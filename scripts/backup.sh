#!/bin/sh
# Runs inside the maintenance container; no host PostgreSQL tools required.
set -eu
umask 077
cd /backups
archive=$(mktemp ".partial_inventory_$(date -u +%Y%m%dT%H%M%SZ)_XXXXXX")
trap 'rm -f "$archive"' EXIT
pg_dump --format=custom --file="$archive"
pg_restore --list "$archive" >/dev/null
completed="${archive#.partial_}.dump"
mv "$archive" "$completed"
sha256sum "$completed" > "$completed.sha256"
echo "Backup saved: backups/$completed"
echo "Checksum saved: backups/$completed.sha256"
echo "Run the isolated restore check, then copy both files off this machine."
