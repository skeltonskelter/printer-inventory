# Phase 5: dashboard and responsive layouts

The dashboard is the home page at `/dashboard`. The printer list remains at `/printers`, with system connection checks at `/system`. Production operations were subsequently added in [Phase 6](PHASE6.md).

## What changed

Six summary cards show total printers, Active, Under Repair, Storage, Retired, and relocated printers. Count links open the corresponding filtered printer list. Additional links show For Repair and Disposed counts, so all six statuses are represented. Relocated printers are counted once per printer, regardless of the number of transfers. This figure overlaps the status counts; it is not another status.

Every count and recent-activity list excludes soft-deleted printers. Their relocation history is still preserved by Phase 4. A retired or disposed printer remains in inventory until deleted.

The dashboard displays the latest five added printers and latest five recorded transfers. The transfer list is ordered by the time the transfer was recorded, then ID, and also displays the actual transfer date. One printer can appear more than once in this activity list. Each sticker links to printer details and its full history.

The dashboard loads on entry and has a **Refresh dashboard** button for changes made elsewhere. Loading and error states replace the cards, avoiding misleading zero counts on a failed request. Empty installations show an **Add your first printer** action.

On tablets up to 991px, navigation moves above the content to give forms and cards the full width. Phone navigation is compact, buttons and inputs have larger touch targets, and phone input text is 16px. Dashboard cards use three columns on desktop, two on tablets and most phones, and one on narrow 320px screens. Activity panels stack on phones. Printer lists continue using cards below the desktop table breakpoint.

## Backend design

`GET /api/dashboard` returns:

```json
{
  "totalPrinters": 0,
  "statusCounts": {
    "ACTIVE": 0, "UNDER_REPAIR": 0, "FOR_REPAIR": 0,
    "STORAGE": 0, "RETIRED": 0, "DISPOSED": 0
  },
  "relocatedPrinters": 0,
  "recentlyAdded": [],
  "recentTransfers": [],
  "checkedAt": "2026-09-20T00:00:00Z"
}
```

`recentlyAdded` contains existing printer response DTOs. Each `recentTransfers` item contains a `printer` DTO and a `relocation` DTO. The endpoint uses `Cache-Control: no-store`.

The controller delegates to a service and JPA repositories. Counts are aggregated in PostgreSQL, and each activity query is limited to five records; the API does not load the entire inventory. A read-only repeatable-read transaction keeps counts and activity in one database snapshot. There are no new dependencies or database migrations.

## Files

Backend files are under `backend/src/main/java/com/example/printerinventory/`:

| File | Change |
| --- | --- |
| `controller/DashboardController.java` | New dashboard endpoint |
| `service/DashboardService.java` | Consistent counts and bounded recent activity |
| `dto/DashboardResponse.java` | Dashboard response contract |
| `repository/PrinterRepository.java` | Status aggregation and latest additions |
| `repository/RelocationHistoryRepository.java` | Distinct relocated-printer count and latest transfers |
| `backend/src/test/java/com/example/printerinventory/DashboardIntegrationTests.java` | Empty inventory, statuses, deletion, distinct counts, ordering and limits |
| `frontend/src/pages/DashboardPage.tsx` | Dashboard cards, activity, empty/loading/error states |
| `frontend/src/App.tsx` | Dashboard navigation and home route |
| `frontend/src/pages/SystemPage.tsx` | Link to the completed dashboard |
| `frontend/src/api/inventory.ts`, `frontend/src/types/inventory.ts` | Typed dashboard API |
| `frontend/src/styles.css` | Dashboard styling and phone/tablet improvements |
| `e2e/dashboard.spec.js`, `e2e/inventory.spec.js` | Dashboard browser coverage and updated list entry route |
| `README.md`, `docs/PHASE5.md` | Current scope, run and test instructions |

## Run

From the project root with Docker Desktop running:

```powershell
docker compose up -d --build --wait
docker compose ps
```

Open http://localhost:8080. Existing data and schema are preserved. Local development still uses `./gradlew bootRun` in `backend/` and `npm run dev` in `frontend/` with the environment configuration in the README.

## Simple manual test

1. Open the dashboard and check its counts against the printer list.
2. Add an Active printer; return to Dashboard and confirm the counts and recent addition.
3. Click **Active printers** and confirm the list is filtered to Active.
4. Relocate that printer twice. Return to Dashboard: relocated printers should increase by one, while both transfers appear in recent activity.
5. Edit its status to Storage. Confirm Active decreases and Storage increases.
6. Delete the test printer. Confirm it disappears from dashboard counts and activity, while its history remains in the database.
7. Check dashboard, filters, forms, details and history at tablet and phone widths. No page should need horizontal scrolling.

Manual records use the real database. Use the isolated suites below for disposable test data.

## Automated tests

```powershell
docker compose -f docker-compose.test.yml up --build --abort-on-container-exit --exit-code-from tests
docker compose -f docker-compose.test.yml down
docker compose -f docker-compose.e2e.yml up --build --abort-on-container-exit --exit-code-from browser-tests
docker compose -f docker-compose.e2e.yml down
```

The suites use separate temporary databases without the application volume or exposed ports. Browser reports are saved to `.verification/browser/html/index.html`. Dashboard screenshots cover 1440, 1024, 768, 375 and 320px widths. The existing inventory and relocation tests remain in the full suite.

## Verification on 20 September 2026

- All 34 backend tests passed: 3 health, 21 inventory, 7 relocation, and 3 dashboard integration tests. Reports are in `.verification/phase5-tests/`.
- All 10 browser tests passed, including the previous CRUD and relocation workflows, dashboard empty/error recovery, filtered links, status changes, distinct relocation counts, and deletion.
- TypeScript checking, Vite production build, and the backend JAR build passed.
- Dashboard overflow checks passed at 1440, 1024, 768, 375 and 320px. Desktop, tablet and phone screenshots were visually inspected.
- Test data stayed in isolated disposable databases. No schema migration or production data changes were required.
- The running application was rebuilt and all three containers are healthy. `/dashboard` and `/api/dashboard` return HTTP 200, and `/api/health` reports API and database `UP`. Both isolated test projects were removed after verification.
