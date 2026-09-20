# Phase 6: production operations

Phase 6 adds production container settings, environment configuration, deployment documentation, and working backup/recovery tooling. The application remains a simple monolith without authentication. No database migration or application feature change is needed.

## Files created or changed

| File | Purpose |
| --- | --- |
| `docker-compose.production.yml` | Resource limits, log rotation, read-only app filesystems, dropped capabilities and graceful shutdown |
| `docker-compose.yml` | Tagged app images, configurable bind address, new Nginx container port, optional backup job |
| `frontend/Dockerfile` | Non-root Nginx with custom runtime configuration |
| `frontend/nginx-main.conf` | Writable temporary paths and process configuration |
| `frontend/nginx.conf`, `frontend/security-headers.conf` | Reverse proxy, caching, security headers, request size limit |
| `.env.example` | Bind address and release tag configuration |
| `.gitattributes` | Linux line endings for shell scripts and Nginx configuration |
| `scripts/backup.sh` | Unique custom-format database archive and SHA-256 checksum |
| `scripts/restore-check.sh`, `docker-compose.restore-check.yml` | Checksum and transactional restore into a separate temporary database |
| `docker-compose.e2e.yml`, `e2e/playwright.config.js` | Run browser tests under production restrictions and the new container port |
| `e2e/deployment.spec.js` | Deep links, headers, caching, CSP and API proxy checks |
| `README.md`, `docs/DEPLOYMENT.md`, `docs/BACKUP-RESTORE.md` | Beginner-oriented operation, deployment, recovery and rollback guides |

## Run production locally

Keep the existing `.env` and database volume. From the project root:

```powershell
docker compose -f docker-compose.yml -f docker-compose.production.yml up -d --build --wait
docker compose -f docker-compose.yml -f docker-compose.production.yml ps
```

With the default host port, open http://localhost:8080. Use the same two Compose files for later production operations. The normal `down` command keeps data; do not add `--volumes`.

## Simple verification

1. Confirm all three services are healthy and `/system` reports all connections working.
2. Open dashboard, printer list, and a printer details deep link; refresh the browser.
3. Confirm editing and relocation still work and history stays correct.
4. Run `docker compose --profile maintenance run --rm --no-deps backup` and note the output filename.
5. Follow the [restore check](BACKUP-RESTORE.md#verify-by-restoring-to-a-disposable-database). Require exit code zero and review the restored record counts.
6. Copy the backup/checksum, matching release artifacts, and protected environment settings off-machine.

For a real server, follow [DEPLOYMENT.md](DEPLOYMENT.md). No remote Ubuntu VM, public endpoint, TLS certificate, scheduled backup, or off-machine storage has been configured automatically.

## Local rollback artifacts

The pre-update application images were retained as `printer-inventory-backend:before-phase6` and `printer-inventory-frontend:before-phase6`. The ignored local file `backups/before-phase6.compose.yml` selects these images and the previous frontend port while retaining the existing database volume. If an application rollback is needed, run from this workspace root:

```powershell
docker compose --project-directory . -f backups/before-phase6.compose.yml up -d --no-build --wait
```

This local rollback configuration is validated, but a live downgrade was not performed. It relies on the retained local images; archive them and the configuration off-machine for disaster recovery. Phase 6 has no schema changes, so this application rollback does not require restoring the database.

## Verified on 20 September 2026

- Base, production, browser-test, restore-check and local rollback configurations validated.
- All 11 browser tests passed under production limits and filesystem restrictions, including dashboard, CRUD, relocation, responsive layouts, deep links, security headers, cache policy and CSP checks. Results are in `.verification/browser/html/index.html` and `.verification/phase6/browser-tests.txt`.
- Production images built and all three running containers became healthy. Nginx configuration validation passed; the proxied health and dashboard APIs and frontend deep route returned HTTP 200.
- Container inspection confirmed non-root application users, read-only app roots, dropped capabilities and configured memory limits.
- `backups/inventory_20260920T083524Z_fcAiGI.dump` and its checksum were created by the new backup job. The archive restored successfully in the isolated project: 1 retained/current printer, 2 locations and 2 relocation records. The restore target guard rejected an application-database address. Report: `.verification/phase6/restore-check.txt`.
- The existing database volume was retained through container recreation. No production test records were added and no schema migration was required.
- The local `.env` uses release tag `phase6`; the existing database credentials were preserved. Previous images and a compatible local rollback configuration were retained. A live downgrade and actual Ubuntu/VM deployment were not performed.
