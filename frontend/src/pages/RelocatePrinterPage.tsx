import { useCallback, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { inventory, apiProblem } from "../api/inventory";
import { useResource } from "../hooks/useResource";
import { Loading, LoadError } from "../components/InventoryUi";
import { NewLocationForm } from "../components/NewLocationForm";
import type { ApiProblem, Location, Printer } from "../types/inventory";
import { locationLabel, stickerLabel } from "../utils/inventory";

export function RelocatePrinterPage() {
  const { id = "" } = useParams();
  const resource = useResource(
    useCallback(
      async (signal) => {
        const [printer, locations] = await Promise.all([
          inventory.get(id, signal),
          inventory.locations(signal),
        ]);
        return { printer, locations };
      },
      [id],
    ),
  );
  return (
    <>
      <Link className="back-link" to={`/printers/${id}`}>
        ← Printer details
      </Link>
      <div className="page-heading">
        <div>
          <div className="eyebrow">LOCATION TRANSFER</div>
          <h1>Relocate printer</h1>
          <p>Move a printer and keep a record of where it has been.</p>
        </div>
      </div>
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <LoadError message={resource.error} retry={resource.reload} />
      ) : (
        resource.data && (
          <RelocateForm
            key={`${id}-${resource.data.printer.version}`}
            printer={resource.data.printer}
            initialLocations={resource.data.locations}
          />
        )
      )}
    </>
  );
}

function RelocateForm({
  printer,
  initialLocations,
}: {
  printer: Printer;
  initialLocations: Location[];
}) {
  const navigate = useNavigate();
  const [locations, setLocations] = useState(initialLocations);
  const [destinationId, setDestinationId] = useState("");
  const [showLocation, setShowLocation] = useState(false);
  const [editingLocation, setEditingLocation] = useState<Location | null>(null);
  const [problem, setProblem] = useState<ApiProblem | null>(null);
  const [busy, setBusy] = useState(false);
  const today = new Date();
  const todayValue = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;
  const destinations = locations.filter(
    (location) => location.id !== printer.location.id,
  );

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const fields = new FormData(event.currentTarget);
    setBusy(true);
    setProblem(null);
    try {
      const updated = await inventory.relocate(printer.id, {
        newLocationId: Number(destinationId),
        relocationDate: String(fields.get("relocationDate")),
        remarks: String(fields.get("remarks") || "").trim() || null,
        version: printer.version,
      });
      navigate(`/printers/${updated.id}`, {
        replace: true,
        state: {
          notice:
            "Printer relocated. The transfer has been added to its history.",
        },
      });
    } catch (error) {
      setProblem(apiProblem(error));
      setBusy(false);
    }
  }
  return (
    <>
      <section className="panel details-panel mb-4">
        <h2>{stickerLabel(printer.stickerNumber)}</h2>
        <p>
          {printer.brand} {printer.model}
        </p>
        <dl className="location-details">
          <div>
            <dt>Current location</dt>
            <dd>{locationLabel(printer.location)}</dd>
          </div>
        </dl>
      </section>
      {showLocation && (
        <NewLocationForm
          location={editingLocation ?? undefined}
          onCancel={() => {
            setShowLocation(false);
            setEditingLocation(null);
          }}
          onCreated={(location) => {
            setLocations((values) =>
              editingLocation
                ? values.map((value) =>
                    value.id === location.id ? location : value,
                  )
                : [...values, location],
            );
            if (!editingLocation) setDestinationId(String(location.id));
            setShowLocation(false);
            setEditingLocation(null);
          }}
        />
      )}
      <form
        className="panel inventory-form"
        onSubmit={submit}
        aria-label="Relocation details"
      >
        {problem && (
          <div role="alert" className="alert alert-danger">
            {problem.message}
            <p className="mt-2 mb-0">
              Your input is still here. If the printer changed or a response was
              lost, check its details before trying again.
            </p>
          </div>
        )}
        <fieldset disabled={busy}>
          <div className="row g-4">
            <div className="col-12 col-md-8">
              <label className="form-label" htmlFor="new-location">
                New location *
              </label>
              <div className="d-flex gap-2">
                <select
                  id="new-location"
                  className={`form-select ${problem?.fieldErrors.newLocationId ? "is-invalid" : ""}`}
                  value={destinationId}
                  onChange={(event) => setDestinationId(event.target.value)}
                  required
                  aria-describedby="destination-help destination-error"
                >
                  <option value="">Choose a different location</option>
                  {destinations.map((location) => (
                    <option key={location.id} value={location.id}>
                      {locationLabel(location)}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  className="btn btn-outline-secondary text-nowrap"
                  aria-label="Edit selected location"
                  disabled={!destinationId || showLocation}
                  onClick={() => {
                    const selected = locations.find(
                      (location) => String(location.id) === destinationId,
                    );
                    if (selected) {
                      setEditingLocation(selected);
                      setShowLocation(true);
                    }
                  }}
                >
                  Edit location
                </button>
              </div>
              <div className="invalid-feedback" id="destination-error">
                {problem?.fieldErrors.newLocationId}
              </div>
              <p className="form-text" id="destination-help">
                {destinations.length
                  ? "The current location is excluded."
                  : "Create a new location before recording a transfer."}
              </p>
              <button
                className="btn btn-link px-0"
                type="button"
                onClick={() => setShowLocation(true)}
                disabled={showLocation}
              >
                + New location
              </button>
            </div>
            <div className="col-12 col-md-4">
              <label htmlFor="relocation-date" className="form-label">
                Relocation date *
              </label>
              <input
                id="relocation-date"
                name="relocationDate"
                className={`form-control ${problem?.fieldErrors.relocationDate ? "is-invalid" : ""}`}
                type="date"
                required
                defaultValue={todayValue}
                aria-describedby="date-help date-error"
              />
              <div className="invalid-feedback" id="date-error">
                {problem?.fieldErrors.relocationDate}
              </div>
              <p id="date-help" className="form-text">
                Use the actual transfer date, no earlier than the last recorded
                transfer. Future dates are not allowed.
              </p>
            </div>
            <div className="col-12">
              <label htmlFor="relocation-remarks" className="form-label">
                Remarks
              </label>
              <textarea
                id="relocation-remarks"
                name="remarks"
                className={`form-control ${problem?.fieldErrors.remarks ? "is-invalid" : ""}`}
                rows={4}
                maxLength={2000}
                placeholder="Reason for transfer or other useful details"
              />
              <div className="invalid-feedback">
                {problem?.fieldErrors.remarks}
              </div>
            </div>
          </div>
          <div className="form-actions">
            <button
              className="btn btn-primary"
              type="submit"
              disabled={showLocation || !destinations.length}
            >
              {busy ? "Relocating…" : "Relocate printer"}
            </button>
            <button
              className="btn btn-outline-secondary"
              type="button"
              onClick={() => navigate(`/printers/${printer.id}`)}
            >
              Cancel
            </button>
          </div>
        </fieldset>
      </form>
    </>
  );
}
