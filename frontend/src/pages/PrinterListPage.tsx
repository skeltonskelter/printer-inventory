import { useCallback, useRef, useState, type ChangeEvent, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { inventory } from "../api/inventory";
import { useResource } from "../hooks/useResource";
import { DeletePrinterDialog } from "../components/DeletePrinterDialog";
import { LoadError, Loading, PrinterBadge } from "../components/InventoryUi";
import { statusLabels, type Printer, type PrinterImportPreview } from "../types/inventory";
import { locationLabel, stickerLabel } from "../utils/inventory";

export function PrinterListPage() {
  const [params, setParams] = useSearchParams();
  const query = params.toString();
  const page = Math.max(0, Number(params.get("page")) || 0);
  const list = useResource(
    useCallback(
      (signal) => inventory.list(new URLSearchParams(query), signal),
      [query],
    ),
  );
  const locations = useResource(
    useCallback((signal) => inventory.locations(signal), []),
  );
  const [deleting, setDeleting] = useState<Printer | null>(null);
  const [notice, setNotice] = useState("");
  const [exporting, setExporting] = useState(false);
  const [importFile, setImportFile] = useState<File | null>(null);
  const [importPreview, setImportPreview] = useState<PrinterImportPreview | null>(null);
  const [importError, setImportError] = useState("");
  const [importing, setImporting] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);

  function exportCsv() {
    setExporting(true);
    const exportParams = new URLSearchParams(params);
    exportParams.delete("page");
    exportParams.delete("size");
    const link = document.createElement("a");
    link.href = `/api/printers/export?${exportParams.toString()}`;
    link.download = `printer-inventory-${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setExporting(false);
  }

  async function chooseImport(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    setImportPreview(null);
    setImportError("");
    setImportFile(null);
    if (!file) return;
    if (!file.name.toLowerCase().endsWith(".csv")) {
      setImportError("Choose a CSV file.");
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      setImportError("CSV files must be 5 MB or smaller.");
      return;
    }
    try {
      setImportPreview(await inventory.previewImport(file));
      setImportFile(file);
    } catch {
      setImportError("The CSV could not be read. Check the file and try again.");
    }
  }

  function cancelImport() {
    setImportFile(null);
    setImportPreview(null);
    setImportError("");
    if (fileInput.current) fileInput.current.value = "";
  }

  async function confirmImport() {
    if (!importFile || !importPreview || importPreview.invalidRows > 0) return;
    setImporting(true);
    setImportError("");
    try {
      const result = await inventory.confirmImport(importFile);
      if (result.invalidRows > 0) {
        setImportPreview({ totalRows: importPreview.totalRows, validRows: 0, invalidRows: result.invalidRows, errors: result.errors });
        return;
      }
      setNotice(`Imported ${result.imported} printer${result.imported === 1 ? "" : "s"}.`);
      cancelImport();
      list.reload();
      locations.reload();
    } catch {
      setImportError("The import could not be completed. No printers were imported.");
    } finally {
      setImporting(false);
    }
  }

  function filter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next = new URLSearchParams();
    new FormData(event.currentTarget).forEach((value, key) => {
      if (String(value).trim()) next.set(key, String(value).trim());
    });
    setNotice("");
    setParams(next);
  }
  function goToPage(value: number) {
    const next = new URLSearchParams(params);
    next.set("page", String(value));
    setParams(next);
  }
  function deleted() {
    setNotice(`Printer ${stickerLabel(deleting?.stickerNumber)} was deleted.`);
    setDeleting(null);
    if (list.data?.content.length === 1 && page > 0) goToPage(page - 1);
    else list.reload();
  }
  function actions(printer: Printer) {
    return (
      <div className="printer-actions">
        <Link
          to={`/printers/${printer.id}`}
          className="btn btn-sm btn-outline-secondary"
          aria-label={`View ${stickerLabel(printer.stickerNumber)}`}
        >
          View
        </Link>
        <Link
          to={`/printers/${printer.id}/edit`}
          className="btn btn-sm btn-outline-secondary"
          aria-label={`Edit ${stickerLabel(printer.stickerNumber)}`}
        >
          Edit
        </Link>
        <Link
          to={`/printers/${printer.id}/relocate`}
          className="btn btn-sm btn-outline-primary"
          aria-label={`Relocate ${stickerLabel(printer.stickerNumber)}`}
        >
          Relocate
        </Link>
        <button
          className="btn btn-sm btn-outline-danger"
          onClick={() => setDeleting(printer)}
          aria-label={`Delete ${stickerLabel(printer.stickerNumber)}`}
        >
          Delete
        </button>
      </div>
    );
  }
  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow">YOUR INVENTORY</div>
          <h1>Printers</h1>
          <p>Keep every printer accounted for, wherever it belongs.</p>
        </div>
        <div className="printer-csv-actions">
          <a className="btn btn-outline-secondary" href="/api/printers/import/template">CSV template</a>
          <button type="button" className="btn btn-outline-secondary" onClick={() => fileInput.current?.click()}>
            Import CSV
          </button>
          <input ref={fileInput} className="d-none" type="file" accept=".csv,text/csv" onChange={chooseImport} />
        <button type="button" className="btn btn-outline-primary" onClick={exportCsv} disabled={exporting}>
          {exporting ? "Exporting…" : "Export CSV"}
          </button>
        </div>
      </div>
      {notice && (
        <div role="status" className="alert alert-success">
          {notice}
        </div>
      )}
      {importError && <div className="alert alert-danger" role="alert">{importError}</div>}
      {importPreview && (
        <section className="panel mb-4" aria-label="CSV import preview">
          <h2>Import preview</h2>
          <p>Total rows: {importPreview.totalRows} · Valid rows: {importPreview.validRows} · Invalid rows: {importPreview.invalidRows}</p>
          {importPreview.errors.length > 0 && (
            <ul className="mb-3">
              {importPreview.errors.map((error, index) => (
                <li key={`${error.row}-${index}`}>{error.row ? `Row ${error.row}: ` : ""}{error.message}</li>
              ))}
            </ul>
          )}
          {importPreview.invalidRows > 0 && <p className="text-danger">Correct every invalid row before confirming the import.</p>}
          <div className="d-flex gap-2 flex-wrap">
            <button className="btn btn-primary" type="button" disabled={importPreview.invalidRows > 0 || importing} onClick={confirmImport}>
              {importing ? "Importingâ€¦" : "Confirm import"}
            </button>
            <button className="btn btn-outline-secondary" type="button" disabled={importing} onClick={cancelImport}>Cancel</button>
            <a className="btn btn-outline-secondary" href="/api/printers/import/template">Download CSV template</a>
          </div>
        </section>
      )}
      <form
        key={query}
        className="panel filter-panel"
        onSubmit={filter}
        aria-label="Filter printers"
      >
        <div className="row g-3">
          <div className="col-12 col-xl-4">
            <label htmlFor="search" className="form-label">
              Search printers
            </label>
            <input
              id="search"
              name="search"
              className="form-control"
              maxLength={120}
              defaultValue={params.get("search") ?? ""}
              placeholder="Sticker, serial, brand or model"
            />
          </div>
          <div className="col-12 col-md-4 col-xl-2">
            <label htmlFor="brand" className="form-label">
              Brand
            </label>
            <input
              id="brand"
              name="brand"
              className="form-control"
              maxLength={100}
              defaultValue={params.get("brand") ?? ""}
              placeholder="Exact brand"
            />
          </div>
          <div className="col-12 col-md-4 col-xl-3">
            <label htmlFor="location-filter" className="form-label">
              Location
            </label>
            <select
              key={`${params.get("locationId")}-${locations.data?.length}`}
              id="location-filter"
              name="locationId"
              className="form-select"
              defaultValue={params.get("locationId") ?? ""}
              disabled={locations.loading}
            >
              <option value="">All locations</option>
              {locations.data?.map((location) => (
                <option key={location.id} value={location.id}>
                  {locationLabel(location)}
                </option>
              ))}
            </select>
          </div>
          <div className="col-12 col-md-4 col-xl-3">
            <label htmlFor="status-filter" className="form-label">
              Status
            </label>
            <select
              id="status-filter"
              name="status"
              className="form-select"
              defaultValue={params.get("status") ?? ""}
            >
              <option value="">All statuses</option>
              {Object.entries(statusLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>
        </div>
        <div className="printer-filter-actions mt-3">
          <div className="d-flex gap-2 flex-wrap">
            <button className="btn btn-primary" type="submit">
              Apply filters
            </button>
            <button
              className="btn btn-outline-secondary"
              type="button"
              onClick={() => {
                setParams({});
                setNotice("");
              }}
            >
              Clear filters
            </button>
          </div>
          <Link to="/printers/new" className="btn btn-primary">
            + Add printer
          </Link>
        </div>
        {locations.error && (
          <div className="mt-3">
            <LoadError
              message={`Locations could not be loaded. ${locations.error}`}
              retry={locations.reload}
            />
          </div>
        )}
      </form>
      {list.loading ? (
        <Loading />
      ) : list.error ? (
        <LoadError message={list.error} retry={list.reload} />
      ) : (
        list.data && (
          <>
            <div className="section-heading">
              <h2>
                {list.data.totalElements}{" "}
                {list.data.totalElements === 1 ? "printer" : "printers"}
              </h2>
              <span>Newest first</span>
            </div>
            {list.data.content.length === 0 ? (
              <div className="panel empty-state">
                <h2>No printers found</h2>
                <p>Add your first printer or try different filters.</p>
                <Link className="btn btn-primary" to="/printers/new">
                  Add printer
                </Link>
                {page > 0 && (
                  <button
                    className="btn btn-outline-secondary ms-2"
                    onClick={() => goToPage(0)}
                  >
                    Go to first page
                  </button>
                )}
              </div>
            ) : (
              <>
                <div className="panel printer-table">
                  <table className="table align-middle mb-0">
                    <thead>
                      <tr>
                        {[
                          "Sticker number",
                          "Brand / model",
                          "Serial number",
                          "Location",
                          "Status",
                          "Actions",
                        ].map((title) => (
                          <th key={title} scope="col">
                            {title}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {list.data.content.map((printer) => (
                        <tr key={printer.id}>
                          <td>
                            <Link
                              className="sticker-link"
                              to={`/printers/${printer.id}`}
                            >
                              {stickerLabel(printer.stickerNumber)}
                            </Link>
                          </td>
                          <td>
                            <strong>{printer.brand}</strong>
                            <div className="text-secondary">
                              {printer.model}
                            </div>
                          </td>
                          <td>{printer.serialNumber || "Not recorded"}</td>
                          <td>{locationLabel(printer.location)}</td>
                          <td>
                            <PrinterBadge status={printer.status} />
                          </td>
                          <td>{actions(printer)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <div className="printer-cards">
                  {list.data.content.map((printer) => (
                    <article className="panel printer-card" key={printer.id}>
                      <div className="d-flex justify-content-between gap-2 flex-wrap">
                        <Link
                          className="sticker-link"
                          to={`/printers/${printer.id}`}
                        >
                          {stickerLabel(printer.stickerNumber)}
                        </Link>
                        <PrinterBadge status={printer.status} />
                      </div>
                      <h2>
                        {printer.brand} {printer.model}
                      </h2>
                      <dl>
                        <dt>Serial number</dt>
                        <dd>{printer.serialNumber || "Not recorded"}</dd>
                        <dt>Location</dt>
                        <dd>{locationLabel(printer.location)}</dd>
                      </dl>
                      {actions(printer)}
                    </article>
                  ))}
                </div>
              </>
            )}
            {list.data.totalPages > 1 && (
              <nav className="pagination-bar" aria-label="Printer pages">
                <button
                  className="btn btn-outline-secondary"
                  disabled={page === 0}
                  onClick={() => goToPage(page - 1)}
                >
                  Previous
                </button>
                <span>
                  Page {page + 1} of {list.data.totalPages}
                </span>
                <button
                  className="btn btn-outline-secondary"
                  disabled={page + 1 >= list.data.totalPages}
                  onClick={() => goToPage(page + 1)}
                >
                  Next
                </button>
              </nav>
            )}
          </>
        )
      )}
      {deleting && (
        <DeletePrinterDialog
          printer={deleting}
          onCancel={() => setDeleting(null)}
          onDeleted={deleted}
        />
      )}
    </>
  );
}
