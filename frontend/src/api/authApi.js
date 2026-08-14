import apiClient from './apiClient'
export const authApi={login:async(payload)=>(await apiClient.post('/auth/login',payload)).data,me:async()=>(await apiClient.get('/auth/me')).data}
