import { useCallback } from "react";
import { inventory } from "../api/inventory";
import { useResource } from "../hooks/useResource";
import { locationLabel, displayDate } from "../utils/inventory";
import { Loading, LoadError } from "./InventoryUi";

export function RelocationHistoryList({ printerId }: { printerId: number }) {
  const history = useResource(
    useCallback(
      (signal) => inventory.relocations(printerId, signal),
      [printerId],
    ),
  );
  return (
    <section
      className="panel details-panel mt-4"
      aria-labelledby="history-heading"
    >
      <h2 id="history-heading">Relocation history</h2>
      {history.loading ? (
        <Loading />
      ) : history.error ? (
        <LoadError message={history.error} retry={history.reload} />
      ) : history.data?.length === 0 ? (
        <p className="text-secondary mb-0">
          No relocations recorded. The printer is at its initial location.
        </p>
      ) : (
        <ol className="relocation-history">
          {history.data?.map((record) => (
            <li key={record.id}>
              <time
                className="relocation-date"
                dateTime={record.relocationDate}
              >
                {new Date(
                  `${record.relocationDate}T00:00:00`,
                ).toLocaleDateString(undefined, {
                  year: "numeric",
                  month: "short",
                  day: "numeric",
                })}
              </time>
              <dl className="relocation-route">
                <div>
                  <dt>From</dt>
                  <dd>{locationLabel(record.previousLocation)}</dd>
                  {record.previousLocation.description && (
                    <dd className="text-secondary">
                      {record.previousLocation.description}
                    </dd>
                  )}
                </div>
                <div>
                  <dt>To</dt>
                  <dd>{locationLabel(record.newLocation)}</dd>
                  {record.newLocation.description && (
                    <dd className="text-secondary">
                      {record.newLocation.description}
                    </dd>
                  )}
                </div>
              </dl>
              <p className="remarks">{record.remarks || "No remarks."}</p>
              <small className="text-secondary">
                Recorded {displayDate(record.createdAt)}
              </small>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
