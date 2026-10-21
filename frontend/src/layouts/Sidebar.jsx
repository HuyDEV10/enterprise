import { useEffect, useRef } from 'react'
import { NavLink } from 'react-router-dom'
import {
  Activity,
  AlertTriangle,
  ArrowUpRight,
  Bell,
  Boxes,
  BrainCircuit,
  Building2,
  Globe2,
  LayoutDashboard,
  Package,
  ShieldCheck,
  ShoppingCart,
  Truck,
  Warehouse,
  Users,
  X,
} from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
const groups = [
  {
    section: 'KHÔNG GIAN LÀM VIỆC',
    items: [
      ['/dashboard', 'Tổng quan', LayoutDashboard],
      ['/risk-monitoring', 'Giám sát rủi ro', Activity],
      ['/ai-forecast', 'Dự báo nhu cầu', BrainCircuit],
      ['/external-events', 'Tín hiệu thị trường', Globe2],
    ],
  },
  {
    section: 'CHUỖI CUNG ỨNG',
    items: [
      ['/suppliers', 'Nhà cung cấp', Building2],
      ['/products', 'Sản phẩm', Package],
      ['/warehouses', 'Kho hàng', Warehouse],
      ['/inventory', 'Tồn kho', Boxes],
      ['/purchase-orders', 'Đơn mua hàng', ShoppingCart],
      ['/shipments', 'Vận chuyển', Truck],
    ],
  },
  {
    section: 'QUẢN TRỊ',
    items: [
      ['/risks', 'Sự kiện rủi ro', AlertTriangle, 'readRisks'],
      ['/alerts', 'Cảnh báo', Bell, 'readAlerts'],
      ['/company', 'Doanh nghiệp', Building2, 'readCompany'],
      ['/users', 'Người dùng', Users, 'manageUsers'],
    ],
  },
]
export default function Sidebar({ open, onClose }) {
  const { can } = useAuth()
  const ref = useRef(null)
  useEffect(() => {
    if (!open) return
    ref.current?.querySelector('button')?.focus()
    const trap = (e) => {
      if (e.key !== 'Tab') return
      const items = ref.current.querySelectorAll('a, button')
      const first = items[0],
        last = items[items.length - 1]
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault()
        last.focus()
      }
      if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault()
        first.focus()
      }
    }
    const el = ref.current
    el.addEventListener('keydown', trap)
    return () => el.removeEventListener('keydown', trap)
  }, [open])
  return (
    <aside
      ref={ref}
      id="primary-sidebar"
      className="sidebar"
      aria-label="Thanh điều hướng"
    >
      <div className="brand">
        <span className="brand-mark">
          <ShieldCheck size={25} strokeWidth={1.8} />
        </span>
        <div>
          <strong>
            enterprise<span>.</span>
          </strong>
          <small>RISK INTELLIGENCE</small>
        </div>
        <button
          className="icon-button sidebar-close"
          aria-label="Đóng menu"
          onClick={onClose}
        >
          <X size={20} />
        </button>
      </div>
      <nav aria-label="Điều hướng chính">
        {groups.map((group) => {
          const items = group.items.filter((item) => !item[3] || can(item[3]))
          return items.length ? (
            <div className="nav-group" key={group.section}>
              <small>{group.section}</small>
              {items.map(([to, label, Icon]) => (
                <NavLink
                  key={to}
                  to={to}
                  className={({ isActive }) =>
                    `nav-link ${isActive ? 'active' : ''}`
                  }
                >
                  <Icon size={18} strokeWidth={1.7} />
                  <span>{label}</span>
                </NavLink>
              ))}
            </div>
          ) : null
        })}
      </nav>
      <div className="sidebar-bottom">
        <div className="sidebar-insight">
          <span className="insight-icon">
            <BrainCircuit size={22} />
          </span>
          <strong>Chủ động trước rủi ro</strong>
          <p>Theo dõi nhu cầu, chuẩn bị cho bước tiếp theo.</p>
          <NavLink to="/ai-forecast">
            Khám phá dự báo <ArrowUpRight size={15} />
          </NavLink>
        </div>
        <small>
          <span className="status-dot" /> Không gian doanh nghiệp
        </small>
      </div>
    </aside>
  )
}
