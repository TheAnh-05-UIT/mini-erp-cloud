import { useEffect, useState } from 'react'

import { ApiError, getJson } from '../../shared/api/http-client'

type BackendStatus = {
  application: string
  status: string
}

type StatusState =
  | { kind: 'loading' }
  | { kind: 'success'; value: BackendStatus }
  | { kind: 'error'; message: string }

export function SystemStatusPage() {
  const [state, setState] = useState<StatusState>({ kind: 'loading' })

  useEffect(() => {
    let active = true

    void getJson<BackendStatus>('/api/v1/status')
      .then((value) => {
        if (active) {
          setState({ kind: 'success', value })
        }
      })
      .catch((error: unknown) => {
        if (active) {
          const message = error instanceof ApiError ? error.message : 'The backend status is unavailable.'
          setState({ kind: 'error', message })
        }
      })

    return () => {
      active = false
    }
  }, [])

  if (state.kind === 'loading') {
    return <p aria-live="polite">Checking backend status…</p>
  }

  if (state.kind === 'error') {
    return <p role="alert">{state.message}</p>
  }

  return (
    <dl>
      <div>
        <dt>Application</dt>
        <dd>{state.value.application}</dd>
      </div>
      <div>
        <dt>Status</dt>
        <dd>{state.value.status}</dd>
      </div>
    </dl>
  )
}
