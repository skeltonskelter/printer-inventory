import { useCallback, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { auditApi } from '../api/audit';
import { LoadError, Loading } from '../components/InventoryUi';
import { useResource } from '../hooks/useResource';
import { auditActions, auditEntityTypes, type AuditLog } from '../types/audit';

const pageSizes = [25, 50, 100] as const;
const label = (value: string) => value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, letter => letter.toUpperCase());

export function AuditTrailPage() {
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get('page')) || 0);
  const requestedSize = Number(params.get('size'));
  const size = pageSizes.includes(requestedSize as (typeof pageSizes)[number]) ? requestedSize : 25;
  const query = params.toString();
  const resource = useResource(useCallback((signal) => {
    const request = new URLSearchParams(query); request.set('page', String(page)); request.set('size', String(size));
    return auditApi.list(request, signal);
  }, [page, query, size]));

  function filter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const next = new URLSearchParams();
    new FormData(event.currentTarget).forEach((value, key) => { if (String(value).trim()) next.set(key, String(value).trim()); });
    next.set('size', String(size)); setParams(next);
  }
  function goToPage(nextPage: number) { const next = new URLSearchParams(params); next.set('page', String(nextPage)); setParams(next); }
  function changeSize(nextSize: number) { const next = new URLSearchParams(params); next.delete('page'); next.set('size', String(nextSize)); setParams(next); }

  return <>
    <div className="page-heading"><div><div className="eyebrow">ADMINISTRATION</div><h1>Audit Trail</h1><p>Review important inventory, account, and authentication activity.</p></div></div>
    <form className="panel filter-panel audit-filters" onSubmit={filter}>
      <div><label className="form-label" htmlFor="audit-user">User</label><input className="form-control" id="audit-user" name="username" defaultValue={params.get('username') ?? ''} maxLength={100} placeholder="Username" /></div>
      <div><label className="form-label" htmlFor="audit-action">Action</label><select className="form-select" id="audit-action" name="action" defaultValue={params.get('action') ?? ''}><option value="">All actions</option>{auditActions.map(value => <option key={value} value={value}>{label(value)}</option>)}</select></div>
      <div><label className="form-label" htmlFor="audit-entity">Entity Type</label><select className="form-select" id="audit-entity" name="entityType" defaultValue={params.get('entityType') ?? ''}><option value="">All entity types</option>{auditEntityTypes.map(value => <option key={value} value={value}>{label(value)}</option>)}</select></div>
      <div className="audit-filter-actions"><button className="btn btn-primary" type="submit">Apply filters</button><button className="btn btn-outline-secondary" type="button" onClick={() => setParams({ size: String(size) })}>Clear filters</button></div>
    </form>
    {resource.loading ? <Loading /> : resource.error ? <LoadError message={resource.error} retry={resource.reload} /> : resource.data && <>
      <div className="section-heading"><h2>{resource.data.totalElements} audit events</h2><span>Newest first</span></div>
      {resource.data.content.length === 0 ? <section className="panel empty-state"><h2>No audit events found</h2><p>Try clearing the current filters.</p></section> : <>
        <section className="panel audit-table" aria-label="Audit trail"><table className="table align-middle mb-0"><thead><tr><th>Date / Time</th><th>User</th><th>Action</th><th>Entity</th><th>Identifier</th><th>Description</th><th>Details</th></tr></thead><tbody>{resource.data.content.map(entry => <AuditRow key={entry.id} entry={entry} />)}</tbody></table></section>
        <div className="audit-cards">{resource.data.content.map(entry => <AuditCard key={entry.id} entry={entry} />)}</div>
      </>}
      {resource.data.totalElements > 0 && <nav className="pagination-bar" aria-label="Audit pages">
        <label className="pagination-size">Rows per page<select className="form-select form-select-sm" value={size} onChange={event => changeSize(Number(event.target.value))}>{pageSizes.map(value => <option key={value}>{value}</option>)}</select></label>
        <span>Showing {resource.data.page * resource.data.size + 1}–{Math.min((resource.data.page + 1) * resource.data.size, resource.data.totalElements)} of {resource.data.totalElements}</span>
        <div className="pagination-controls"><button className="btn btn-outline-secondary" disabled={resource.data.page === 0} onClick={() => goToPage(page - 1)}>Previous</button><span>Page {resource.data.page + 1} of {Math.max(resource.data.totalPages, 1)}</span><button className="btn btn-outline-secondary" disabled={resource.data.page + 1 >= resource.data.totalPages} onClick={() => goToPage(page + 1)}>Next</button></div>
      </nav>}
    </>}
  </>;
}

function AuditRow({ entry }: { entry: AuditLog }) { return <tr><td>{new Date(entry.timestamp).toLocaleString()}</td><td><strong>{entry.username}</strong><small>{entry.userRole}</small></td><td><span className="audit-badge">{label(entry.action)}</span></td><td>{label(entry.entityType)}</td><td>{entry.entityIdentifier || '—'}</td><td>{entry.description}</td><td><AuditDetails entry={entry} /></td></tr>; }
function AuditCard({ entry }: { entry: AuditLog }) { return <article className="panel audit-card"><div className="audit-card-heading"><span className="audit-badge">{label(entry.action)}</span><time>{new Date(entry.timestamp).toLocaleString()}</time></div><h2>{entry.description}</h2><dl><dt>User</dt><dd>{entry.username} ({entry.userRole})</dd><dt>Entity</dt><dd>{label(entry.entityType)}</dd><dt>Identifier</dt><dd>{entry.entityIdentifier || '—'}</dd></dl><AuditDetails entry={entry} /></article>; }
function AuditDetails({ entry }: { entry: AuditLog }) {
  const hasOld = Object.keys(entry.oldValues).length > 0, hasNew = Object.keys(entry.newValues).length > 0;
  if (!hasOld && !hasNew) return <span className="text-secondary">—</span>;
  return <details className="audit-details"><summary>View details</summary><div className="audit-change-grid">{hasOld && <ValueList title="Old Values" values={entry.oldValues} />}{hasNew && <ValueList title="New Values" values={entry.newValues} />}</div></details>;
}
function ValueList({ title, values }: { title: string; values: Record<string, unknown> }) { return <section><h3>{title}</h3><dl>{Object.entries(values).map(([key, value]) => <div key={key}><dt>{label(key.replace(/([a-z])([A-Z])/g, '$1 $2'))}</dt><dd>{value === null || value === '' ? '—' : String(value)}</dd></div>)}</dl></section>; }
