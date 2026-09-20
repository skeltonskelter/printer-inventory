import { useEffect, useRef, useState } from "react";
import { apiProblem, inventory } from "../api/inventory";
import type { Printer } from "../types/inventory";

export function DeletePrinterDialog({
  printer,
  onCancel,
  onDeleted,
}: {
  printer: Printer;
  onCancel: () => void;
  onDeleted: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    const element = dialog.current!;
    element.showModal();
    return () => element.close();
  }, []);
  async function remove() {
    setBusy(true);
    setError("");
    try {
      await inventory.remove(printer.id);
      onDeleted();
    } catch (error) {
      setError(apiProblem(error).message);
      setBusy(false);
    }
  }
  return (
    <dialog
      ref={dialog}
      className="delete-dialog"
      aria-labelledby="delete-title"
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onCancel();
      }}
    >
      <h2 id="delete-title">Delete printer?</h2>
      <p>
        Are you sure you want to delete printer{" "}
        <strong>{printer.stickerNumber}</strong>?
      </p>
      <p className="text-secondary">
        It will disappear from the inventory. Its record is retained, and its
        sticker and serial numbers remain reserved.
      </p>
      {error && (
        <div role="alert" className="alert alert-danger">
          {error}
        </div>
      )}
      <div className="d-flex justify-content-end gap-2">
        <button
          autoFocus
          className="btn btn-outline-secondary"
          disabled={busy}
          onClick={onCancel}
        >
          Cancel
        </button>
        <button
          className="btn btn-danger"
          disabled={busy}
          onClick={() => void remove()}
        >
          {busy ? "Deleting…" : "Delete printer"}
        </button>
      </div>
    </dialog>
  );
}
