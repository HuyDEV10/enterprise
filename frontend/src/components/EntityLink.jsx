import { Link } from 'react-router-dom'
export default function EntityLink({ id, to, children }) { if (!id) return <span>—</span>; return <Link className="table-link" to={to}>{children || id}</Link> }
