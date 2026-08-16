import { NavLink } from 'react-router-dom'
import { Activity, AlertTriangle, Boxes, BrainCircuit, Building2, Globe2, LayoutDashboard, Package, ShoppingCart, Truck, Warehouse, Users } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'

const management = [
  { to: '/suppliers', label: 'Nhà cung cấp', icon: Building2 },
  { to: '/products', label: 'Sản phẩm', icon: Package },
  { to: '/warehouses', label: 'Kho hàng', icon: Warehouse },
  { to: '/inventory', label: 'Tồn kho', icon: Boxes },
  { to: '/purchase-orders', label: 'Đơn mua hàng', icon: ShoppingCart },
  { to: '/shipments', label: 'Vận chuyển', icon: Truck },
]

export default function Sidebar() {
  const { hasRole } = useAuth()
  const links = [
    {
      section: 'TỔNG QUAN',
      items: [
        { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
        { to: '/risk-monitoring', label: 'Giám sát rủi ro', icon: Activity },
        { to: '/ai-forecast', label: 'AI Forecast', icon: BrainCircuit },
        { to: '/external-events', label: 'External Events', icon: Globe2 },
      ],
    },
    { section: 'QUẢN LÝ', items: management },
    ...(hasRole('ADMIN', 'MANAGER', 'ANALYST') ? [{
      section: 'RỦI RO',
      items: [
        { to: '/risks', label: 'Sự kiện rủi ro', icon: AlertTriangle },
        { to: '/alerts', label: 'Cảnh báo', icon: AlertTriangle },
      ],
    }] : []),
    ...(hasRole('ADMIN', 'MANAGER', 'ANALYST') ? [{
      section: 'HỆ THỐNG',
      items: [
        { to: '/company', label: 'Doanh nghiệp', icon: Building2 },
        ...(hasRole('ADMIN') ? [{ to: '/users', label: 'Người dùng', icon: Users }] : []),
      ],
    }] : []),
  ]

  return <aside className="sidebar">
    <div className="brand"><span>ER</span><div><strong>Enterprise</strong><small>Risk System</small></div></div>
    <nav>{links.map((group) => <div className="nav-group" key={group.section}>
      <small>{group.section}</small>
      {group.items.map(({ to, label, icon: Icon }) => <NavLink key={to} to={to} className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}><Icon size={18} /><span>{label}</span></NavLink>)}
    </div>)}</nav>
  </aside>
}
