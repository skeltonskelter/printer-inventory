#!/bin/sh
# Only restores into the disposable restore-check project, never the application DB.
set -eu
if [ "$PGHOST" != "restore-db" ] || [ "$PGDATABASE" != "inventory_restore_check" ]; then
    echo "Refusing restore: target must be the isolated restore-check database." >&2
    exit 1
fi
case "${BACKUP_FILE:-}" in
    ""|.*|*[!a-zA-Z0-9_.-]*) echo "BACKUP_FILE must be a backup filename, without a directory." >&2; exit 1 ;;
esac
case "$BACKUP_FILE" in *.dump) ;; *) echo "Expected a .dump archive." >&2; exit 1 ;; esac
cd /backups
sha256sum -c "$BACKUP_FILE.sha256"
pg_restore --exit-on-error --single-transaction --no-owner --no-privileges --dbname="$PGDATABASE" "$BACKUP_FILE"
psql --no-psqlrc --set=ON_ERROR_STOP=1 <<'SQL'
DO $$
BEGIN
    IF (SELECT count(*) FROM flyway_schema_history WHERE version IN ('1', '2') AND success) <> 2 THEN
        RAISE EXCEPTION 'Expected successful V1 and V2 migrations';
    END IF;
    IF EXISTS (SELECT 1 FROM relocation_history r LEFT JOIN printers p ON p.id = r.printer_id
               LEFT JOIN locations a ON a.id = r.previous_location_id
               LEFT JOIN locations b ON b.id = r.new_location_id
               WHERE p.id IS NULL OR a.id IS NULL OR b.id IS NULL) THEN
        RAISE EXCEPTION 'Orphaned relocation history';
    END IF;
END $$;
SELECT (SELECT count(*) FROM printers) AS retained_printers,
       (SELECT count(*) FROM printers WHERE deleted_at IS NULL) AS current_printers,
       (SELECT count(*) FROM locations) AS locations,
       (SELECT count(*) FROM relocation_history) AS relocations;
SQL
echo "Restore check passed. The application database was not changed."
