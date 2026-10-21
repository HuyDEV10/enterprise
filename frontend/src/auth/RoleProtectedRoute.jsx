import { Outlet } from 'react-router-dom'
import { useAuth } from './AuthContext'
import AccessDeniedPage from '../pages/AccessDeniedPage'
export default function RoleProtectedRoute({ roles, permission, children }) {
  const { hasRole, can } = useAuth()
  return (permission ? can(permission) : hasRole(...roles)) ? (
    children || <Outlet />
  ) : (
    <AccessDeniedPage />
  )
}
