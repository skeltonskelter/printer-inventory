import { useCallback } from "react";
import { Link } from "react-router-dom";
import { inventory } from "../api/inventory";
import { useResource } from "../hooks/useResource";
import { Icon } from "../components/Icon";
import { Loading, LoadError, PrinterBadge } from "../components/InventoryUi";
import { displayDate, locationLabel, stickerLabel } from "../utils/inventory";

export function DashboardPage() {
  const { data, loading, error, reload } = useResource(
    useCallback((signal) => inventory.dashboard(signal), []),
  );
  const metrics = data
    ? [
        {
          label: "Total printers",
          value: data.totalPrinters,
          to: "/printers",
          note: "All current inventory",
          tone: "purple",
        },
        {
          label: "Active printers",
          value: data.statusCounts.ACTIVE,
          to: "/printers?status=ACTIVE",
          note: "Ready for everyday work",
          tone: "green",
        },
        {
          label: "Under repair",
          value: data.statusCounts.UNDER_REPAIR,
          to: "/printers?status=UNDER_REPAIR",
          note: "Repairs in progress",
          tone: "amber",
        },
        {
          label: "In storage",
          value: data.statusCounts.STORAGE,
          to: "/printers?status=STORAGE",
          note: "Stored equipment",
          tone: "blue",
        },
        {
          label: "Retired printers",
          value: data.statusCounts.RETIRED,
          to: "/printers?status=RETIRED",
          note: "Retired from service",
          tone: "gray",
        },
        {
          label: "Relocated printers",
          value: data.relocatedPrinters,
          to: null,
          note: "Moved at least once",
          tone: "purple",
        },
      ]
    : [];
  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow">YOUR WORKSPACE AT A GLANCE</div>
          <h1>Dashboard</h1>
          <p>A clear view of your printers and their latest movements.</p>
        </div>
        <div className="heading-actions">
          <button
            type="button"
            className="btn btn-refresh"
            disabled={loading}
            onClick={reload}
          >
            <Icon name="refresh" />
            {loading ? "Refreshing…" : "Refresh dashboard"}
          </button>
          <Link to="/printers/new" className="btn btn-primary">
            + Add printer
          </Link>
        </div>
      </div>
      {loading ? (
        <Loading />
      ) : error ? (
        <LoadError message={error} retry={reload} />
      ) : (
        data && (
          <>
            <section
              className="dashboard-metrics"
              aria-label="Inventory totals"
            >
              {metrics.map((metric) => (
                <article
                  className={`panel metric-card metric-${metric.tone}`}
                  key={metric.label}
                >
                  <h2>
                    {metric.to ? (
                      <Link to={metric.to}>
                        {metric.label}
                        <span aria-hidden="true"> ↗</span>
                      </Link>
                    ) : (
                      metric.label
                    )}
                  </h2>
                  <strong className="metric-value">
                    {metric.value.toLocaleString()}
                  </strong>
                  <p>{metric.note}</p>
                </article>
              ))}
            </section>
            <div className="dashboard-context">
              <p>
                Also in your inventory:{" "}
                <Link to="/printers?status=FOR_REPAIR">
                  For Repair ({data.statusCounts.FOR_REPAIR.toLocaleString()})
                </Link>
                <span aria-hidden="true"> · </span>
                <Link to="/printers?status=DISPOSED">
                  Disposed ({data.statusCounts.DISPOSED.toLocaleString()})
                </Link>
              </p>
              <p>
                Deleted printers are excluded. Relocated printers are counted
                once.
              </p>
            </div>
            {data.totalPrinters === 0 && (
              <section className="panel empty-state mb-4">
                <h2>Your inventory starts here</h2>
                <p>
                  Add your first printer to see its status and activity on this
                  dashboard.
                </p>
                <Link to="/printers/new" className="btn btn-outline-primary">
                  Add your first printer
                </Link>
              </section>
            )}
            <div className="dashboard-activity">
              <section
                className="panel activity-panel"
                aria-labelledby="recent-added"
              >
                <div className="activity-heading">
                  <div>
                    <h2 id="recent-added">Recently added</h2>
                    <p>The latest five printers in your inventory.</p>
                  </div>
                  <Link to="/printers">View all</Link>
                </div>
                {data.recentlyAdded.length === 0 ? (
                  <p className="activity-empty">No printers added yet.</p>
                ) : (
                  <ul className="activity-list">
                    {data.recentlyAdded.map((printer) => (
                      <li key={printer.id}>
                        <div className="activity-title">
                          <Link
                            className="sticker-link"
                            to={`/printers/${printer.id}`}
                          >
                            {stickerLabel(printer.stickerNumber)}
                          </Link>
                          <PrinterBadge status={printer.status} />
                        </div>
                        <p>
                          {printer.brand} {printer.model}
                        </p>
                        <p className="activity-location">
                          {locationLabel(printer.location)}
                        </p>
                        <small>Added {displayDate(printer.createdAt)}</small>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
              <section
                className="panel activity-panel"
                aria-labelledby="recent-transfers"
              >
                <div className="activity-heading">
                  <div>
                    <h2 id="recent-transfers">Recent transfers</h2>
                    <p>The latest five recorded transfers.</p>
                  </div>
                  <Icon name="arrow" />
                </div>
                {data.recentTransfers.length === 0 ? (
                  <p className="activity-empty">
                    No transfers recorded for current printers.
                  </p>
                ) : (
                  <ul className="activity-list">
                    {data.recentTransfers.map(({ printer, relocation }) => (
                      <li key={relocation.id}>
                        <div className="activity-title">
                          <Link
                            className="sticker-link"
                            to={`/printers/${printer.id}`}
                          >
                            {stickerLabel(printer.stickerNumber)}
                          </Link>
                          <span className="transfer-label">Relocated</span>
                        </div>
                        <p>
                          <span className="activity-direction">From</span>{" "}
                          {locationLabel(relocation.previousLocation)}
                        </p>
                        <p>
                          <span className="activity-direction">To</span>{" "}
                          {locationLabel(relocation.newLocation)}
                        </p>
                        <small>
                          Transfer date {relocation.relocationDate} · Recorded{" "}
                          {displayDate(relocation.createdAt)}
                        </small>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            </div>
            <p className="dashboard-updated">
              Updated {displayDate(data.checkedAt)}. Refresh to see changes made
              elsewhere.
            </p>
          </>
        )
      )}
    </>
  );
}
