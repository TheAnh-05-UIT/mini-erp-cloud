export type ApiErrorBody = { code?: string; message?: string; status?: number }

export class ApiError extends Error {
  readonly kind: 'http' | 'network' | 'invalid-response'
  readonly status?: number
  readonly code?: string

  constructor(message: string, kind: ApiError['kind'], status?: number, code?: string) {
    super(message)
    this.name = 'ApiError'
    this.kind = kind
    this.status = status
    this.code = code
  }
}

type RequestOptions = Omit<RequestInit, 'body'> & { body?: unknown }

function csrfToken(): string | undefined {
  return document.cookie.split('; ').find((value) => value.startsWith('XSRF-TOKEN='))?.split('=').slice(1).join('=')
}

async function errorFor(response: Response): Promise<ApiError> {
  let payload: ApiErrorBody | undefined
  try { payload = await response.json() as ApiErrorBody } catch { /* Safe fallback for non-JSON errors. */ }
  return new ApiError(payload?.message ?? `The server returned HTTP ${response.status}.`, 'http', response.status, payload?.code)
}

/** Calls the same-origin API without storing credentials or session material. */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method?.toUpperCase() ?? 'GET'
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (options.body !== undefined) headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const token = csrfToken()
    if (!token) throw new ApiError('Your security token is missing. Refresh the page and try again.', 'http', 403, 'CSRF_TOKEN_INVALID')
    headers.set('X-XSRF-TOKEN', decodeURIComponent(token))
  }

  let response: Response
  try {
    response = await fetch(path, { ...options, method, headers, credentials: 'include', body: options.body === undefined ? undefined : JSON.stringify(options.body) })
  } catch {
    throw new ApiError('The backend could not be reached. Check that it is running.', 'network')
  }
  if (!response.ok) throw await errorFor(response)
  if (response.status === 204) return undefined as T
  if (!response.headers.get('content-type')?.includes('application/json')) throw new ApiError('The backend returned an unexpected response.', 'invalid-response', response.status)
  try { return await response.json() as T } catch { throw new ApiError('The backend returned invalid JSON.', 'invalid-response', response.status) }
}

/** Obtains the session-bound CSRF cookie used only to transport the request token. */
export async function refreshCsrfToken(): Promise<void> { await apiRequest<void>('/api/v1/auth/csrf') }

/** Compatibility helper for safe read-only status checks. */
export async function getJson<T>(path: string): Promise<T> { return apiRequest<T>(path) }
