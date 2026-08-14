import { Outlet } from 'react-router-dom'
import Header from './Header'
import Sidebar from './Sidebar'
export default function MainLayout(){return <div className="app-shell"><Sidebar/><div className="main-area"><Header/><main className="content"><Outlet/></main></div></div>}
