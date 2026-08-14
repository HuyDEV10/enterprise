import { Link } from 'react-router-dom'
export default function AccessDeniedPage(){return <main style={{padding:40}}><h1>403 - Không có quyền truy cập</h1><p>Tài khoản hiện tại không được phép truy cập chức năng này.</p><Link to="/dashboard">Quay về Dashboard</Link></main>}
