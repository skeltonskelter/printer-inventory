# Phase 3: printer management screens

The browser now supports listing, adding, viewing, editing, searching, filtering, and deleting printers. It uses the existing Phase 2 APIs. There are no new database migrations or backend changes in this phase.

## Run and open

From the project root, keeping your existing `.env` and database volume:

```powershell
docker compose up -d --build --wait
```

Open **http://localhost:8080/printers**. The home URL redirects here. Connection status remains available at **http://localhost:8080/system**. Local Vite development uses the same routes on **http://localhost:5173**; backend setup is unchanged.

## What you can do

- **List printers:** desktop uses a table; smaller screens use cards with the same actions. Pagination follows the API's default of 20 records per page.
- **Search:** enter any part of a sticker number, serial number, brand, or model, then select **Apply filters**.
- **Filter:** combine an exact brand name (case-insensitive), location, and status. **Clear filters** resets them. Search/filter values live in the URL, so reload and browser Back preserve them.
- **Add:** select **Add printer**, fill the required fields, select a location and status, then save. The details page opens after success.
- **Create a location:** use **+ New location** in the add-printer form. Save the department and any optional details; the new location becomes selected without losing your printer input.
- **View:** see every printer field, dates, remarks, and the complete current location.
- **Edit:** update printer information and save. Location remains fixed to prevent an unrecorded transfer. If someone edited the record after you loaded it, the API rejects your stale version and your typed values stay on the form.
- **Delete:** a modal identifies the printer and explains record retention. Cancel or Escape closes it without deleting. Confirming removes it from inventory views; Phase 2's retained database record and reserved identifiers remain.

Status values display as friendly labels, including **Under Repair** and **For Repair**. Unknown serial numbers display as **Not recorded**. Required inputs and server validation are shown in the form. Request failures provide a message and retry action; forms keep entered values after failed saves.

Relocation actions and history were subsequently added in [Phase 4](PHASE4.md). The dashboard and further responsive improvements are documented in [Phase 5](PHASE5.md).

## Files created or modified

| Files | Purpose |
| --- | --- |
| `frontend/src/pages/PrinterListPage.tsx` | Search, filters, pagination, table/cards, deletion |
| `frontend/src/pages/PrinterFormPage.tsx` | Add/edit forms, validation, version-aware saves |
| `frontend/src/pages/PrinterDetailsPage.tsx` | Printer information, current location, edit/delete actions |
| `frontend/src/components/DeletePrinterDialog.tsx` | Confirmation, keyboard focus, cancellation, failed-delete feedback |
| `frontend/src/components/NewLocationForm.tsx` | Create a location during printer entry |
| `frontend/src/components/InventoryUi.tsx` | Shared status badges, loading, and retry feedback |
| `frontend/src/api/inventory.ts` | Typed API calls and API error messages |
| `frontend/src/types/inventory.ts` | Printer/location DTOs and status labels |
| `frontend/src/hooks/useResource.ts` | Cancellable loads that ignore outdated responses |
| `frontend/src/utils/inventory.ts` | Consistent location/date display |
| `frontend/src/App.tsx`, `pages/SystemPage.tsx`, `styles.css`, `frontend/index.html` | Routes, navigation, responsive styles, updated system page |
| `e2e/`, `docker-compose.e2e.yml` | Automated real-browser tests with isolated PostgreSQL |
| `README.md`, `docs/PHASE3.md` | Run instructions and verification guide |

No production frontend dependencies were added. Playwright is a separate development-only browser test dependency under `e2e/` and is not included in the frontend image.

## Simple manual check

1. Open `/printers` and select **Add printer**.
2. Enter a unique test sticker, brand, and model. Create a location if none exists; then save.
3. Check the details page, including current location and dates.
4. Edit the model or remarks and change the status to **Under Repair**. Save and check the result.
5. Return to the list, search the sticker, and apply a matching status/location filter.
6. Select **Delete**, then **Cancel**. The printer must remain.
7. Open the confirmation again and choose **Delete printer**. The printer should disappear from search results.
8. Repeat viewing and editing at phone width. Cards and forms should fit without horizontal scrolling.

Manual demo records use the real database and are retained after deletion. The automated suite below uses a separate disposable database instead.

## Automated browser tests

From the project root:

```powershell
docker compose -f docker-compose.e2e.yml up --build --abort-on-container-exit --exit-code-from browser-tests
docker compose -f docker-compose.e2e.yml down
```

Run the shutdown command after each test run to remove the test containers; the next run starts with a fresh empty database. The test project has no application data volume and publishes no ports. Your normal application stays running. The first run downloads the Chromium test image and can take several minutes.

Tests cover the complete create-location/add/view/edit/delete workflow, required fields, duplicate and stale-edit conflicts, combined filters, pagination, cancellation, network failure/retry, missing routes/records, and layouts at 1440, 768, 375, and 320 pixels. Browser/API interactions use the real isolated Spring Boot backend and PostgreSQL database.

Current reports and screenshots are saved under `.verification/browser/` (ignored by Git). Open `.verification/browser/html/index.html` for results. Traces are saved if a test fails. The original Phase 3 artifacts remain in `.verification/phase3/`.

To check only the production frontend build:

```powershell
docker compose build frontend
```

Or, with local Node.js installed, run `npm ci` followed by `npm run build` inside `frontend/`.

## Verified on 19 September 2026

- Strict TypeScript checking and the Vite production build passed.
- All **5 end-to-end browser tests passed**, using the real backend and PostgreSQL in an isolated Compose project.
- The backend image built successfully and its 3 health tests passed during the browser-test setup.
- Desktop table, tablet/phone cards, phone forms/details, and the delete dialog were inspected in saved screenshots. Layout checks at 1440, 768, 375, and 320 pixels found no horizontal overflow.
- The tests exercised creating a location, adding/viewing/editing/deleting printers, native required-field validation, duplicate identifiers, stale edits, filters, browser Back/reload, pagination, deletion cancellation, and network error recovery.
- The running application's database was not used for test records. No backend source or database schema changed in Phase 3.

The report is `.verification/phase3/html/index.html`. Screenshots are saved alongside it in `.verification/phase3/`.
