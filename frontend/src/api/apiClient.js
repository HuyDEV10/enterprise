import axios from 'axios'
import { authStorage } from '../auth/authStorage'

const apiClient=axios.create({baseURL:import.meta.env.VITE_API_BASE_URL||'http://localhost:8080/api',timeout:15000,headers:{'Content-Type':'application/json'}})
apiClient.interceptors.request.use(config=>{const token=authStorage.getToken();if(token) config.headers.Authorization=`Bearer ${token}`;return config})
apiClient.interceptors.response.use(r=>r,error=>{if(error?.response?.status===401 && !error?.config?.url?.includes('/auth/login')){authStorage.clear();if(window.location.pathname!='/login') window.location.assign('/login')}return Promise.reject(error)})
export function getApiErrorMessage(error,fallback='Đã xảy ra lỗi khi kết nối hệ thống.'){const data=error?.response?.data;if(typeof data==='string'&&data.trim())return data;if(data?.message)return data.message;if(data?.error)return data.error;if(error?.code==='ECONNABORTED')return 'Yêu cầu quá thời gian chờ. Vui lòng thử lại.';if(!error?.response)return 'Không thể kết nối backend. Hãy kiểm tra Spring Boot đang chạy.';return fallback}
export default apiClient
