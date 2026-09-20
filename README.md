# Printer Inventory

A simple internal printer inventory application, built one phase at a time.

**Current scope: Phases 1–6.** The application includes a dashboard, printer management, search, filters, validation, safe deletion, transactional relocation, preserved transfer history, responsive layouts, production container settings, and backup/recovery tooling. There is no authentication or user management.

For an Ubuntu server or VMware VM, follow the [deployment guide](docs/DEPLOYMENT.md) and [backup/restore guide](docs/BACKUP-RESTORE.md). The [Phase 6 guide](docs/PHASE6.md) lists changes and verification. Use both Compose files for production; the quick start below uses the base development configuration.

Start with the [Phase 5 dashboard guide](docs/PHASE5.md) for counts, recent activity, changed files, and responsive testing. The dashboard is now the home page; printer management is at `/printers`.

See the [Phase 4 relocation guide](docs/PHASE4.md) for transfers, date rules, history, API examples, migration, and testing.

The [Phase 3 screen guide](docs/PHASE3.md) covers printer management. The [Phase 2 API guide](docs/PHASE2.md) covers request examples and database rules. Connection status is available at `/system`.

## Architecture

```text
Browser → React frontend → /api/ → Spring Boot → PostgreSQL
            Nginx in Docker               Java 21       persistent volume
```

In Docker, Nginx serves the frontend and proxies `/api/` to the backend. Only Nginx has a published port. During local development, Vite proxies `/api/` to the local backend. This keeps browser requests on the same origin without custom CORS configuration.

The backend uses Spring Web MVC, Spring Data JPA, Bean Validation, PostgreSQL's JDBC driver, and Gradle with Groovy configuration. Flyway applies versioned SQL migrations; Hibernate validates the resulting schema. The frontend uses React, TypeScript, Vite, Bootstrap 5, Axios, and React Router.

## Requirements

For the complete Docker setup:

- Docker Desktop with **Linux containers** on Windows, or Docker Engine on Ubuntu.
- Docker Compose v2 or newer (`docker compose version`).
- Internet access for the first image and dependency downloads.
- Port 8080 available, or a different `APP_PORT` in `.env`.

For local development without Docker:

- JDK 21; verify with `java -version` and `javac -version`.
- Node.js 24 LTS and npm; verify with `node --version` and `npm --version`.
- PostgreSQL 17 running locally.
- VS Code; Java and React/TypeScript extensions are optional.

