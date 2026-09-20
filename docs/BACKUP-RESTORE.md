# Backup and recovery

A Docker volume preserves data across container recreation, but it is not a backup. Back up before releases and at an interval that matches how much data your organization can afford to lose. Keep protected copies outside the VM and test restoring them regularly.

## Create a backup

With the application database running, from the project root (same command on Windows and Ubuntu):

```bash
docker compose --profile maintenance run --rm --no-deps backup
```

The job connects to the existing database using `.env`, runs PostgreSQL 17 `pg_dump` in custom format, validates the archive listing, and writes a unique timestamped `.dump` plus `.dump.sha256` to `backups/`. It does not stop the application. PostgreSQL provides a consistent snapshot for the dump; later writes are not in that backup. No host PostgreSQL or shell installation is needed.

An unsuccessful dump returns a nonzero exit code and removes its unfinished temporary file. Success is printed only after the checksum exists. The script does not delete old backups or copy them off-machine. Keep both output files together, plus protected copies of `.env`, release source/configuration, and matching image archives. Backups can contain identifiers and remarks; restrict access. A checksum detects corruption, not malicious replacement of both files.

## Verify by restoring to a disposable database

Use the exact filename printed by the backup job, without a directory. PowerShell:

```powershell
$env:BACKUP_FILE = 'inventory_TIMESTAMP_SUFFIX.dump'
docker compose -f docker-compose.restore-check.yml up --abort-on-container-exit --exit-code-from restore-check
docker compose -f docker-compose.restore-check.yml down
```

Ubuntu:

```bash
export BACKUP_FILE='inventory_TIMESTAMP_SUFFIX.dump'
docker compose -f docker-compose.restore-check.yml up --abort-on-container-exit --exit-code-from restore-check
docker compose -f docker-compose.restore-check.yml down
```

The checker verifies the checksum, restores with errors treated as fatal in one transaction, checks migrations and history references, and prints record counts. Require exit code zero and **Restore check passed**. Review the printed counts against the inventory expected at backup time.

This separate Compose project has its own network, no exposed port, and a temporary database in memory. It never attaches the application volume. The script refuses a target other than `restore-db` / `inventory_restore_check`. Always run `down` after checking, including after a failure; the next test needs an empty database. Old pre-Phase-4 backups fail the V2 assertion by design; use a matching older recovery procedure for them.

The options are described in the [PostgreSQL pg_restore documentation](https://www.postgresql.org/docs/17/app-pgrestore.html). This check verifies database recovery. Before using a restored database in production, also test the matching application release against it.

## Schedule backups on Ubuntu

Example cron entry for an operator authorized to run Docker, after adjusting the project path:

```cron
0 2 * * * cd /opt/printer-inventory && /usr/bin/docker compose --profile maintenance run --rm --no-deps backup
```

Confirm the Docker executable path, cron environment and output delivery on your server. Configure monitoring/alerts for failures and separately copy archives off the VM. Choose and document a retention policy; no scheduled job or automatic pruning is installed by this repository. Test the command interactively before adding it to cron.

## Recover without overwriting the working database

The following is a deliberate maintenance procedure, not part of the test script. Use a **new, unused database name** and preserve the old database. Commands below use `printer_inventory_recovered` as an example. Do not run them if that name already contains data.

1. Verify the archive using the isolated check. Retain a fresh backup of the current database before recovery.
2. Stop application writes with `docker compose -f docker-compose.yml -f docker-compose.production.yml stop frontend backend`. Leave `db` running.
3. Copy the selected archive and restore it to the new database. These commands use the container's existing database user, so no password is printed:

```bash
docker compose cp ./backups/inventory_TIMESTAMP_SUFFIX.dump db:/tmp/recovery.dump
docker compose exec -T db sh -c 'createdb -U "$POSTGRES_USER" printer_inventory_recovered'
docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" --dbname=printer_inventory_recovered --no-owner --no-privileges --exit-on-error --single-transaction /tmp/recovery.dump'
```

4. Test the matching backend release against the recovered database before switching users. For example, replace `RELEASE_TAG` below with the archived release tag and run from the project root in a separate terminal:

```bash
docker run --rm --name printer-inventory-recovery-api --network printer-inventory_default --env-file .env -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/printer_inventory_recovered -p 127.0.0.1:18080:8080 --entrypoint sh printer-inventory-backend:RELEASE_TAG -c 'export SPRING_DATASOURCE_USERNAME="$POSTGRES_USER"; export SPRING_DATASOURCE_PASSWORD="$POSTGRES_PASSWORD"; exec java -jar /app/app.jar'
```

In another terminal, check `http://127.0.0.1:18080/api/health`, `/api/dashboard`, `/api/printers`, and selected printers' relocation endpoints. Inspect expected identifiers, locations and histories. Stop the recovery API with Ctrl+C when done. This uses the existing Compose network but publishes the test API only on loopback. Do not connect a newer release that automatically applies migrations until that is part of the recovery plan.

5. For the final switch, set root `.env` `POSTGRES_DB=printer_inventory_recovered`, preserving the existing database username/password and the correct release tag/configuration. Recreate with `docker compose -f docker-compose.yml -f docker-compose.production.yml up -d --no-build --wait`.
6. Verify health, counts, identifiers, current locations and transfer histories. Retain the previous database until recovery is accepted and later writes have been reconciled. Do not drop it automatically.

Changing `.env` did not create or restore the database: step 3 explicitly did that. The old and new databases live in the same existing volume; this protects against overwriting a database but is not off-machine protection. If the whole server was lost, first prepare a new PostgreSQL 17 instance and restore there with matching credentials/configuration.

For PostgreSQL major-version upgrades, follow PostgreSQL's migration procedure separately. Do not point a new major image at the existing data directory as an application rollback technique.
