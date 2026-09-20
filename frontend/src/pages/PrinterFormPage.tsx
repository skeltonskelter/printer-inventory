import { useCallback, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { apiProblem, inventory } from "../api/inventory";
import { useResource } from "../hooks/useResource";
import { LoadError, Loading } from "../components/InventoryUi";
import { NewLocationForm } from "../components/NewLocationForm";
import {
  statusLabels,
  type ApiProblem,
  type Location,
  type Printer,
  type PrinterStatus,
} from "../types/inventory";
import { locationLabel } from "../utils/inventory";

export function PrinterFormPage() {
  const { id } = useParams();
  const resource = useResource(
    useCallback(
      async (signal) => {
        const [locations, printer] = await Promise.all([
          inventory.locations(signal),
          id ? inventory.get(id, signal) : Promise.resolve(null),
        ]);
        return { locations, printer };
      },
      [id],
    ),
  );
  return (
    <>
      <Link className="back-link" to={id ? `/printers/${id}` : "/printers"}>
        ← {id ? "Printer details" : "All printers"}
      </Link>
      <div className="page-heading">
        <div>
          <div className="eyebrow">PRINTER INFORMATION</div>
          <h1>{id ? "Edit printer" : "Add printer"}</h1>
          <p>
            {id
              ? "Keep the printer information up to date."
              : "Register a printer and assign its initial location."}
          </p>
        </div>
      </div>
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <LoadError message={resource.error} retry={resource.reload} />
      ) : (
        resource.data && (
          <PrinterForm
            key={`${id ?? "new"}-${resource.data.printer?.version ?? 0}`}
            printer={resource.data.printer}
            initialLocations={resource.data.locations}
          />
        )
      )}
    </>
  );
}

