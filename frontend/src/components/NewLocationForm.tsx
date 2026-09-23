import { useState, type FormEvent } from "react";
import { apiProblem, inventory } from "../api/inventory";
import type { ApiProblem, Location } from "../types/inventory";

export function NewLocationForm({
  onCreated,
  onCancel,
  location,
}: {
  onCreated: (location: Location) => void;
  onCancel: () => void;
  location?: Location;
}) {
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<ApiProblem | null>(null);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = Object.fromEntries(
      new FormData(event.currentTarget),
    ) as Record<string, string>;
    if (!values.department.trim()) {
      setProblem({
        message: "Department is required.",
        fieldErrors: { department: "Enter a department." },
      });
      return;
    }
    setBusy(true);
    setProblem(null);
    try {
      const saved = location
        ? await inventory.updateLocation(location.id, {
            ...values,
            version: location.version,
          })
        : await inventory.createLocation(values);
      onCreated(saved);
    } catch (error) {
      setProblem(apiProblem(error));
      setBusy(false);
    }
  }
  return (
    <section
      className="panel inventory-form mb-4"
      aria-labelledby="location-form-title"
    >
      <h2 id="location-form-title">
        {location ? "Edit location" : "New location"}
      </h2>
      <p>
        Give the location a department and add any details that help you find
        it.
      </p>
      {problem && (
        <div role="alert" className="alert alert-danger">
          {problem.message}
        </div>
      )}
      <form onSubmit={submit}>
        <fieldset disabled={busy}>
          <div className="row g-3">
            {(
              [
                ["department", "Department", 120],
                ["section", "Section", 120],
                ["building", "Building", 120],
                ["floor", "Floor", 50],
                ["room", "Room", 80],
                ["description", "Additional description", 1000],
              ] as const
            ).map(([name, label, max]) => (
              <div
                className={
                  name === "description" ? "col-12" : "col-12 col-md-6"
                }
                key={name}
              >
                <label
                  className="form-label"
                  htmlFor={`${location ? `edit-${location.id}` : "new"}-${name}`}
                >
                  {label}
                  {name === "department" ? " *" : ""}
                </label>
                <input
                  autoFocus={name === "department"}
                  id={`${location ? `edit-${location.id}` : "new"}-${name}`}
                  name={name}
                  maxLength={max}
                  required={name === "department"}
                  defaultValue={location?.[name] ?? ""}
                  className={`form-control ${problem?.fieldErrors[name] ? "is-invalid" : ""}`}
                  aria-describedby={
                    problem?.fieldErrors[name] ? `new-${name}-error` : undefined
                  }
                />
                <div id={`new-${name}-error`} className="invalid-feedback">
                  {problem?.fieldErrors[name]}
                </div>
              </div>
            ))}
          </div>
          <div className="d-flex gap-2 mt-4">
            <button type="submit" className="btn btn-primary">
              {busy ? "Saving…" : location ? "Save changes" : "Save location"}
            </button>
            <button
              type="button"
              className="btn btn-outline-secondary"
              onClick={onCancel}
            >
              {location ? "Cancel" : "Cancel location"}
            </button>
          </div>
        </fieldset>
      </form>
    </section>
  );
}
