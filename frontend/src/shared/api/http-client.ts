export class ApiError extends Error {
  readonly kind: 'http' | 'network' | 'invalid-response'
  readonly status?: number

  constructor(
    message: string,
    kind: 'http' | 'network' | 'invalid-response',
    status?: number,
  ) {
    super(message)
    this.name = 'ApiError'
    this.kind = kind
    this.status = status
  }
}

export async function getJson<T>(path: string): Promise<T> {
  let response: Response

  try {
    response = await fetch(path, {
      headers: {
        Accept: 'application/json',
      },
    })
  } catch {
    throw new ApiError('The backend could not be reached.', 'network')
  }

  if (!response.ok) {
    throw new ApiError(`The backend returned HTTP ${response.status}.`, 'http', response.status)
  }

  if (!response.headers.get('content-type')?.includes('application/json')) {
    throw new ApiError('The backend returned an unexpected response.', 'invalid-response', response.status)
  }

  try {
    return (await response.json()) as T
  } catch {
    throw new ApiError('The backend returned invalid JSON.', 'invalid-response', response.status)
  }
}
