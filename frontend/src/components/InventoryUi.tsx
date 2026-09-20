import { statusLabels, type PrinterStatus } from "../types/inventory";

export function PrinterBadge({ status }: { status: PrinterStatus }) {
  return (
    <span className={`printer-badge printer-${status.toLowerCase()}`}>
      {statusLabels[status]}
    </span>
  );
}
export function Loading() {
  return (
    <div role="status" className="panel p-4 text-secondary">
      <span
        className="spinner-border spinner-border-sm me-2"
        aria-hidden="true"
      />
      Loading…
    </div>
  );
}
export function LoadError({
  message,
  retry,
}: {
  message: string;
  retry: () => void;
}) {
  return (
    <div className="alert alert-danger" role="alert">
      <p>{message}</p>
      <button className="btn btn-outline-danger btn-sm" onClick={retry}>
        Try again
      </button>
    </div>
  );
}
