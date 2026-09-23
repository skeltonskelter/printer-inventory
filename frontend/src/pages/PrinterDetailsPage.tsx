import { useCallback, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { inventory } from "../api/inventory";
import { useResource } from "../hooks/useResource";
import { LoadError, Loading, PrinterBadge } from "../components/InventoryUi";
import { DeletePrinterDialog } from "../components/DeletePrinterDialog";
import { displayDate, stickerLabel } from "../utils/inventory";
import { RelocationHistoryList } from "../components/RelocationHistoryList";

export function PrinterDetailsPage() {
  const { id = "" } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const resource = useResource(
    useCallback((signal) => inventory.get(id, signal), [id]),
  );
  const [deleting, setDeleting] = useState(false);
  const printer = resource.data;
  return (
    <>
      <Link to="/printers" className="back-link">
        ← All printers
      </Link>
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <LoadError message={resource.error} retry={resource.reload} />
      ) : (
        printer && (
          <>
            <div className="page-heading">
              <div>
                <div className="eyebrow">PRINTER DETAILS</div>
                <h1>{stickerLabel(printer.stickerNumber)}</h1>
                <p>
                  {printer.brand} {printer.model}
                </p>
              </div>
              <div className="d-flex gap-2 flex-wrap">
                <Link
                  to={`/printers/${printer.id}/relocate`}
                  className="btn btn-outline-primary"
                >
                  Relocate
                </Link>
                <Link
                  to={`/printers/${printer.id}/edit`}
                  className="btn btn-primary"
                >
                  Edit printer
                </Link>
                <button
                  className="btn btn-outline-danger"
                  onClick={() => setDeleting(true)}
                >
                  Delete
                </button>
              </div>
            </div>
            {location.state?.notice && (
              <div className="alert alert-success" role="status">
                {location.state.notice}
              </div>
            )}
            <div className="row g-4">
              <div className="col-12 col-xl-8">
                <section className="panel details-panel">
                  <h2>Printer information</h2>
                  <dl className="details-grid">
                    <div>
                      <dt>Serial number</dt>
                      <dd>{printer.serialNumber || "Not recorded"}</dd>
                    </div>
                    <div>
                      <dt>Sticker number</dt>
                      <dd>{stickerLabel(printer.stickerNumber)}</dd>
                    </div>
                    <div>
                      <dt>Brand</dt>
                      <dd>{printer.brand}</dd>
                    </div>
                    <div>
                      <dt>Model</dt>
                      <dd>{printer.model}</dd>
                    </div>
                    <div>
                      <dt>Date added</dt>
                      <dd>{displayDate(printer.createdAt)}</dd>
                    </div>
                    <div>
                      <dt>Last updated</dt>
                      <dd>{displayDate(printer.updatedAt)}</dd>
                    </div>
                    <div>
                      <dt>Status</dt>
                      <dd>
                        <PrinterBadge status={printer.status} />
                      </dd>
                    </div>
                    <div className="detail-wide">
                      <dt>Remarks</dt>
                      <dd className="remarks">
                        {printer.remarks || "No remarks."}
                      </dd>
                    </div>
                  </dl>
                </section>
              </div>
              <div className="col-12 col-xl-4">
                <section className="panel details-panel">
                  <h2>Current location</h2>
                  <dl className="location-details">
                    {(
                      [
                        ["Department", printer.location.department],
                        ["Section", printer.location.section],
                        ["Building", printer.location.building],
                        ["Floor", printer.location.floor],
                        ["Room", printer.location.room],
                        [
                          "Additional description",
                          printer.location.description,
                        ],
                      ] as const
                    ).map(([label, value]) => (
                      <div key={label}>
                        <dt>{label}</dt>
                        <dd>{value || "Not recorded"}</dd>
                      </div>
                    ))}
                  </dl>
                </section>
              </div>
            </div>
            <RelocationHistoryList printerId={printer.id} />
            {deleting && (
              <DeletePrinterDialog
                printer={printer}
                onCancel={() => setDeleting(false)}
                onDeleted={() => navigate("/printers", { replace: true })}
              />
            )}
          </>
        )
      )}
    </>
  );
}
