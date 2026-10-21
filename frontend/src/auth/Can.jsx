import { useAuth } from './AuthContext'
export default function Can({ permission, children, fallback = null }) {
  const { can } = useAuth()
  return can(permission) ? children : fallback
}
