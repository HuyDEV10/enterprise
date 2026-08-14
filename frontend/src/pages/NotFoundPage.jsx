import { Link } from 'react-router-dom'
export default function NotFoundPage(){return <div className="state-box state-box--empty"><strong>404 · Không tìm thấy trang</strong><span>Đường dẫn này chưa tồn tại trong hệ thống.</span><Link className="btn btn-primary" to="/dashboard">Về Dashboard</Link></div>}
