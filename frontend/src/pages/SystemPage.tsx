import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getHealth } from '../api/health';
import { Icon } from '../components/Icon';
import type { HealthResponse } from '../types/health';

type ConnectionState = 'connected' | 'unavailable' | 'unknown' | 'checking';

function StatusBadge({ state }: { state: ConnectionState }) {
  const labels = { connected: 'Connected', unavailable: 'Unavailable', unknown: 'Not verified', checking: 'Checking' };
  return <span className={`status-badge status-${state}`}><span />{labels[state]}</span>;
}

export function SystemPage() {
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  const checkConnection = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    setError(false);
    setHealth(null);
    try {
      const result = await getHealth(signal);
      if (!signal?.aborted) setHealth(result);
    } catch {
      if (!signal?.aborted) setError(true);
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void checkConnection(controller.signal);
    return () => controller.abort();
  }, [checkConnection]);

  const apiState: ConnectionState = loading ? 'checking' : error || health?.api !== 'UP' ? 'unavailable' : 'connected';
  const dbState: ConnectionState = loading ? 'checking' : error ? 'unknown' : health?.database === 'UP' ? 'connected' : 'unavailable';
  const connected = !loading && health?.status === 'UP';
  const title = loading ? 'Checking your connections…' : connected ? 'All systems connected' : 'A connection needs attention';
  const description = loading ? 'Verifying the backend API and PostgreSQL database.' : error
    ? 'The backend could not be reached. Check that the services are running, then try again.'
    : health?.message;

  return (
    <>
      <div className="page-heading">
        <div><div className="eyebrow">YOUR WORKSPACE</div><h1>System overview</h1><p>A connected foundation for your printer inventory.</p></div>
        <button type="button" className="btn btn-refresh" onClick={() => void checkConnection()} disabled={loading}>
          <Icon name="refresh" />{loading ? 'Checking…' : 'Refresh status'}
        </button>
      </div>

      <section className={`connection-banner ${!loading && !connected ? 'needs-attention' : ''}`} aria-live="polite" aria-busy={loading}>
        <div className="banner-icon">{loading ? <span className="spinner-border spinner-border-sm" aria-hidden="true" /> : connected ? <Icon name="check" /> : <span aria-hidden="true">!</span>}</div>
        <div><h2>{title}</h2><p>{description}</p></div>
        <span className="phase-tag">SYSTEM HEALTH</span>
      </section>

      <div className="section-heading"><h2>Connection status</h2><span>Live service checks</span></div>
      <div className="row g-3 mb-4">
        {([
          { name: 'Frontend', detail: 'Your workspace is ready in this browser.', tech: 'React + TypeScript', icon: 'browser', state: 'connected' },
          { name: 'Backend API', detail: 'Receives requests from your workspace.', tech: 'Spring Boot · Java 21', icon: 'server', state: apiState },
          { name: 'Database', detail: 'Checked through a live database query.', tech: 'PostgreSQL', icon: 'database', state: dbState },
        ] as const).map((service, index) => (
          <div className="col-12 col-md-4" key={service.name}>
            <section className="panel service-card h-100">
              <div className="service-card-top"><span className={`service-icon icon-${index}`}><Icon name={service.icon} /></span><StatusBadge state={service.state} /></div>
              <h3>{service.name}</h3><p>{service.detail}</p><div className="service-tech">{service.tech}</div>
            </section>
          </div>
        ))}
      </div>

      <div className="row g-4">
        <div className="col-12 col-xl-8">
          <section className="panel connection-panel h-100">
            <div className="panel-heading"><h2>How the connection works</h2><p>One simple path, from your browser to your database.</p></div>
            <div className="connection-flow" aria-label="Frontend requests the backend API, which queries PostgreSQL">
              <div><Icon name="browser" /><strong>Frontend</strong><small>Your browser</small></div><span className="flow-arrow"><Icon name="arrow" /></span>
              <div><Icon name="server" /><strong>Backend</strong><small>REST API</small></div><span className="flow-arrow"><Icon name="arrow" /></span>
              <div><Icon name="database" /><strong>PostgreSQL</strong><small>Database</small></div>
            </div>
            <dl className="connection-details">
              <div><dt>Health endpoint</dt><dd><a href="/api/health" target="_blank" rel="noreferrer"><code>GET /api/health</code><span className="visually-hidden"> (opens in a new tab)</span></a></dd></div>
              <div><dt>Last checked</dt><dd>{health && !loading ? new Date(health.checkedAt).toLocaleString() : loading ? 'Checking now…' : 'No successful response'}</dd></div>
            </dl>
          </section>
        </div>
        <div className="col-12 col-xl-4">
          <section className="panel next-panel h-100">
            <span className="small-label">YOUR INVENTORY WORKSPACE</span>
            <h2>A home for every printer.</h2>
            <p>Add printers, keep their details current, and find equipment across your organization.</p>
            <div className="up-next">EXPLORE YOUR INVENTORY</div>
            <ul><li>Printer counts by status</li><li>Recent additions and transfers</li></ul>
            <Link className="btn btn-primary" to="/dashboard">Open dashboard</Link>
          </section>
        </div>
      </div>
    </>
  );
}
