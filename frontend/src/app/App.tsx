import { useEffect, useState } from 'react'
import { AuthProvider, useAuth } from '../features/auth/AuthProvider'
import { LoginPage } from '../features/auth/LoginPage'
import { CatalogPage } from '../features/catalog/CatalogPage'
import { UsersPage } from '../features/users/UsersPage'

function Workspace() {
  const { state, user, logout } = useAuth()
  const [route, setRoute] = useState(window.location.hash || '#/products')
  const [loggingOut, setLoggingOut] = useState(false)
  useEffect(() => { const update = () => setRoute(window.location.hash || '#/products'); window.addEventListener('hashchange', update); return () => window.removeEventListener('hashchange', update) }, [])
  if (state === 'loading') return <main className="app-loading"><p>Restoring your secure session…</p></main>
  if (state !== 'authenticated' || !user) return <LoginPage />
  const canManageUsers = user.roles.includes('ADMIN')
  async function leave() { setLoggingOut(true); try { await logout() } finally { setLoggingOut(false) } }
  const catalogRoute = route === '#/products' || route === '#/categories'
  return <div className="workspace"><aside className="sidebar"><a className="brand" href="#/products"><span>ME</span><strong>Mini ERP</strong></a><nav aria-label="Primary navigation"><p className="nav-label">Workspace</p><a className="nav-item" href="#/products" aria-current={route === '#/products' ? 'page' : undefined}>Products</a><a className="nav-item" href="#/categories" aria-current={route === '#/categories' ? 'page' : undefined}>Categories</a>{canManageUsers && <a className="nav-item" href="#/users" aria-current={route === '#/users' ? 'page' : undefined}>User administration</a>}<p className="nav-label">Coming soon</p>{['Dashboard', 'Purchasing', 'Inventory', 'Sales'].map((item) => <span className="nav-item disabled" key={item} aria-disabled="true">{item}</span>)}</nav></aside><div className="content"><header className="topbar"><div><span className="eyebrow">CATALOG</span><strong>Secure workspace</strong></div><div className="profile"><div><strong>{user.displayName}</strong><small>{user.username}</small></div><button className="button secondary" disabled={loggingOut} onClick={() => void leave()}>{loggingOut ? 'Signing out…' : 'Sign out'}</button></div></header><main className="page-content">{route === '#/users' ? canManageUsers ? <UsersPage /> : <Denied /> : catalogRoute ? <CatalogPage mode={route === '#/categories' ? 'categories' : 'products'} /> : <CatalogPage mode="products" />}</main></div></div>
}
function Denied() { return <section className="panel"><h1>Access restricted</h1><p>Your current role does not have access to this page.</p></section> }
export function App() { return <AuthProvider><Workspace /></AuthProvider> }
