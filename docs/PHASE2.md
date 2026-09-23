# Phase 2: printer and location APIs

Phase 2 adds the backend inventory API described here. Printer management screens are documented in the [Phase 3 guide](PHASE3.md); connection status is at `/system`. There is no authentication. Relocation is now documented in the [Phase 4 guide](PHASE4.md).

## What changed

Requests follow **controller → service → repository → PostgreSQL**. Controllers accept validated request DTOs, services apply the rules inside transactions, and response DTOs keep database entities out of the API.

| Files | Purpose |
| --- | --- |
| `backend/src/main/resources/db/migration/V1__create_inventory.sql` | Creates printer and location tables, constraints, and indexes |
| `backend/src/main/java/com/example/printerinventory/entity/` | Printer, location, and six printer statuses |
| `backend/src/main/java/com/example/printerinventory/repository/` | Database access and search/filter predicates |
| `backend/src/main/java/com/example/printerinventory/dto/` | Request validation, response shapes, pagination, and errors |
| `backend/src/main/java/com/example/printerinventory/service/` | CRUD, normalization, duplicate checks, safe deletion, version checks |
| `backend/src/main/java/com/example/printerinventory/controller/` | REST endpoints |
| `backend/src/main/java/com/example/printerinventory/exception/` | Central error handling |
| `backend/src/test/java/com/example/printerinventory/InventoryIntegrationTests.java` | Tests using real PostgreSQL and the full Spring application |
| `backend/build.gradle`, `backend/Dockerfile` | Migration dependencies and separate integration-test task/container target |
| `backend/src/main/resources/application.properties` | Hibernate schema validation |
| `docker-compose.test.yml` | Isolated test database and test runner |
| `README.md`, `docs/PHASE2.md` | Setup, API examples, and phase notes |