function PrinterForm({
  printer,
  initialLocations,
}: {
  printer: Printer | null;
  initialLocations: Location[];
}) {
  const navigate = useNavigate();
  const [locations, setLocations] = useState(initialLocations);
  const [locationId, setLocationId] = useState(
    printer ? String(printer.location.id) : "",
  );
  const [showLocation, setShowLocation] = useState(false);
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<ApiProblem | null>(null);
  const [notice, setNotice] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const fields = new FormData(event.currentTarget);
    const text = (name: string) => String(fields.get(name) ?? "").trim();
    const errors: Record<string, string> = {};
    for (const [name, label] of [
      ["brand", "Brand"],
      ["model", "Model"],
      ["stickerNumber", "Sticker number"],
    ]) {
      if (!text(name)) errors[name] = `${label} is required.`;
    }
    if (!locationId) errors.locationId = "Choose or create a location.";
    if (Object.keys(errors).length) {
      setProblem({
        message: "Please correct the highlighted fields.",
        fieldErrors: errors,
      });
      return;
    }
    const input = {
      brand: text("brand"),
      model: text("model"),
      stickerNumber: text("stickerNumber"),
      serialNumber: text("serialNumber") || null,
      locationId: Number(locationId),
      status: text("status") as PrinterStatus,
      remarks: text("remarks") || null,
      ...(printer ? { version: printer.version } : {}),
    };
    setBusy(true);
    setProblem(null);
    try {
      const saved = printer
        ? await inventory.update(printer.id, input)
        : await inventory.create(input);
      navigate(`/printers/${saved.id}`, {
        replace: true,
        state: { notice: printer ? "Printer updated." : "Printer added." },
      });
    } catch (error) {
      setProblem(apiProblem(error));
      setBusy(false);
    }
  }
  const invalid = (name: string) =>
    problem?.fieldErrors[name] ? " is-invalid" : "";
  return (
    <>
      {showLocation && (
        <NewLocationForm
          onCancel={() => setShowLocation(false)}
          onCreated={(location) => {
            setLocations((values) => [...values, location]);
            setLocationId(String(location.id));
            setShowLocation(false);
            setNotice(`Location ${location.department} added and selected.`);
          }}
        />
      )}
      {notice && (
        <div role="status" className="alert alert-success">
          {notice}
        </div>
      )}
      <form
        className="panel inventory-form"
        onSubmit={submit}
        aria-label="Printer information"
      >
        <p className="text-secondary">Fields marked * are required.</p>
        {problem && (
          <div role="alert" className="alert alert-danger">
            {problem.message}
            {problem.message.includes("Reload") && (
              <p className="mt-2 mb-0">
                Copy any changes you want to keep, then reload this page to get
                the latest record.
              </p>
            )}
          </div>
        )}
        <fieldset disabled={busy}>
          <div className="row g-4">
            {(
              [
                ["brand", "Brand", 100, true],
                ["model", "Model", 120, true],
                ["serialNumber", "Serial number", 120, false],
                ["stickerNumber", "Sticker number", 80, true],
              ] as const
            ).map(([name, label, max, required]) => (
              <div className="col-12 col-md-6" key={name}>
                <label htmlFor={name} className="form-label">
                  {label}
                  {required ? " *" : ""}
                </label>
                <input
                  id={name}
                  name={name}
                  required={required}
                  maxLength={max}
                  defaultValue={printer?.[name] ?? ""}
                  className={`form-control${invalid(name)}`}
                  aria-invalid={!!problem?.fieldErrors[name]}
                  aria-describedby={`${name}-help ${name}-error`}
                />
                <div className="form-text" id={`${name}-help`}>
                  {name === "serialNumber"
                    ? "Leave blank if unknown."
                    : name === "stickerNumber"
                      ? "Use the unique asset sticker on the printer."
                      : ""}
                </div>
                <div id={`${name}-error`} className="invalid-feedback">
                  {problem?.fieldErrors[name]}
                </div>
              </div>
            ))}
            <div className="col-12 col-md-8">
              <label htmlFor="locationId" className="form-label">
                Location *
              </label>
              <select
                id="locationId"
                className={`form-select${invalid("locationId")}`}
                value={locationId}
                onChange={(event) => setLocationId(event.target.value)}
                required
                disabled={!!printer}
                aria-describedby="location-help location-error"
              >
                <option value="">Choose a location</option>
                {locations.map((location) => (
                  <option key={location.id} value={location.id}>
                    {locationLabel(location)}
                  </option>
                ))}
              </select>
              <div id="location-error" className="invalid-feedback">
                {problem?.fieldErrors.locationId}
              </div>
              <div id="location-help" className="form-text">
                {printer
                  ? "To change location, use Relocate from the printer details page. Each transfer is recorded in its history."
                  : locations.length === 0
                    ? "No locations yet. Create one to add your first printer."
                    : "Select where this printer is currently kept."}
              </div>
              {!printer && (
                <button
                  className="btn btn-link px-0"
                  type="button"
                  disabled={showLocation}
                  onClick={() => setShowLocation(true)}
                >
                  + New location
                </button>
              )}
            </div>
            <div className="col-12 col-md-4">
              <label htmlFor="status" className="form-label">
                Status *
              </label>
              <select
                id="status"
                name="status"
                className={`form-select${invalid("status")}`}
                defaultValue={printer?.status ?? "ACTIVE"}
                required
              >
                {Object.entries(statusLabels).map(([value, label]) => (
                  <option value={value} key={value}>
                    {label}
                  </option>
                ))}
              </select>
              <div className="invalid-feedback">
                {problem?.fieldErrors.status}
              </div>
            </div>
            <div className="col-12">
              <label htmlFor="remarks" className="form-label">
                Remarks
              </label>
              <textarea
                id="remarks"
                name="remarks"
                className={`form-control${invalid("remarks")}`}
                rows={4}
                maxLength={2000}
                defaultValue={printer?.remarks ?? ""}
              />
              <div className="invalid-feedback">
                {problem?.fieldErrors.remarks}
              </div>
            </div>
          </div>
          <div className="form-actions">
            <button
              type="submit"
              className="btn btn-primary"
              disabled={showLocation}
            >
              {busy ? "Saving…" : printer ? "Save changes" : "Add printer"}
            </button>
            <button
              type="button"
              className="btn btn-outline-secondary"
              onClick={() =>
                navigate(printer ? `/printers/${printer.id}` : "/printers")
              }
            >
              Cancel
            </button>
          </div>
        </fieldset>
      </form>
    </>
  );
}
