import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import { authApi } from '../api/authApi'
import { getApiErrorMessage } from '../api/apiClient'
import { authStorage } from './authStorage'
import { can } from './permissions'
const AuthContext = createContext(null)
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)
  const [sessionError, setSessionError] = useState('')
  const sequence = useRef(0)
  const restoreSession = useCallback(async () => {
    const request = ++sequence.current
    setLoading(true)
    setSessionError('')
    try {
      if (authStorage.getToken()) {
        const currentUser = await authApi.me()
        if (request === sequence.current) setUser(currentUser)
      }
    } catch (error) {
      if (request !== sequence.current) return
      if (error?.response?.status === 401) {
        authStorage.clear()
        setUser(null)
      } else setSessionError(getApiErrorMessage(error))
    } finally {
      if (request === sequence.current) setLoading(false)
    }
  }, [])
  useEffect(() => {
    restoreSession()
    const expire = () => {
      sequence.current++
      setUser(null)
      setSessionError('')
      setLoading(false)
    }
    window.addEventListener('auth:expired', expire)
    return () => {
      sequence.current++
      window.removeEventListener('auth:expired', expire)
    }
  }, [restoreSession])
  const value = useMemo(
    () => ({
      user,
      loading,
      sessionError,
      retrySession: restoreSession,
      login: async (credentials) => {
        const data = await authApi.login(credentials)
        sequence.current++
        authStorage.setToken(data.accessToken)
        setSessionError('')
        setUser(data.user)
        setLoading(false)
        return data.user
      },
      logout: () => {
        sequence.current++
        authStorage.clear()
        setUser(null)
        setLoading(false)
        setSessionError('')
      },
      hasRole: (...roles) => roles.some((role) => user?.roles?.includes(role)),
      can: (permission) => can(user, permission),
    }),
    [user, loading, sessionError, restoreSession],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
export const useAuth = () => useContext(AuthContext)
