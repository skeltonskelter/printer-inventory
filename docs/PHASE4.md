# Phase 4: relocation and history

Phase 4 adds printer transfers with permanent location history. The dashboard was subsequently added in [Phase 5](PHASE5.md). No login or production dependencies were added in Phase 4.

## Use the application

Open http://localhost:8080/printers. Choose **Relocate** from a printer row/card or its details page. Select a different destination, enter the actual relocation date, and optionally add transfer remarks. **New location** can create and select a destination without discarding the form. After saving, the details page shows the new current location and newest-first history. Cancel leaves the printer unchanged.

Printer remarks and transfer remarks are separate. Relocation does not change brand, model, identifiers, status, or the printer's creation date. Errors retain the entered values. If a stale-record or network error occurs, check the printer details before retrying; a lost response may follow a successful transfer.

## Rules and implementation

- A transfer must use a different, existing destination. The current location is excluded from the form.
- Relocation date is required, cannot be in the future, and cannot precede the latest transfer. Same-day transfers and returning to an earlier location are allowed. The first transfer can be backdated.
- The backend evaluates today in `APP_TIME_ZONE`, default `Asia/Manila`. The date input initially uses the browser's local date; the server enforces the configured business date. Dates are stored as SQL `DATE`; recorded timestamps are separate UTC instants.
- The service locks the printer row and checks the submitted version. Two requests using the same version cannot both transfer it. Reload details after a conflict.
- History insertion and current-location update share one database transaction. If either fails, both roll back. Each history record stores the actual previous and new location.
- History is read-only through the API. Locations referenced by current printers or history cannot be edited or deleted, preserving their historical labels. Printer deletion remains a soft deletion; history stays in the database and readable through its history endpoint.
- History is ordered by relocation date descending, then record ID descending for same-day transfers. The current UI lists all history for the selected printer.

Spring's method-validation errors are mapped back to field messages, including request bodies on endpoints with validated path parameters. Reference: [Spring ParameterErrors](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/validation/method/ParameterErrors.html).

## API

`POST /api/printers/{id}/relocate` accepts:

```json
{
  "newLocationId": 2,
  "relocationDate": "2026-09-19",
  "remarks": "Transferred upon department request",
  "version": 0
}
```

Use the current printer's `version` from `GET /api/printers/{id}` and a real destination ID. Success returns HTTP 200 with the updated printer and incremented version. Invalid input/future dates return 400; missing destinations, missing printers, or relocation of deleted printers return 404; stale versions, unchanged destinations, or out-of-order dates return 409. Remarks are optional, trimmed, and limited to 2,000 characters.

`GET /api/printers/{id}/relocations` returns an array of `id`, `printerId`, `previousLocation`, `newLocation`, `relocationDate`, `remarks`, and `createdAt`. Both locations include their full location fields. No transfers returns `[]`; an unknown printer returns 404. A soft-deleted printer's history remains readable here even though its normal details endpoint returns 404.

Ordinary `PUT /api/printers/{id}` still rejects location changes: use the relocation endpoint so the transfer is recorded.

## Main files

| Files | Purpose |
| --- | --- |
| `backend/src/main/resources/db/migration/V2__create_relocation_history.sql` | Additive history table, foreign keys, indexes, different-location constraint |
| `backend/src/main/java/com/example/printerinventory/entity/RelocationHistory.java` | History entity with immutable mapped fields |
| `.../dto/RelocationRequest.java`, `RelocationResponse.java` | Validated request and history response |
| `.../controller/RelocationController.java`, `.../service/RelocationService.java` | Endpoints and atomic transfer rules |
| `.../repository/RelocationHistoryRepository.java`, `PrinterRepository.java` | History lookup and printer row locking |
| `.../service/LocationService.java`, `.../entity/Printer.java` | Preserve referenced locations and update current location |
| `.../config/TimeConfiguration.java`, `application.properties`, `.env.example`, `docker-compose.yml` | Configurable business timezone |
| `frontend/src/pages/RelocatePrinterPage.tsx` | Transfer form, new destination, validation and recovery |
| `frontend/src/components/RelocationHistoryList.tsx` | Empty/loading/error states and responsive history |
| Frontend details/list pages, `App.tsx`, API/types, `styles.css` | Relocation actions, route, request types, layout |
| `backend/src/test/java/com/example/printerinventory/RelocationIntegrationTests.java` | Real-commit, rollback, validation, retention and concurrency tests |
| `e2e/relocation.spec.js` | Browser transfer and history workflows |

## Build and run

Back up the existing database using the README backup commands, then run from the project root:

```powershell
docker compose up -d --build --wait
docker compose ps
```

Flyway applies V2 to existing V1 databases without resetting data. V1 is unchanged. Do not use `down --volumes`, delete Flyway history, or modify previously applied migrations.

V2 is additive, but the old application does not understand history protections. After any transfers exist, keep the Phase 4 backend for normal use. For a rollback, stop writes and test the previous release against a copy first; preserve a current backup and do not drop history to force compatibility.

## Automated checks

```powershell
docker compose -f docker-compose.test.yml up --build --abort-on-container-exit --exit-code-from tests
docker compose -f docker-compose.test.yml down
docker compose -f docker-compose.e2e.yml up --build --abort-on-container-exit --exit-code-from browser-tests
docker compose -f docker-compose.e2e.yml down
```

Both test projects use separate temporary PostgreSQL databases and no application volume or published ports. The relocation integration suite requires a database named `inventory_test` as a safeguard. Do not point integration tests at the application database.

The backend suite contains 3 health tests and 28 integration tests. Seven new tests cover chained and same-day transfers, field/date validation, stale versions, retained deleted-printer history, protected historical locations, rollback after a deliberately injected database failure, and simultaneous requests. The rollback test inserts history, forces the subsequent printer update to fail, and verifies that neither change persisted.

The browser suite contains 8 tests, including all 5 previous inventory tests. New tests exercise transfer/return/reload/history, stale form recovery, mobile transfer with a newly created location, future-date rejection, cancellation, and history retention after deletion. Reports/screenshots are in `.verification/browser/`; open `html/index.html`. These artifacts are ignored by Git.

## Manual check

1. Add a test printer in location A, then open its details and confirm the empty history.
2. Relocate it to B with today's date and remarks. Confirm current location B and a history entry from A to B.
3. Reload, then move it back to A. Confirm both entries, newest first.
4. Open the form in two tabs. Save one, then submit the other. The second must report a conflict without adding history.
5. Try a future date. Confirm the printer and history remain unchanged.
6. Check the form and history on a phone-width screen.

Manual records use the real database and are retained after soft deletion. Use the isolated automated suite for disposable test data.

## Verification on 19 September 2026

- All 31 backend tests passed: 3 health tests, 21 existing inventory integration tests, and 7 relocation integration tests.
- All 8 Chromium browser tests passed against the real isolated backend and PostgreSQL. Desktop and phone relocation/history screenshots were inspected.
- Strict TypeScript checking and the Vite production build passed.
- Backend reports are saved in `.verification/phase4-tests/`; browser results are in `.verification/browser/html/index.html`.
- A pre-migration custom-format backup was saved as `backups/printer_inventory_phase4_20260919.dump` and its archive listing was verified. A full restore was not performed.
- Test fixtures were confined to separate databases; both test projects were removed after verification.

Final live verification on 20 September 2026: the existing database upgraded from Flyway V1 to V2 successfully. Backend, frontend, and database containers are healthy. The proxied health endpoint reports API and database `UP`, and the printer list and relocation frontend routes return HTTP 200. The application is running at http://localhost:8080.
