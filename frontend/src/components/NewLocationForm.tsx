import { useState, type FormEvent } from "react";
import { apiProblem, inventory } from "../api/inventory";
import type { ApiProblem, Location } from "../types/inventory";

export function NewLocationForm({
  onCreated,
  onCancel,
}: {
  onCreated: (location: Location) => void;
  onCancel: () => void;
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
      onCreated(await inventory.createLocation(values));
    } catch (error) {
      setProblem(apiProblem(error));
      setBusy(false);
    }
  }
  return (
    <section
      className="panel inventory-form mb-4"
      aria-labelledby="new-location-title"
    >
      <h2 id="new-location-title">New location</h2>
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
                <label className="form-label" htmlFor={`new-${name}`}>
                  {label}
                  {name === "department" ? " *" : ""}
                </label>
                <input
                  autoFocus={name === "department"}
                  id={`new-${name}`}
                  name={name}
                  maxLength={max}
                  required={name === "department"}
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
              {busy ? "Saving…" : "Save location"}
            </button>
            <button
              type="button"
              className="btn btn-outline-secondary"
              onClick={onCancel}
            >
              Cancel location
            </button>
          </div>
        </fieldset>
      </form>
    </section>
  );
}