Flyway is the one added infrastructure library. It records each SQL migration so startup can apply changes once without rebuilding the database. Spring Boot documents this setup in its [database initialization guide](https://docs.spring.io/spring-boot/4.1/how-to/data-initialization.html). Keep applied migration files unchanged; future changes belong in `V2__...sql`, `V3__...sql`, etc.

## Run it

Keep your existing root `.env` and database volume. From the project root:

```powershell
docker compose up -d --build --wait
docker compose ps
```

Open **http://localhost:8080/system** for connectivity status, **http://localhost:8080/api/locations** for locations, and **http://localhost:8080/api/printers** for the printer list API. Fresh installations return an empty array for locations and a page with empty `content` for printers.

For local development, the same environment variables and `gradlew.bat bootRun` / `npm run dev` commands in the main README still apply. Back up any important database before an upgrade. This migration only adds tables and does not reset the volume.

## Data rules

- Required printer fields: `brand`, `model`, `stickerNumber`, `locationId`, `status`.
- Optional printer fields: `serialNumber`, `remarks`. Unknown or blank serial numbers become `null`; multiple printers may have an unknown serial number.
- Sticker numbers and supplied serial numbers are trimmed, stored in uppercase, and unique without regard to case. PostgreSQL enforces uniqueness even if two requests arrive together.
- Required location field: `department`. `building`, `floor`, `room`, and `description` are optional. A printer must reference an existing location.
- Length limits: brand 100; model and serial 120; sticker 80; remarks 2000; department/building 120; floor 50; room 80; description 1000.
- Status values: `ACTIVE`, `UNDER_REPAIR`, `FOR_REPAIR`, `STORAGE`, `RETIRED`.
- `createdAt` and `updatedAt` are assigned by the backend and returned in UTC.
- Responses include `version`. Supply the latest value on **PUT**. A missing version returns 400; an out-of-date version returns 409 so an old form cannot overwrite a newer edit. POST does not require it.
- PUT is a complete editable-field replacement, not a partial update. Omitted optional fields are cleared.

### Safe deletion and location changes

Deleting a printer sets `deleted_at`; it does **not** physically remove the row. Deleted printers disappear from lists/search and return 404 for later GET/PUT/DELETE requests. Their identifiers remain reserved. There is no restore or permanent-delete endpoint in this phase.

This preserves the printer row and any relocation history. Phase 4 adds the `relocation_history` table and transactional transfers; see its guide for retained-history access.

A location referenced by any printer, including a deleted printer, cannot be edited or deleted (409). This prevents changing a printer's recorded location indirectly. Create a new location if needed. Unreferenced locations support full update and deletion. A database foreign key also prevents deleting referenced locations.

PUT on a printer must keep its current `locationId`. A different location returns 409 instead of silently overwriting it. Actual transfers use the Phase 4 relocation endpoint.

## Endpoints and responses

| Method | Path | Success |
| --- | --- | --- |
| GET | `/api/locations` | 200, sorted array of locations |
| POST | `/api/locations` | 201, location DTO and `Location` header |
| GET | `/api/locations/{id}` | 200, location DTO |
| PUT | `/api/locations/{id}` | 200, updated location DTO |
| DELETE | `/api/locations/{id}` | 204, empty body |
| GET | `/api/printers` | 200, paginated response |
| GET | `/api/printers/search` | Same behavior as `/api/printers` |
| POST | `/api/printers` | 201, printer DTO and `Location` header |
| GET | `/api/printers/{id}` | 200, printer DTO with nested `location` |
| PUT | `/api/printers/{id}` | 200, updated printer DTO |
| DELETE | `/api/printers/{id}` | 204, empty body; retains database row |

Printer list parameters can be combined:

| Parameter | Behavior |
| --- | --- |
| `search` | Case-insensitive substring in sticker, serial, brand, or model; max 120 characters |
| `brand` | Case-insensitive exact brand; max 100 characters |
| `locationId` | Positive numeric location ID |
| `status` | One of the six exact uppercase enum values |
| `page` | Zero-based page, default 0; range 0–1000000 |
| `size` | Page size, default 20; range 1–100 |

Results are ordered by newest ID first. `%` and `_` in search are treated literally, not as database wildcards. Values are passed as query parameters, not concatenated into SQL.

Example: `/api/printers?search=epson&status=ACTIVE&brand=Epson&page=0&size=20`

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

Errors use a consistent shape:

```json
{
  "timestamp": "2026-09-19T00:00:00Z",
  "status": 400,
  "message": "Please correct the highlighted fields.",
  "path": "/api/printers",
  "fieldErrors": { "brand": "Brand is required" }
}
```

400 means invalid input; 404 means missing/deleted record; 409 means duplicate identifier, protected location, or conflicting edit. Database availability failures return 503. Raw SQL, connection strings, and database error messages are not exposed in API responses.

## Simple manual API test (PowerShell)

These commands create a clearly named demo record in your application database. Soft-deleting the printer later retains the row and its location by design. Use the isolated automated tests below if you do not want any demo records in your application database.

Set the URL (`8080` for Docker, or your local backend port):

```powershell
$api = 'http://localhost:8080/api'
$demoId = 'DEMO-' + [Guid]::NewGuid().ToString('N').Substring(0, 10)
```

1. Create a location:

```powershell
$locationBody = @{
    department = 'Demo ICT'
    building = 'Main Building'
    floor = '1'
    room = '101'
    description = 'Phase 2 API test'
} | ConvertTo-Json
$location = Invoke-RestMethod "$api/locations" -Method Post -ContentType 'application/json' -Body $locationBody
$location
```

2. Create a printer:

```powershell
$printerFields = @{
    brand = 'Epson'
    model = 'L5290'
    serialNumber = "$demoId-SERIAL"
    stickerNumber = $demoId
    locationId = $location.id
    status = 'ACTIVE'
    remarks = 'Phase 2 API test'
}
$printer = Invoke-RestMethod "$api/printers" -Method Post -ContentType 'application/json' -Body ($printerFields | ConvertTo-Json)
$printer
```

Expect the assigned ID, the location details, timestamps, and `version: 0`.

3. Read, search, and filter:

```powershell
Invoke-RestMethod "$api/printers/$($printer.id)"
(Invoke-RestMethod "$api/printers?search=$demoId").content
(Invoke-RestMethod "$api/printers?brand=Epson&status=ACTIVE&locationId=$($location.id)").content
```

4. Edit the printer:

```powershell
$printerFields.status = 'UNDER_REPAIR'
$printerFields.remarks = 'Paper feed issue'
$printerFields.version = $printer.version
$printer = Invoke-RestMethod "$api/printers/$($printer.id)" -Method Put -ContentType 'application/json' -Body ($printerFields | ConvertTo-Json)
$printer
```

Expect the new status, remarks, and an incremented version. Repeating the previous body without updating its version returns 409. Repeating the create request returns 409 for the duplicate sticker number.

5. Delete only this demo printer:

```powershell
Invoke-RestMethod "$api/printers/$($printer.id)" -Method Delete
(Invoke-RestMethod "$api/printers?search=$demoId").totalElements
```

Expect no deletion response body and a search count of 0. Getting that printer again returns 404. Deleting its location returns 409 because the retained printer still references it.

## Tests

From the project root:

```powershell
docker compose -f docker-compose.test.yml up --build --abort-on-container-exit --exit-code-from tests
docker compose -f docker-compose.test.yml down
```

The test runner builds Java code, runs the three health unit tests, and runs the full API integration suite against PostgreSQL 17. Flyway applies the migration to a clean test database, and Hibernate validates it. API tests roll back their records. The separate `printer-inventory-tests` project uses temporary database storage and no application volume. Running this command does not stop the normal application.

Coverage includes:

- Actual database health and migration success.
- Create, read, edit, and delete for locations/printers.
- All six statuses and optional serial numbers.
- Search across all four required fields, combined filters, and pagination.
- Required-field validation, malformed JSON, invalid statuses and IDs, invalid page sizes, and missing references.
- Stale/missing update versions, unique identifiers, retained deleted records, and protected locations.
- Rejection of silent location changes and database-level uniqueness enforcement.

To save reports before removing the test containers:

```powershell
New-Item -ItemType Directory -Force .verification | Out-Null
docker compose -f docker-compose.test.yml cp tests:/app/build/reports/tests .verification/phase2-tests
```

Open the copied `integrationTest/index.html` or `test/index.html`. `.verification/` is ignored by Git.

For a local Java build without database tests:

```powershell
cd backend
.\gradlew.bat test bootJar
```

To run `gradlew.bat integrationTest` directly, set `SPRING_DATASOURCE_*` to a dedicated empty test database first. Never use the real inventory database for integration tests.

## Scope of the next phase

Phase 3 adds the inventory screens and delete confirmation. Relocation, relocation history, and dashboard APIs are intentionally not part of Phase 2.

## Verified on 19 September 2026

- Backend compilation and executable JAR build passed.
- All **24 tests passed**: 3 health tests and 21 PostgreSQL integration cases, with no failures, errors, or skipped tests.
- The final integration test task runs every time, even when Gradle's other build outputs are up to date.
- The existing application database upgraded to migration V1 successfully, without replacing its volume.
- All three application containers are healthy.
- Live requests through Nginx returned 200 for health, printer search, and locations; 400 with field errors for an invalid create request; and 404 for a missing printer.
- CRUD test data was created only in the isolated test database. No demo inventory records were added to the running application.
- The frontend was unchanged; its existing production build was reused. No new visual verification was needed for these backend changes.

The test reports are saved locally under `.verification/phase2-tests/`. The test containers were removed afterward. The application remains running at **http://localhost:8080**.
