# Deploy to an Ubuntu server or VMware VM

This guide deploys the existing monolith: Nginx → Spring Boot → PostgreSQL. There is no login. Give access only to the intended internal network. No server was provisioned remotely by this project; follow these steps on your own VM.

## 1. Prepare the VM

Use a supported Ubuntu Server release, with a practical starting allocation of 2 vCPUs, 4 GB RAM, and 30 GB disk plus room for database growth and backups. Builds need additional transient RAM and Internet access. Adjust capacity after observing real workload; these are starting values, not a load-tested capacity guarantee.

Give the VM a stable internal IP or DHCP reservation. VMware bridged networking can make the VM reachable on the LAN; with NAT, arrange the required host port forwarding instead. Confirm routing from a client before troubleshooting the application.

Install Docker Engine and the Compose plugin using the [official Ubuntu installation guide](https://docs.docker.com/engine/install/ubuntu/). Verify:

```bash
docker version
docker compose version
```

Use an account authorized to operate Docker. Membership in the Docker group grants powerful host access; do not grant it to ordinary application users.

Copy the source into one stable directory, for example `/opt/printer-inventory`, owned by your deployment operator. Keep the Compose project name `printer-inventory` so updates find the same named database volume. Do not upload local `.env`, build folders, `node_modules`, or backups into a source repository.

## 2. Configure the environment

From the project root, on a **new installation only**:

```bash
cp .env.example .env
chmod 600 .env
mkdir -p backups
chmod 700 backups
```

Edit `.env`:

```dotenv
POSTGRES_DB=printer_inventory
POSTGRES_USER=printerapp
POSTGRES_PASSWORD=replace_with_a_long_random_password
APP_BIND_ADDRESS=192.168.1.50
APP_PORT=80
APP_TIME_ZONE=Asia/Manila
APP_VERSION=2026-09-20-r1
```

Replace the sample IP with an IP actually assigned to this VM. Use a random password, for example 32 hexadecimal bytes from `openssl rand -hex 32`. Never overwrite an existing `.env` with the example: PostgreSQL initialization variables do not change existing database credentials.

`APP_VERSION` tags the frontend/backend images so old releases can be retained. Use a new tag for each release; do not rebuild an old release tag. Keep matching source/configuration with every image release. Base images use maintained version tags, so rebuilding later can produce a different image. For exact rollback, save the built images, rather than relying on rebuilding old source.

`APP_BIND_ADDRESS=127.0.0.1` allows a host reverse proxy to be the entry point. `0.0.0.0` listens on every IPv4 interface. The database and backend have no published host ports. Do not include `docker-compose.dev.yml` on the production server.

Docker-published ports can bypass UFW's usual rules. Enforce access restrictions at your network firewall and with Docker-aware host firewall rules, and test from both an allowed and a disallowed client. See [Docker firewall documentation](https://docs.docker.com/engine/network/packet-filtering-firewalls/). Merely adding a UFW rule is not enough to prove the port is restricted.

The provided entry point is HTTP. If your organization requires HTTPS, terminate TLS at its managed reverse proxy using a real hostname/certificate and proxy to the VM's restricted HTTP entry point. TLS and remote network setup require your site's address and certificate and have not been configured here. Do not expose this no-login application directly to the public Internet.

## 3. Build and start the production configuration

Always include **both** Compose files when operating production:

```bash
docker compose -f docker-compose.yml -f docker-compose.production.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.production.yml build --pull
docker compose -f docker-compose.yml -f docker-compose.production.yml up -d --no-build --wait --wait-timeout 180
docker compose -f docker-compose.yml -f docker-compose.production.yml ps
```

Avoid printing full `docker compose config` output into shared logs: it includes resolved database credentials. `config --quiet` validates without printing them.

Open `http://VM-IP` when using port 80, or `http://VM-IP:8080` with the default port. The dashboard is the home page. Flyway automatically applies pending migrations without deleting the data volume.

The production override adds:

- Read-only backend/frontend filesystems, temporary writable `/tmp`, dropped Linux capabilities, and no privilege escalation.
- Non-root Java and Nginx processes. Nginx listens on container port 8080; the configured host port is independent.
- Memory/CPU/process limits: database 768 MB/1 CPU, backend 768 MB/1 CPU, frontend 128 MB/0.5 CPU. Java's maximum heap is 65% of its container memory, leaving space for non-heap memory. Tune limits if monitoring shows pressure.
- JSON log rotation at 10 MB × 3 files per service, health checks, restart-on-exit policies, and graceful-stop periods.

The database remains writable on its persistent volume. Health checks show readiness, but Docker does not automatically restart a process solely because it becomes unhealthy. Investigate and repair the cause.

Nginx serves hashed assets with long-lived caching and HTML with revalidation, supports deep React routes, forwards `/api/` requests, re-resolves backend DNS after recreation, limits request bodies to 1 MB, hides its version, and supplies security headers. The content policy permits same-origin scripts and connections; inline styles are allowed for Bootstrap. Header snippets are repeated in cache-setting locations because [Nginx header inheritance](https://nginx.org/en/docs/http/ngx_http_headers_module.html) is replaced when a location defines its own headers.

## 4. Verify from the VM and a client

```bash
curl --fail http://VM-IP/api/health
curl --fail http://VM-IP/api/dashboard
docker compose -f docker-compose.yml -f docker-compose.production.yml exec -T frontend nginx -t
docker compose -f docker-compose.yml -f docker-compose.production.yml logs --tail=100
docker stats --no-stream
df -h
```

Use your real IP and port. Health must return API/database `UP`; all three containers should be healthy. Visit dashboard, printer list and a deep link after browser refresh. Create a test printer/location, edit/search it, transfer it, confirm history, and check a phone/tablet. If you delete the test printer, its row/history remain intentionally retained.

Automated tests use disposable data instead:

```bash
docker compose -f docker-compose.test.yml up --build --abort-on-container-exit --exit-code-from tests
docker compose -f docker-compose.test.yml down
docker compose -f docker-compose.e2e.yml up --build --abort-on-container-exit --exit-code-from browser-tests
docker compose -f docker-compose.e2e.yml down
```

The browser-test stack inherits production resource and filesystem restrictions. Reports are under `.verification/browser/html/index.html`.

## 5. Operate and update

Use the same two-file prefix for `logs`, `ps`, `stop`, `start`, `restart`, `up`, and `down`. Normal `down` keeps the named volume. **Do not use `down --volumes`** unless intentionally deleting the database. Do not change project/volume names during a release.

Before an update:

1. Run and verify a backup using [BACKUP-RESTORE.md](BACKUP-RESTORE.md). Copy it off the VM.
2. Retain the current source/configuration, protected `.env`, and exact frontend/backend images. Record the PostgreSQL image digest too. Do not combine an application release with an untested PostgreSQL major-version change.
3. Run the test suites for the new release. Set a new `APP_VERSION`, build, and schedule the brief service recreation. Run `up --no-build --wait` using both files.
4. Repeat health, dashboard, deep-link and workflow checks. Keep the previous release until the new one is accepted.

Example image archive for a release tagged `2026-09-20-r1`:

```bash
docker image save -o /secure-backups/printer-inventory-2026-09-20-r1.tar printer-inventory-backend:2026-09-20-r1 printer-inventory-frontend:2026-09-20-r1
```

Create the destination directory with suitable permissions first. Source/configuration and the database dump are separate artifacts; the image archive does not contain your database volume or `.env`.

## Rollback

For an application-only rollback with a compatible schema:

1. Preserve a fresh backup and stop new application writes (`stop frontend backend` with both files).
2. Restore the previous source and Compose/Nginx configuration, and use its matching `APP_VERSION` and environment settings. Keep the database credentials matching the existing database.
3. Load archived images if needed: `docker image load -i /secure-backups/printer-inventory-2026-09-20-r1.tar`.
4. Start the previous images with both matching Compose files and `up -d --no-build --wait`. Do not rebuild the old tag. Recheck health and workflows.

Phase 6 changes no database schema. It does change Nginx's internal port from 80 to 8080: a Phase 5 frontend image requires its Phase 5 Compose port/healthcheck configuration. Always roll back configuration together with images.

If a migration makes the previous version incompatible, test recovery into a **new database** using the backup guide, then switch the application connection during a maintenance window. Never delete Flyway history or edit applied migrations to force a rollback. Restoring an older backup loses later writes; preserve the current database and account for those writes before switching.

## Troubleshooting

- **Cannot bind host IP:** use an IP assigned to the VM, or the default bind address for local testing.
- **Unhealthy backend:** inspect backend/database logs, credentials and Flyway errors. Confirm memory limits are adequate; inspect `docker stats` and container OOM status.
- **Read-only filesystem errors:** only `/tmp` is writable in the app containers. Application data belongs in PostgreSQL; do not add ad-hoc writable source mounts.
- **502 after recreation:** allow the 10-second DNS refresh, then check backend health and service names.
- **Backup permission denied:** the maintenance job writes protected files as container root. On Linux, use the deployment operator's authorized `sudo` access to copy/protect backups; do not make backups world-readable.
- **Disk filling:** check data, backups and image archives; log rotation does not prune backups or old images. Keep required rollback images before manual cleanup.