Gradle is included through the wrapper (`gradlew` / `gradlew.bat`). You do not need to install it globally. Version compatibility references: [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html) and [Vite getting started](https://vite.dev/guide/).

## Folder structure

```text
printer-inventory/
├── backend/
│   ├── gradle/wrapper/              # Official Gradle wrapper
│   ├── gradlew / gradlew.bat
│   ├── build.gradle                # Java dependencies and build
│   ├── settings.gradle
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/example/printerinventory/
│       │   ├── PrinterInventoryApplication.java
│       │   ├── controller/           # Health, inventory, relocation, dashboard endpoints
│       │   ├── dto/                  # Validated requests and API responses
│       │   ├── entity/               # Printer, location, and status enum
│       │   ├── exception/            # Consistent API errors
│       │   ├── repository/           # JPA access and search predicates
│       │   └── service/              # Inventory rules and transactions
│       ├── main/resources/application.properties
│       ├── main/resources/db/migration/
│       └── test/java/com/example/printerinventory/
│           ├── HealthEndpointTests.java
│           └── InventoryIntegrationTests.java
├── frontend/
│   ├── src/
│   │   ├── api/                    # Shared Axios client and health request
│   │   ├── components/             # Small shared SVG icon component
│   │   ├── pages/                 # Dashboard, printer list/forms/details, system status
│   │   ├── hooks/                 # Cancellable resource loading
│   │   ├── utils/                 # Location/date formatting
│   │   ├── types/health.ts
│   │   ├── App.tsx                 # Layout and routes
│   │   ├── main.tsx
│   │   └── styles.css
│   ├── package.json / package-lock.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── index.html
│   ├── .env.example
│   ├── Dockerfile
│   └── nginx.conf
├── docker-compose.yml
├── docker-compose.production.yml   # Production limits, logging, read-only app containers
├── docker-compose.restore-check.yml # Isolated backup recovery verification
├── scripts/                       # Container-run backup and restore checks
├── docker-compose.dev.yml          # Optional localhost-only DB port
├── docker-compose.test.yml         # Separate disposable database tests
├── docs/PHASE2.md                  # CRUD API guide and manual test
├── docs/PHASE3.md                  # Screen guide and browser checks
├── docs/PHASE4.md                  # Relocation, history, and transaction checks
├── docs/PHASE5.md                  # Dashboard and responsive layouts
├── e2e/                           # Playwright browser tests (development only)
├── docker-compose.e2e.yml          # Isolated browser-test application
├── .env.example
├── .gitignore
└── README.md
```

## Quick start with Docker

Run from the project root. Start Docker Desktop first on Windows.

1. Create your environment file **only if `.env` does not already exist**:

   PowerShell:

   ```powershell
   Copy-Item .env.example .env
   ```

   Ubuntu/macOS:

   ```bash
   cp .env.example .env
   ```

2. Edit `.env` and replace `POSTGRES_PASSWORD=change_me` with your own password. Do not commit `.env`. Use a password without spaces or `$` to avoid Compose interpolation surprises.

3. Build and start:

   ```bash
   docker compose up -d --build --wait
   docker compose ps
   ```

4. Open **http://localhost:8080** for the dashboard, or **http://localhost:8080/printers** for printer management. Connection status is at **http://localhost:8080/system**. On another machine, use `http://SERVER-IP:8080`. Setting `APP_PORT=80` changes this to `http://SERVER-IP`.

The first build downloads dependencies and may take several minutes. PostgreSQL must become healthy before the backend starts; the backend must pass its database health check before the frontend starts.

For an Ubuntu VM, follow [DEPLOYMENT.md](docs/DEPLOYMENT.md), including the production override, internal bind address, firewall considerations, and verification. This application has no login and is intended for a trusted internal network. Docker-published ports require Docker-aware network restrictions; do not assume UFW alone restricts them.

## Verify Phase 1

1. `docker compose ps` should show `db`, `backend`, and `frontend` as healthy.
2. Open **http://localhost:8080/system**. It should show **All systems connected**, with three **Connected** badges.
3. Click **Refresh status**. The **Last checked** timestamp should update.
4. Open **http://localhost:8080/api/health**. Expect HTTP 200 and:

   ```json
   {
     "status": "UP",
     "api": "UP",
     "database": "UP",
     "message": "The backend and PostgreSQL are connected.",
     "checkedAt": "2026-09-18T00:00:00Z"
   }
   ```

   The actual timestamp will be the time of your request. PostgreSQL is checked by the backend, so this verifies frontend/proxy → backend → database communication.

5. Resize the browser to a phone width. The service cards should stack without horizontal scrolling.

Optional failure/recovery check on your development instance:

```bash
docker compose stop db
```

Refresh the page status. The backend should remain reachable, the database should show **Unavailable**, and `/api/health` should return HTTP 503. Restore it immediately afterward:

```bash
docker compose start db
```

Wait a few seconds and refresh again. All systems should return to connected. If you stop the backend instead, the page reports that the backend cannot be reached, and the database is **Not verified**.

## Local development from VS Code, without Docker

### 1. Create a local PostgreSQL database

Install and start PostgreSQL 17. Open `psql` as the PostgreSQL administrator (or use pgAdmin's query tool) and run these statements separately:

```sql
CREATE USER printerapp WITH PASSWORD 'replace_with_your_local_password';
CREATE DATABASE printer_inventory OWNER printerapp;
```

On startup, Flyway creates the Phase 2 inventory tables in this database. Existing Phase 1 volumes upgrade automatically; do not reset or delete the volume.

### 2. Start the backend in one terminal

PowerShell:

```powershell
cd backend
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:5432/printer_inventory'
$env:SPRING_DATASOURCE_USERNAME = 'printerapp'
$env:SPRING_DATASOURCE_PASSWORD = 'replace_with_your_local_password'
.\gradlew.bat bootRun
```

Ubuntu/macOS:

```bash
cd backend
export SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/printer_inventory'
export SPRING_DATASOURCE_USERNAME='printerapp'
export SPRING_DATASOURCE_PASSWORD='replace_with_your_local_password'
./gradlew bootRun
```

The backend listens on **http://localhost:8080/api/health**. Stop it with Ctrl+C. If `gradlew` is not executable on Linux, run `chmod +x gradlew` once.

**The root `.env` is read by Docker Compose, not automatically by Spring Boot or your terminal.** Set the Spring environment variables as above. Keep actual passwords out of tracked files.

### 3. Start the frontend in a second terminal

From the project root:

```bash
cd frontend
npm ci
npm run dev
```

Open **http://localhost:5173**. Vite forwards `/api` to the backend on port 8080. If you change `SERVER_PORT` for the backend, copy `frontend/.env.example` to `frontend/.env`, update `API_PROXY_TARGET`, and restart Vite.

### Optional: run only PostgreSQL in Docker

If you prefer not to install PostgreSQL locally, stop the full stack first and start just the database with the development override:

```bash
docker compose down
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d db --wait
```

Set the backend variables to the database name, user, and password in your root `.env`, then run the backend/frontend locally as above. The override publishes PostgreSQL on **127.0.0.1:5432 only**. If that port is in use, set `POSTGRES_PORT=5433` and use `jdbc:postgresql://localhost:5433/printer_inventory` locally.

Before returning to the full Docker stack, stop local development servers and run:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml down
docker compose up -d --build --wait
```

This avoids a port 8080 conflict between the local backend and Docker frontend.

## Builds and tests

Backend (inside `backend/`):

```powershell
.\gradlew.bat test bootJar
```

Linux/macOS equivalent: `./gradlew test bootJar`.

The three automated health endpoint tests use a mocked JDBC dependency and do not need a running database. PostgreSQL API integration tests are separate so ordinary builds do not depend on a running database. Run them from the project root:

```bash
docker compose -f docker-compose.test.yml up --build --abort-on-container-exit --exit-code-from tests
docker compose -f docker-compose.test.yml down
```

This uses a separate project and temporary PostgreSQL storage, with no application volume or host database port. A nonzero exit code indicates a test failure. See [Phase 2 testing](docs/PHASE2.md#tests) for details.

Frontend (inside `frontend/`):

```bash
npm ci
npm run build
```

This runs strict TypeScript checking and creates `dist/`. Docker builds run the backend tests and the frontend build as well.

## Everyday Docker commands

Run from the project root:

| Action | Command |
| --- | --- |
| Build and start | `docker compose up -d --build --wait` |
| View containers and health | `docker compose ps` |
| View logs | `docker compose logs --tail=100` |
| Follow backend logs | `docker compose logs -f backend` |
| Restart services | `docker compose restart` |
| Stop but keep containers | `docker compose stop` |
| Start existing containers | `docker compose start` |
| Normal shutdown (keeps data) | `docker compose down` |
| Start after shutdown | `docker compose up -d --wait` |
| Rebuild after changing code | `docker compose up -d --build --wait` |
| Validate configuration | `docker compose config --quiet` |

After `.env` changes, use `docker compose up -d`; `restart` does not reload container environment variables.

## Environment variables

| Variable | Default/example | Purpose |
| --- | --- | --- |
| `POSTGRES_DB` | `printer_inventory` | Database initialized on first Docker startup |
| `POSTGRES_USER` | `printerapp` | Database user initialized on first startup |
| `POSTGRES_PASSWORD` | `change_me` | Replace in root `.env`; no password is stored in code |
| `APP_PORT` | `8080` | Host port for the Nginx frontend |
| `APP_BIND_ADDRESS` | `0.0.0.0` | Host IPv4 interface; use the VM internal IP or `127.0.0.1` behind a host proxy |
| `APP_VERSION` | `local` | Frontend/backend image tag; use unique release tags in production |
| `APP_TIME_ZONE` | `Asia/Manila` | Business timezone used to reject future relocation dates |
| `POSTGRES_PORT` | `5432` | Local database port, development override only |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/printer_inventory` | Local backend connection URL; Compose sets its own URL using service name `db` |
| `SPRING_DATASOURCE_USERNAME` | `printerapp` | Local backend database user; Compose supplies `POSTGRES_USER` |
| `SPRING_DATASOURCE_PASSWORD` | No default | Required local backend password; Compose supplies `POSTGRES_PASSWORD` |
| `SERVER_PORT` | `8080` | Optional local backend port |
| `API_PROXY_TARGET` | `http://localhost:8080` | Optional Vite development proxy target in `frontend/.env` |

No database secrets are passed to the frontend. The browser calls same-origin `/api/` paths. The frontend container listens on port 8080; `APP_PORT` controls its published host port.

## Database and persistence

The named volume `printer-inventory_postgres_data` stores PostgreSQL data. `docker compose down`, restarts, and image rebuilds preserve it. Phase 2 adds `printers` and `locations`, plus Flyway's migration tracking table. `V1__create_inventory.sql` defines their foreign key, indexes, and uniqueness rules. Phase 4 adds `relocation_history` through `V2__create_relocation_history.sql`, preserving existing inventory. Hibernate validates the schema (`ddl-auto=validate`) instead of modifying it. Add a new migration for future changes; do not edit a migration after it has been applied.

PostgreSQL's initialization variables apply only to an **empty volume**. Changing the password, username, or database name in `.env` does not alter an existing database. Change credentials using PostgreSQL administration tools and then update `.env` to match.

**Permanent data deletion:** `docker compose down --volumes` deletes this project's database volume. This is not a normal shutdown command. Use it only when intentionally resetting the database and after making any required backup.

## API endpoints

| Method | Path | Behavior |
| --- | --- | --- |
| GET | `/api/health` | 200 when a real PostgreSQL query succeeds; 503 when the API is up but the database check fails |
| GET | `/api/dashboard` | Non-deleted inventory counts and five recent additions/transfers; see the Phase 5 guide |
| GET / POST | `/api/locations` | List / create locations |
| GET / PUT / DELETE | `/api/locations/{id}` | View / update / delete an unused location |
| GET / POST | `/api/printers` | Search and filter a paginated list / create a printer |
| GET | `/api/printers/search` | Alias of the printer list endpoint |
| GET / PUT / DELETE | `/api/printers/{id}` | View / update / soft-delete a printer |
| POST | `/api/printers/{id}/relocate` | Atomically record a transfer and update current location |
| GET | `/api/printers/{id}/relocations` | Newest-first history, retained after printer deletion |

The health response includes `status`, `api`, `database`, `message`, and UTC `checkedAt`. Health responses are not cached. Nginx also provides `/healthz` for its own container health check; it only checks Nginx, not the database. Inventory DTOs and request examples are documented in the [Phase 2 guide](docs/PHASE2.md).

## Troubleshooting

- **Docker daemon unavailable:** start Docker Desktop and select Linux containers. On Ubuntu, check the Docker service and your user's Docker access.
- **Port already allocated:** stop the conflicting service or change `APP_PORT`; don't run the local backend and default Docker frontend on 8080 together.
- **Backend unhealthy or database unavailable:** inspect `docker compose logs --tail=100 backend db`. Check credentials, database startup, and the existing-volume password rule above.
- **Frontend reports the API is unreachable:** confirm the backend is running. In local development, check the Vite proxy target and backend port. In Docker, inspect `docker compose logs frontend backend`.
- **First build cannot download dependencies:** verify Internet access, DNS, and any organizational proxy settings, then rebuild.
- **Java version mismatch:** set `JAVA_HOME` to a JDK 21 installation and restart the terminal.
- **`npm` not found:** install Node.js 24 LTS and open a new terminal, or use the complete Docker workflow.
- **Frontend dependencies installed in Docker on Windows:** native build dependencies differ between Linux and Windows. Run `npm ci` locally before starting Vite on Windows; it refreshes `node_modules` for your OS.
- **Invalid or empty health response:** the UI reports a failed connection instead of showing a false success for an HTML proxy error page.

## Backups

The preferred Phase 6 workflow runs PostgreSQL tools inside a maintenance container:

```bash
docker compose --profile maintenance run --rm --no-deps backup
```

It saves a unique archive and checksum. Follow [BACKUP-RESTORE.md](docs/BACKUP-RESTORE.md) to verify the archive in a disposable database, schedule backups, and recover into a new database. No automated schedule or off-machine copy is installed.

Before upgrades, back up the database and keep a protected copy of your `.env`. A Docker volume is persistence, not a backup. Store backup copies outside the VM and periodically test restoring them.

Create a PostgreSQL custom-format dump inside the container, then copy it to the host. This avoids corrupting a binary dump through older PowerShell output redirection:

```bash
mkdir backups
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -f /tmp/printer_inventory.dump'
docker compose cp db:/tmp/printer_inventory.dump ./backups/printer_inventory.dump
```

Rename each backup with its date so that subsequent backups do not overwrite it. The `backups/` directory is ignored by Git.

## Rollback

For production, use the release-image and matching-configuration procedure in [DEPLOYMENT.md](docs/DEPLOYMENT.md#rollback). Phase 6 changes Nginx's internal port, so previous frontend images require their previous Compose configuration. The general source-based procedure below is for development.

1. Keep a copy or version-control commit of the previously working source, the matching environment settings, and a database backup before making an upgrade.
2. Run `docker compose down` (without `--volumes`).
3. Restore the previous source and its matching environment settings.
4. Run `docker compose up -d --build --wait` and repeat the Phase 1 verification.

Phase 2 adds tables without changing Phase 1's health query, so reverting to the Phase 1 application can leave these tables intact. Do not drop the inventory tables or delete Flyway's history to roll back the application. For future migrations, check schema compatibility before rollback. If a database restore is necessary, first preserve the current database and test the restore in a separate database. A custom-format dump can be restored with PostgreSQL's `pg_restore`; never overwrite the only copy of your working data without a verified backup.

## Completed phases

All six requested phases are implemented. See [Phase 6](docs/PHASE6.md) for production configuration and verified recovery tooling. Actual Ubuntu/VM deployment requires your server and network configuration; see the deployment guide.

## Verification in this workspace — 18 September 2026

- Windows JDK 21: `gradlew.bat test bootJar` passed; 3 tests, 0 failures or errors.
- Docker backend build: Java compilation, the same 3 tests, and executable JAR creation passed.
- Docker frontend build: `npm ci`, strict TypeScript checking, and the Vite production build passed.
- Both Compose configurations validated. The full three-service stack started with all containers healthy.
- The frontend returned HTTP 200, Nginx configuration validation passed, and the proxied `/api/health` returned HTTP 200 with PostgreSQL `UP`.
- Stopping the development database returned HTTP 503 with API `UP` and database `DOWN`; restarting it restored HTTP 200.
- A normal `docker compose down` followed by startup preserved the database volume and the PostgreSQL cluster identifier, and all services became healthy again.
- **Visual verification remains manual:** Computer Use stopped because it could not reliably identify the browser URL. The responsive layout and browser refresh interaction were implemented but were not visually verified. Follow the browser checks above, including resizing to phone/tablet widths.

A root `.env` with a randomly generated local database password was created for these checks and is ignored by Git. Keep it; do not overwrite it with `.env.example` while using this existing database volume. Node.js was not available on the host command path, so the frontend was built using its Node.js Docker image. The stack is left running at **http://localhost:8080**.
