import { useAuth } from '../auth/AuthContext'
import { LockKeyhole } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import Header from './Header'
import Sidebar from './Sidebar'
export default function MainLayout() {
  const { can } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)
  const location = useLocation()
  const toggle = useRef(null)
  useEffect(() => {
    setMenuOpen(false)
  }, [location.pathname])
  useEffect(() => {
    if (!menuOpen) return
    const close = (event) => {
      if (event.key === 'Escape') {
        setMenuOpen(false)
        toggle.current?.focus()
      }
    }
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', close)
    return () => {
      document.body.style.overflow = previous
      window.removeEventListener('keydown', close)
    }
  }, [menuOpen])
  return (
    <div className={`app-shell ${menuOpen ? 'menu-open' : ''}`}>
      <a className="skip-link" href="#main-content">
        Đi đến nội dung
      </a>
      {menuOpen && (
        <button
          className="sidebar-backdrop"
          aria-label="Đóng menu"
          onClick={() => setMenuOpen(false)}
        />
      )}
      <Sidebar
        open={menuOpen}
        onClose={() => {
          setMenuOpen(false)
          toggle.current?.focus()
        }}
      />
      <div className="main-area" inert={menuOpen || undefined}>
        <Header toggleRef={toggle} onMenu={() => setMenuOpen(true)} />
        <main id="main-content" className="content">
          {!can('writeOperations') &&
            [
              'suppliers',
              'products',
              'product-categories',
              'warehouses',
              'inventory',
              'purchase-orders',
              'shipments',
            ].includes(location.pathname.split('/')[1]) && (
              <div className="read-only-note">
                <LockKeyhole size={13} />
                Bạn đang xem ở chế độ chỉ đọc.
              </div>
            )}
          <Outlet />
        </main>
        <footer className="app-footer">
          <span>Enterprise · Supply chain intelligence</span>
          <span>Quyết định tốt hơn, mỗi ngày.</span>
        </footer>
      </div>
    </div>
  )
}
