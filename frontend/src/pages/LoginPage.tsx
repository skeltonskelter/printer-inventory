import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const username = String(form.get('username') ?? '').trim();
    const password = String(form.get('password') ?? '');
    setError('');
    setSubmitting(true);
    try {
      await login(username, password);
      navigate('/printers', { replace: true });
    } catch {
      setError('Invalid username or password.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="login-page">
      <section className="login-card" aria-labelledby="login-title">
        <img className="login-logo" src="/ictlogo.png" alt="ICT Department logo" />
        <div className="login-heading">
          <h1 id="login-title">Printer Inventory</h1>
          <p>Sign in to continue to the inventory workspace.</p>
        </div>
        {error && <div className="alert alert-danger" role="alert">{error}</div>}
        <form onSubmit={submit}>
          <div className="mb-3">
            <label className="form-label" htmlFor="login-username">Username</label>
            <input id="login-username" name="username" className="form-control" autoComplete="username" required autoFocus />
          </div>
          <div className="mb-4">
            <label className="form-label" htmlFor="login-password">Password</label>
            <input id="login-password" name="password" type="password" className="form-control" autoComplete="current-password" required />
          </div>
          <button className="btn btn-primary w-100" type="submit" disabled={submitting}>
            {submitting ? 'Signing in…' : 'Login'}
          </button>
        </form>
      </section>
    </main>
  );
}
