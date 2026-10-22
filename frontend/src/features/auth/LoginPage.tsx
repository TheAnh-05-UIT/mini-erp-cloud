import { useState, type FormEvent } from 'react'
import { ApiError } from '../../shared/api/http-client'
import { useAuth } from './AuthProvider'

export function LoginPage() {
  const { login, state } = useAuth(); const [username, setUsername] = useState(''); const [password, setPassword] = useState(''); const [error, setError] = useState<string | null>(null); const [busy, setBusy] = useState(false)
  async function submit(event: FormEvent) { event.preventDefault(); setError(null); setBusy(true); try { await login(username, password) } catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Unable to sign in. Please try again.') } finally { setBusy(false) } }
  return <main className="login-page"><section className="login-card" aria-labelledby="login-heading"><span className="eyebrow">MINI ERP CLOUD</span><h1 id="login-heading">Sign in to your workspace</h1><p>Use your organization account to continue.</p>{state === 'expired' && <p className="notice warning" role="status">Your session has expired. Please sign in again.</p>}<form onSubmit={submit}><label>Username<input autoComplete="username" value={username} onChange={(event) => setUsername(event.target.value)} required /></label><label>Password<input type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} required /></label>{error && <p className="notice error" role="alert">{error}</p>}<button className="button primary" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button></form></section></main>
}
