import { useState } from 'react';
import { api } from '../api.js';
import { Logo } from '../icons.jsx';

// Demo sign-in: looks the user up by email. There are no passwords in this demo.
export default function Account({ user, onUser, onNavigate }) {
  const [mode, setMode] = useState('signin');
  const [email, setEmail] = useState('');
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  if (user) {
    const initials = `${user.firstName?.[0] || ''}${user.lastName?.[0] || ''}`.toUpperCase();
    return (
      <section className="auth-card">
        <span className="avatar">{initials || '?'}</span>
        <h1>{user.firstName} {user.lastName}</h1>
        <p className="muted">{user.email}</p>
        <p className="muted small">Customer #{user.id}</p>
        <div className="state-actions">
          <button className="btn primary" onClick={() => onNavigate('orders')}>My orders</button>
          <button className="btn ghost" onClick={() => onUser(null)}>Sign out</button>
        </div>
      </section>
    );
  }

  const run = async (action) => {
    setBusy(true);
    setError('');
    try {
      await action();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };

  // run() catches and displays its own errors, so the returned promise is deliberately not awaited
  const signIn = (event) => {
    event.preventDefault();
    void run(async () => {
      const users = await api.getUsers();
      const match = users.find((u) => u.email?.toLowerCase() === email.trim().toLowerCase());
      if (!match) throw new Error('No account with that email yet. Create one instead.');
      onUser(match);
      onNavigate('shop');
    });
  };

  const register = (event) => {
    event.preventDefault();
    void run(async () => {
      const users = await api.getUsers();
      if (users.some((u) => u.email?.toLowerCase() === form.email.trim().toLowerCase())) {
        throw new Error('That email is already registered. Sign in instead.');
      }
      onUser(await api.createUser({ ...form, email: form.email.trim() }));
      onNavigate('shop');
    });
  };

  const field = (name) => ({
    value: form[name],
    onChange: (e) => setForm({ ...form, [name]: e.target.value }),
    required: true,
  });

  return (
    <section className="auth-card">
      <Logo size={44} />
      <h1>{mode === 'signin' ? 'Welcome back' : 'Create your account'}</h1>
      <p className="muted">
        {mode === 'signin' ? 'Sign in to check out and track your orders.' : 'It takes less than a minute.'}
      </p>

      <div className="tabs" role="tablist">
        <button type="button" role="tab" aria-selected={mode === 'signin'} className={mode === 'signin' ? 'tab active' : 'tab'}
          onClick={() => { setMode('signin'); setError(''); }}>
          Sign in
        </button>
        <button type="button" role="tab" aria-selected={mode === 'register'} className={mode === 'register' ? 'tab active' : 'tab'}
          onClick={() => { setMode('register'); setError(''); }}>
          Create account
        </button>
      </div>

      {error && <div className="alert error">{error}</div>}

      {mode === 'signin' ? (
        <form className="form" onSubmit={signIn}>
          <label>
            Email
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" required />
          </label>
          <button type="submit" className="btn primary block" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
        </form>
      ) : (
        <form className="form" onSubmit={register}>
          <div className="two-col">
            <label>First name<input {...field('firstName')} /></label>
            <label>Last name<input {...field('lastName')} /></label>
          </div>
          <label>Email<input type="email" placeholder="you@example.com" {...field('email')} /></label>
          <button type="submit" className="btn primary block" disabled={busy}>{busy ? 'Creating…' : 'Create account'}</button>
        </form>
      )}

      <p className="muted small">Demo store: no password needed.</p>
    </section>
  );
}
