import { useEffect } from 'react'

import { AuthProvider, useAuth } from './auth/AuthContext'
import { AppShell } from './components/AppShell'
import { ToastProvider } from './components/Toast'
import { LoginPage } from './pages/LoginPage'
import { TicketDetailPage } from './pages/TicketDetailPage'
import { TicketListPage } from './pages/TicketListPage'
import { matchPath, RouterProvider, useRouter } from './router'

function Screens() {
  const { state } = useAuth()
  const { path, navigate } = useRouter()

  useEffect(() => {
    if (state.status === 'signedOut' && path !== '/login') {
      navigate('/login', { replace: true })
    }
    if (state.status === 'signedIn' && (path === '/login' || path === '/')) {
      navigate('/tickets', { replace: true })
    }
  }, [state.status, path, navigate])

  if (state.status === 'checking') {
    return <div className="app-checking" aria-busy="true" />
  }
  if (state.status === 'signedOut') {
    return <LoginPage />
  }

  const detail = matchPath('/tickets/:id', path)
  return (
    <AppShell>
      {detail && /^\d+$/.test(detail.id) ? <TicketDetailPage key={detail.id} id={Number(detail.id)} /> : <TicketListPage />}
    </AppShell>
  )
}

export default function App() {
  return (
    <RouterProvider>
      <AuthProvider>
        <ToastProvider>
          <Screens />
        </ToastProvider>
      </AuthProvider>
    </RouterProvider>
  )
}
