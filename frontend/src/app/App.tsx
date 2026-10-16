import { SystemStatusPage } from '../features/system-status/SystemStatusPage'

export function App() {
  return (
    <main>
      <h1>Mini ERP</h1>
      <p>Application foundation</p>
      <section aria-labelledby="backend-status-heading">
        <h2 id="backend-status-heading">Backend status</h2>
        <SystemStatusPage />
      </section>
    </main>
  )
}
