import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import {
  ArrowRight,
  Eye,
  EyeOff,
  ShieldCheck,
  Activity,
  Check,
  LockKeyhole,
} from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { getApiErrorMessage } from '../api/apiClient'
export default function LoginPage() {
  const { user, login } = useAuth(),
    navigate = useNavigate(),
    location = useLocation()
  const [username, setUsername] = useState(''),
    [password, setPassword] = useState('')
  const [error, setError] = useState(''),
    [submitting, setSubmitting] = useState(false),
    [visible, setVisible] = useState(false)
  const destination = location.state?.from
  const target =
    destination?.pathname?.startsWith('/') &&
    !destination.pathname.startsWith('//') &&
    destination.pathname !== '/login'
      ? destination.pathname + (destination.search || '')
      : '/dashboard'
  if (user) return <Navigate to={target} replace />
  async function submit(e) {
    e.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      await login({ username: username.trim(), password })
      navigate(target, { replace: true })
    } catch (err) {
      setError(getApiErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }
  return (
    <main className="login-shell">
      <section className="login-story">
        <div className="brand">
          <span className="brand-mark">
            <ShieldCheck size={26} />
          </span>
          <div>
            <strong>
              enterprise<span>.</span>
            </strong>
            <small>RISK INTELLIGENCE</small>
          </div>
        </div>
        <div className="login-story-content">
          <span className="eyebrow">
            <span className="status-dot" /> KẾT NỐI DỮ LIỆU. CHỦ ĐỘNG TƯƠNG LAI.
          </span>
          <h1>
            Nhìn rõ hôm nay.
            <br />
            <em>Vững bước ngày mai.</em>
          </h1>
          <p>
            Một không gian để kết nối chuỗi cung ứng, nhận diện rủi ro và đưa ra
            quyết định tốt hơn.
          </p>
          <div className="login-visual" aria-hidden="true">
            <div className="visual-caption">
              <Activity size={18} />
              <span>Chuỗi cung ứng trong tầm tay</span>
              <span className="visual-live">CONNECTED</span>
            </div>
            <div className="network">
              <span className="network-node n1">
                <BuildingIcon />
                Nguồn cung
              </span>
              <span className="network-node n2">
                <ShieldCheck size={32} />
                Enterprise
              </span>
              <span className="network-node n3">
                <Check size={24} />
                Vận hành
              </span>
            </div>
            <div className="visual-bottom">
              <span>01 · Theo dõi</span>
              <span>02 · Phân tích</span>
              <span>03 · Hành động</span>
            </div>
          </div>
        </div>
        <small className="login-copyright">
          Enterprise Risk System · Được thiết kế cho sự chủ động.
        </small>
      </section>
      <section className="login-form-side">
        <div className="login-form-wrap">
          <span className="login-lock">
            <LockKeyhole size={24} />
          </span>
          <span className="eyebrow">CHÀO MỪNG TRỞ LẠI</span>
          <h2>
            Đăng nhập vào
            <br />
            không gian của bạn
          </h2>
          <p>Sẵn sàng cho một ngày làm việc hiệu quả.</p>
          <form onSubmit={submit}>
            {error && (
              <div className="alert alert-danger" role="alert">
                {error}
              </div>
            )}
            <label className="form-field">
              <span className="form-label">Tên đăng nhập</span>
              <input
                autoComplete="username"
                placeholder="Nhập tên đăng nhập"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
                maxLength={100}
                autoFocus
              />
            </label>
            <label className="form-field">
              <span className="form-label">Mật khẩu</span>
              <span className="password-input">
                <input
                  autoComplete="current-password"
                  placeholder="Nhập mật khẩu của bạn"
                  type={visible ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  maxLength={1000}
                />
                <button
                  className="icon-button"
                  type="button"
                  aria-label={visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                  aria-pressed={visible}
                  onClick={() => setVisible(!visible)}
                >
                  {visible ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </span>
            </label>
            <button
              className="btn btn-primary login-submit"
              disabled={submitting}
            >
              {submitting ? 'Đang đăng nhập…' : 'Đăng nhập'}
              <ArrowRight size={18} />
            </button>
          </form>
          <p className="login-help">
            Cần cấp tài khoản hoặc đặt lại mật khẩu?
            <br />
            Liên hệ quản trị viên doanh nghiệp của bạn.
          </p>
          <div className="login-security">
            <ShieldCheck size={15} /> Truy cập theo quyền được cấp
          </div>
        </div>
      </section>
    </main>
  )
}
function BuildingIcon() {
  return <Activity size={24} />
}
