import { apiRequest, refreshCsrfToken } from '../../shared/api/http-client'

export type CurrentUser = { id: string; username: string; displayName: string; roles: string[] }
type AuthenticationContext = { currentUser: CurrentUser }

export async function restoreSession(): Promise<CurrentUser | null> {
  try { return (await apiRequest<AuthenticationContext>('/api/v1/auth/me')).currentUser } catch (error) {
    if (error instanceof Error && 'status' in error && (error as { status?: number }).status === 401) return null
    throw error
  }
}

export async function login(username: string, password: string): Promise<CurrentUser> {
  await refreshCsrfToken()
  const result = await apiRequest<AuthenticationContext>('/api/v1/auth/login', { method: 'POST', body: { username, password } })
  await refreshCsrfToken()
  return result.currentUser
}

export async function logout(): Promise<void> {
  await apiRequest<{ status: string }>('/api/v1/auth/logout', { method: 'POST' })
}
