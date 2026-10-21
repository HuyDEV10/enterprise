import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'
import { ErrorState, LoadingState } from '../components/States'
export default function ProtectedRoute() {
  const { user, loading, sessionError, retrySession, logout } = useAuth()
  const location = useLocation()
  if (loading)
    return (
      <main className="session-screen">
        <LoadingState text="Đang kiểm tra phiên đăng nhập…" />
      </main>
    )
  if (sessionError)
    return (
      <main className="session-screen">
        <ErrorState message={sessionError} onRetry={retrySession} />
        <button className="link-button" onClick={logout}>
          Dùng tài khoản khác
        </button>
      </main>
    )
  if (!user) return <Navigate to="/login" replace state={{ from: location }} />
  return <Outlet />
}
