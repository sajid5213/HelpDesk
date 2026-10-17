import { useEffect, useState } from 'react'

type Health = 'checking' | 'UP' | 'DOWN' | 'unreachable'

function App() {
  const [health, setHealth] = useState<Health>('checking')

  useEffect(() => {
    fetch('/actuator/health')
      .then((res) => res.json())
      .then((body: { status: string }) => setHealth(body.status === 'UP' ? 'UP' : 'DOWN'))
      .catch(() => setHealth('unreachable'))
  }, [])

  return (
    <main>
      <h1>HelpDesk</h1>
      <p>
        Backend: <strong data-status={health}>{health}</strong>
      </p>
    </main>
  )
}

export default App
