import { Link } from 'react-router-dom'
import { ShieldAlert } from 'lucide-react'
export default function AccessDeniedPage() {
  return (
    <div className="state-box access-denied">
      <ShieldAlert size={36} />
      <span className="eyebrow">GIỚI HẠN TRUY CẬP</span>
      <h1>Bạn chưa có quyền truy cập</h1>
      <p>Liên hệ quản trị viên nếu bạn cần sử dụng chức năng này.</p>
      <Link className="btn btn-primary" to="/dashboard">
        Về tổng quan
      </Link>
    </div>
  )
}
