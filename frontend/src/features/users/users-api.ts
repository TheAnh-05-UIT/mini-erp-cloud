import { apiRequest } from '../../shared/api/http-client'

export const ROLE_CODES = ['ADMIN', 'PURCHASING_STAFF', 'WAREHOUSE_STAFF', 'SALES_STAFF', 'MANAGER'] as const
export type RoleCode = typeof ROLE_CODES[number]
export type User = { id: string; username: string; displayName: string; active: boolean; roles: RoleCode[] }
export type UserPage = { content: User[]; page: number; size: number; totalElements: number; totalPages: number }
export type CreateUserInput = { username: string; displayName: string; initialPassword: string; roleCodes: RoleCode[] }

export const usersApi = {
  list: (page: number, size = 20) => apiRequest<UserPage>(`/api/v1/users?page=${page}&size=${size}`),
  get: (id: string) => apiRequest<User>(`/api/v1/users/${id}`),
  create: (input: CreateUserInput) => apiRequest<User>('/api/v1/users', { method: 'POST', body: input }),
  update: (id: string, displayName: string) => apiRequest<User>(`/api/v1/users/${id}`, { method: 'PATCH', body: { displayName } }),
  activate: (id: string) => apiRequest<User>(`/api/v1/users/${id}/activate`, { method: 'POST' }),
  deactivate: (id: string) => apiRequest<User>(`/api/v1/users/${id}/deactivate`, { method: 'POST' }),
  replaceRoles: (id: string, roleCodes: RoleCode[]) => apiRequest<User>(`/api/v1/users/${id}/roles`, { method: 'PUT', body: { roleCodes } }),
}
