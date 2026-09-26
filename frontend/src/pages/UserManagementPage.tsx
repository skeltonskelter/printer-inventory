import { useCallback, useState, type FormEvent } from 'react';
import { apiProblem } from '../api/inventory';
import { usersApi } from '../api/users';
import { useAuth } from '../auth/AuthContext';
import { LoadError, Loading } from '../components/InventoryUi';
import { useResource } from '../hooks/useResource';
import type { ApiProblem } from '../types/inventory';
import type { ManagedUser, UserRole } from '../types/auth';

type Editor = { kind: 'create' } | { kind: 'edit'; user: ManagedUser } | { kind: 'password'; user: ManagedUser } | null;

export function UserManagementPage() {
  const resource = useResource(useCallback((signal) => usersApi.list(signal), []));
  const { refresh } = useAuth();
  const [editor, setEditor] = useState<Editor>(null);
  const [notice, setNotice] = useState('');
  const [problem, setProblem] = useState<ApiProblem | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  async function setStatus(user: ManagedUser) {
    setBusyId(user.id); setProblem(null); setNotice('');
    try {
      await usersApi.setStatus(user.id, !user.enabled);
      setNotice(`${user.fullName} ${user.enabled ? 'disabled' : 'enabled'}.`);
      resource.reload();
      await refresh();
    } catch (error) { setProblem(apiProblem(error)); }
    finally { setBusyId(null); }
  }

  function open(next: Editor) { setEditor(next); setProblem(null); setNotice(''); }

  return <>
    <div className="page-heading">
      <div><div className="eyebrow">ADMINISTRATION</div><h1>User Management</h1><p>Create accounts and control access to the inventory workspace.</p></div>
      <button className="btn btn-primary" type="button" onClick={() => open({ kind: 'create' })}>+ Add User</button>
    </div>
    {notice && <div className="alert alert-success" role="status">{notice}</div>}
    {problem && <div className="alert alert-danger" role="alert">{problem.message}</div>}
    {editor && <UserEditor editor={editor} onCancel={() => setEditor(null)} onSaved={async (message) => {
      setEditor(null); setNotice(message); resource.reload(); await refresh();
    }} />}
    {resource.loading ? <Loading /> : resource.error ? <LoadError message={resource.error} retry={resource.reload} /> : resource.data && <>
      <section className="panel user-table" aria-label="Application users">
        <table className="table align-middle mb-0"><thead><tr><th>Full Name</th><th>Username</th><th>Role</th><th>Status</th><th>Created At</th><th>Actions</th></tr></thead>
          <tbody>{resource.data.map(user => <tr key={user.id}>
            <td><strong>{user.fullName}</strong></td><td>{user.username}</td><td><RoleBadge role={user.role} /></td><td><StatusBadge enabled={user.enabled} /></td>
            <td>{new Date(user.createdAt).toLocaleString()}</td><td><UserActions user={user} busy={busyId === user.id} edit={() => open({ kind: 'edit', user })} password={() => open({ kind: 'password', user })} status={() => void setStatus(user)} /></td>
          </tr>)}</tbody></table>
      </section>
      <div className="user-cards">{resource.data.map(user => <article className="panel user-card" key={user.id}>
        <h2>{user.fullName}</h2><dl><dt>Username</dt><dd>{user.username}</dd><dt>Role</dt><dd><RoleBadge role={user.role} /></dd><dt>Status</dt><dd><StatusBadge enabled={user.enabled} /></dd><dt>Created At</dt><dd>{new Date(user.createdAt).toLocaleString()}</dd></dl>
        <UserActions user={user} busy={busyId === user.id} edit={() => open({ kind: 'edit', user })} password={() => open({ kind: 'password', user })} status={() => void setStatus(user)} />
      </article>)}</div>
    </>}
  </>;
}

function RoleBadge({ role }: { role: UserRole }) { return <span className="user-badge">{role}</span>; }
function StatusBadge({ enabled }: { enabled: boolean }) { return <span className={`user-badge ${enabled ? 'user-active' : 'user-disabled'}`}>{enabled ? 'Active' : 'Disabled'}</span>; }
function UserActions({ user, busy, edit, password, status }: { user: ManagedUser; busy: boolean; edit: () => void; password: () => void; status: () => void }) {
  return <div className="user-actions"><button className="btn btn-sm btn-outline-secondary" onClick={edit}>Edit</button><button className="btn btn-sm btn-outline-secondary" onClick={password}>Reset Password</button><button className={`btn btn-sm ${user.enabled ? 'btn-outline-danger' : 'btn-outline-primary'}`} disabled={busy} onClick={status}>{user.enabled ? 'Disable' : 'Enable'}</button></div>;
}

