import { createContext,useContext,useEffect,useMemo,useState } from 'react'
import { authApi } from '../api/authApi'
import { authStorage } from './authStorage'
const AuthContext=createContext(null)
export function AuthProvider({children}){const [user,setUser]=useState(null);const [loading,setLoading]=useState(true);useEffect(()=>{const token=authStorage.getToken();if(!token){setLoading(false);return}authApi.me().then(setUser).catch(()=>authStorage.clear()).finally(()=>setLoading(false))},[]);const value=useMemo(()=>({user,loading,login:async(credentials)=>{const data=await authApi.login(credentials);authStorage.setToken(data.accessToken);setUser(data.user);return data.user},logout:()=>{authStorage.clear();setUser(null)},hasRole:(...roles)=>roles.some(r=>user?.roles?.includes(r))}),[user,loading]);return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>}
export function useAuth(){return useContext(AuthContext)}
