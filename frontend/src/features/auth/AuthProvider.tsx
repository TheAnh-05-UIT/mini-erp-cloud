import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { ApiError } from '../../shared/api/http-client'
import { login as loginRequest, logout as logoutRequest, restoreSession, type CurrentUser } from './auth-api'

type AuthState = 'loading' | 'anonymous' | 'authenticated' | 'expired'
type AuthContextValue = { state: AuthState; user: CurrentUser | null; login: (username: string, password: string) => Promise<void>; logout: () => Promise<void>; clearExpired: () => void }
const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>('loading')
  const [user, setUser] = useState<CurrentUser | null>(null)
  useEffect(() => { void restoreSession().then((current) => { setUser(current); setState(current ? 'authenticated' : 'anonymous') }).catch(() => setState('anonymous')) }, [])
  const login = useCallback(async (username: string, password: string) => { const current = await loginRequest(username, password); setUser(current); setState('authenticated') }, [])
  const logout = useCallback(async () => { await logoutRequest(); setUser(null); setState('anonymous') }, [])
  const clearExpired = useCallback(() => { setUser(null); setState('expired') }, [])
  const value = useMemo(() => ({ state, user, login, logout, clearExpired }), [state, user, login, logout, clearExpired])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue { const value = useContext(AuthContext); if (!value) throw new Error('useAuth must be used within AuthProvider'); return value }
export function isSessionError(error: unknown): boolean { return error instanceof ApiError && error.status === 401 }