function UserEditor({ editor, onCancel, onSaved }: { editor: Exclude<Editor, null>; onCancel: () => void; onSaved: (message: string) => Promise<void> }) {
  const user = editor.kind === 'create' ? null : editor.user;
  const passwordOnly = editor.kind === 'password';
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<ApiProblem | null>(null);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = new FormData(event.currentTarget); const text = (name: string) => String(form.get(name) ?? '').trim();
    const password = String(form.get('password') ?? ''); const confirmPassword = String(form.get('confirmPassword') ?? '');
    const errors: Record<string, string> = {};
    if (!passwordOnly) { if (!text('fullName')) errors.fullName = 'Full Name is required.'; if (!text('username')) errors.username = 'Username is required.'; }
    if (editor.kind !== 'edit') { if (password.length < 12) errors.password = 'Password must contain at least 12 characters.'; if (password !== confirmPassword) errors.confirmPassword = 'Passwords do not match.'; }
    if (Object.keys(errors).length) { setProblem({ message: 'Please correct the highlighted fields.', fieldErrors: errors }); return; }
    setBusy(true); setProblem(null);
    try {
      if (editor.kind === 'create') await usersApi.create({ fullName: text('fullName'), username: text('username'), password, confirmPassword, role: text('role') as UserRole, enabled: text('enabled') === 'true' });
      else if (editor.kind === 'edit') await usersApi.update(user!.id, { fullName: text('fullName'), username: text('username'), role: text('role') as UserRole, enabled: text('enabled') === 'true' });
      else await usersApi.resetPassword(user!.id, password, confirmPassword);
      await onSaved(editor.kind === 'create' ? 'User created.' : editor.kind === 'edit' ? 'User updated.' : 'Password reset.');
    } catch (error) { setProblem(apiProblem(error)); setBusy(false); }
  }
  const invalid = (name: string) => problem?.fieldErrors[name] ? ' is-invalid' : '';
  return <form className="panel inventory-form user-editor mb-4" onSubmit={submit} aria-label={passwordOnly ? `Reset password for ${user!.username}` : editor.kind === 'create' ? 'Add user' : `Edit ${user!.username}`}>
    <h2>{passwordOnly ? `Reset Password — ${user!.username}` : editor.kind === 'create' ? 'Add User' : 'Edit User'}</h2>
    {problem && <div className="alert alert-danger" role="alert">{problem.message}</div>}
    <fieldset disabled={busy}><div className="row g-3">
      {!passwordOnly && <><div className="col-12 col-md-6"><label className="form-label" htmlFor="user-full-name">Full Name *</label><input id="user-full-name" name="fullName" className={`form-control${invalid('fullName')}`} defaultValue={user?.fullName ?? ''} maxLength={200} required /><div className="invalid-feedback">{problem?.fieldErrors.fullName}</div></div>
      <div className="col-12 col-md-6"><label className="form-label" htmlFor="user-username">Username *</label><input id="user-username" name="username" className={`form-control${invalid('username')}`} defaultValue={user?.username ?? ''} maxLength={100} required autoComplete="off" /><div className="invalid-feedback">{problem?.fieldErrors.username}</div></div></>}
      {editor.kind !== 'edit' && <><div className="col-12 col-md-6"><label className="form-label" htmlFor="user-password">{passwordOnly ? 'New Password' : 'Password'} *</label><input id="user-password" name="password" type="password" className={`form-control${invalid('password')}`} minLength={12} maxLength={72} required autoComplete="new-password" /><div className="invalid-feedback">{problem?.fieldErrors.password}</div></div>
      <div className="col-12 col-md-6"><label className="form-label" htmlFor="user-confirm-password">Confirm Password *</label><input id="user-confirm-password" name="confirmPassword" type="password" className={`form-control${invalid('confirmPassword')}`} minLength={12} maxLength={72} required autoComplete="new-password" /><div className="invalid-feedback">{problem?.fieldErrors.confirmPassword}</div></div></>}
      {!passwordOnly && <><div className="col-12 col-md-6"><label className="form-label" htmlFor="user-role">Role *</label><select id="user-role" name="role" className="form-select" defaultValue={user?.role ?? 'USER'} required><option value="USER">USER</option><option value="ADMIN">ADMIN</option></select></div>
      <div className="col-12 col-md-6"><label className="form-label" htmlFor="user-status">Status *</label><select id="user-status" name="enabled" className="form-select" defaultValue={String(user?.enabled ?? true)} required><option value="true">Active</option><option value="false">Disabled</option></select></div></>}
    </div><div className="form-actions"><button className="btn btn-primary" type="submit">{busy ? 'Saving…' : passwordOnly ? 'Reset Password' : 'Save User'}</button><button className="btn btn-outline-secondary" type="button" onClick={onCancel}>Cancel</button></div></fieldset>
  </form>;
}
