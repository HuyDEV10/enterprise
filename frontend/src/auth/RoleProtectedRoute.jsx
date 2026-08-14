import { Navigate,Outlet } from 'react-router-dom'
import { useAuth } from './AuthContext'
export default function RoleProtectedRoute({roles}){const {hasRole}=useAuth();return hasRole(...roles)?<Outlet/>:<Navigate to="/access-denied" replace/>}
