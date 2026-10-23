import axios from 'axios'
import { authStorage } from '../auth/authStorage'
const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})
apiClient.interceptors.request.use((config) => {
  const token = authStorage.getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (
      error?.response?.status === 401 &&
      !error?.config?.url?.includes('/auth/login')
    ) {
      const currentToken = authStorage.getToken()
      // A late 401 from an old session must not erase a newer login.
      if (
        currentToken &&
        error.config?.headers?.Authorization === `Bearer ${currentToken}`
      ) {
        authStorage.clear()
        window.dispatchEvent(new Event('auth:expired'))
      }
    }
    return Promise.reject(error)
  },
)
const messages = {
  AI_UNREACHABLE: 'Dịch vụ dự báo đang tạm gián đoạn. Vui lòng thử lại sau.',
  AI_DISABLED: 'Tính năng AI hiện chưa được bật.',
  M5_DATASET_UNAVAILABLE:
    'Bộ dữ liệu M5 chưa sẵn sàng. Vui lòng liên hệ quản trị viên.',
  MODEL_UNAVAILABLE: 'Mô hình dự báo chưa sẵn sàng. Vui lòng thử lại sau.',
  TARGET_NOT_MENTIONED:
    'Tên thực thể phải xuất hiện trong nội dung tin. Hãy dùng đúng tên hoặc mã có trong bài.',
  TARGET_TRUNCATED:
    'Tên thực thể quá dài trong ngữ cảnh. Hãy rút gọn tên hoặc đoạn tin.',
  ENTITY_TOO_LONG:
    'Tên thực thể vượt giới hạn của mô hình. Hãy dùng tên công ty hoặc mã ngắn hơn.',
}
export function getApiErrorMessage(
  error,
  fallback = 'Không thể hoàn tất yêu cầu. Vui lòng thử lại.',
) {
  const status = error?.response?.status,
    data = error?.response?.data
  let message = messages[data?.code]
  if (status === 429) {
    const seconds = Number(error.response.headers?.['retry-after'])
    message = `Bạn đã thử quá nhiều lần. Vui lòng thử lại${seconds > 0 ? ` sau ${seconds} giây` : ' sau ít phút'}.`
  }
  if (!message && status === 403)
    message = 'Bạn không có quyền thực hiện thao tác này.'
  if (!message && status === 401)
    message = 'Thông tin đăng nhập không hợp lệ hoặc phiên đã hết hạn.'
  if (!message && status === 409)
    message =
      data?.message || 'Dữ liệu vừa thay đổi. Hãy tải lại trước khi tiếp tục.'
  if (!message && [502, 503, 504].includes(status))
    message = 'Dịch vụ đang tạm gián đoạn. Vui lòng thử lại sau.'
  if (!message && error?.code === 'ECONNABORTED')
    message = 'Yêu cầu quá thời gian chờ. Vui lòng thử lại.'
  if (!message && !error?.response)
    message =
      'Không thể kết nối hệ thống. Hãy kiểm tra kết nối mạng và thử lại.'
  message ||= (typeof data === 'string' ? data : data?.message) || fallback
  return data?.requestId ? `${message} (Mã hỗ trợ: ${data.requestId})` : message
}
export default apiClient
