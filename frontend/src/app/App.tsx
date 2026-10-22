import { useEffect, useState } from 'react'
import { AuthProvider, useAuth } from '../features/auth/AuthProvider'
import { LoginPage } from '../features/auth/LoginPage'
import { UsersPage } from '../features/users/UsersPage'

function Workspace() {
  const { state, user, logout } = useAuth(); const [route, setRoute] = useState(window.location.hash || '#/users'); const [loggingOut, setLoggingOut] = useState(false)
  useEffect(() => { const update = () => setRoute(window.location.hash || '#/users'); window.addEventListener('hashchange', update); return () => window.removeEventListener('hashchange', update) }, [])
  if (state === 'loading') return <main className="app-loading"><p>Restoring your secure session…</p></main>
  if (state !== 'authenticated' || !user) return <LoginPage />
  const canManageUsers = user.roles.includes('ADMIN')
  async function leave() { setLoggingOut(true); try { await logout() } finally { setLoggingOut(false) } }
  return <div className="workspace"><aside className="sidebar"><a className="brand" href="#/users"><span>ME</span><strong>Mini ERP</strong></a><nav aria-label="Primary navigation"><p className="nav-label">Workspace</p><a className="nav-item" href="#/users" aria-current={route === '#/users' ? 'page' : undefined}>User administration</a><p className="nav-label">Coming soon</p>{['Dashboard', 'Products', 'Purchasing', 'Inventory', 'Sales'].map((item) => <span className="nav-item disabled" key={item} aria-disabled="true">{item}</span>)}</nav></aside><div className="content"><header className="topbar"><div><span className="eyebrow">ADMINISTRATION</span><strong>Secure workspace</strong></div><div className="profile"><div><strong>{user.displayName}</strong><small>{user.username}</small></div><button className="button secondary" disabled={loggingOut} onClick={() => void leave()}>{loggingOut ? 'Signing out…' : 'Sign out'}</button></div></header><main className="page-content">{canManageUsers && route === '#/users' ? <UsersPage /> : <section className="panel"><h1>Access restricted</h1><p>Your current role does not have access to User Administration.</p></section>}</main></div></div>
}

export function App() { return <AuthProvider><Workspace /></AuthProvider> }
