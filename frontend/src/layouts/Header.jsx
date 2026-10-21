import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ChevronRight, LogOut, Menu, ShieldCheck } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
const labels = {
  dashboard: 'Tổng quan',
  suppliers: 'Nhà cung cấp',
  products: 'Sản phẩm',
  inventory: 'Tồn kho',
  warehouses: 'Kho hàng',
  'purchase-orders': 'Đơn mua hàng',
  shipments: 'Vận chuyển',
  risks: 'Sự kiện rủi ro',
  alerts: 'Cảnh báo',
  company: 'Doanh nghiệp',
  users: 'Người dùng',
  'ai-forecast': 'Dự báo nhu cầu',
  'external-events': 'Tín hiệu thị trường',
  'risk-monitoring': 'Giám sát rủi ro',
  'product-categories': 'Danh mục sản phẩm',
}
const roles = {
  ADMIN: 'Quản trị viên',
  MANAGER: 'Quản lý',
  ANALYST: 'Chuyên viên phân tích',
  STAFF: 'Nhân viên',
}
export default function Header({ onMenu, toggleRef }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate(),
    { pathname } = useLocation()
  const initials = (user?.fullName || user?.username || 'U')
    .split(' ')
    .map((x) => x[0])
    .join('')
    .slice(0, 2)
    .toUpperCase()
  return (
    <header className="top-header">
      <div className="header-breadcrumb">
        <button
          ref={toggleRef}
          className="icon-button menu-toggle"
          aria-label="Mở menu"
          aria-controls="primary-sidebar"
          onClick={onMenu}
        >
          <Menu size={21} />
        </button>
        <Link to="/dashboard" className="workspace-label">
          Không gian làm việc
        </Link>
        <ChevronRight size={14} />
        <strong>{labels[pathname.split('/')[1]] || 'Chi tiết'}</strong>
      </div>
      <div className="header-user">
        <span className="header-secure">
          <ShieldCheck size={15} /> Phiên bảo mật
        </span>
        <span className="avatar">{initials}</span>
        <div>
          <strong>{user?.fullName || user?.username}</strong>
          <small>{user?.roles?.map((r) => roles[r] || r).join(' · ')}</small>
        </div>
        <button
          className="icon-button"
          title="Đăng xuất"
          aria-label="Đăng xuất"
          onClick={() => {
            logout()
            navigate('/login', { replace: true })
          }}
        >
          <LogOut size={18} />
        </button>
      </div>
    </header>
  )
}
