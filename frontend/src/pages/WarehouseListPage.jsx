import { useEffect,useState } from 'react'
import { Link } from 'react-router-dom'
import { warehouseApi } from '../api/warehouseApi'
import { getApiErrorMessage } from '../api/apiClient'
import PageHeader from '../components/PageHeader'
import Badge from '../components/Badge'
import { EmptyState,ErrorState,LoadingState } from '../components/States'
export default function WarehouseListPage(){const[rows,setRows]=useState([]),[loading,setLoading]=useState(true),[error,setError]=useState('');const load=async()=>{setLoading(true);try{setRows(await warehouseApi.getAll());setError('')}catch(e){setError(getApiErrorMessage(e,'Không thể tải danh sách kho.'))}finally{setLoading(false)}};useEffect(()=>{load()},[]);return <><PageHeader title="Kho hàng" description="Quản lý địa điểm lưu trữ hàng hóa." actions={<Link className="btn btn-primary" to="/warehouses/new">+ Thêm kho</Link>}/>{loading?<LoadingState/>:error?<ErrorState message={error} onRetry={load}/>:rows.length===0?<EmptyState title="Chưa có kho hàng nào."/>:<div className="panel table-wrap"><table><thead><tr><th>Mã kho</th><th>Tên kho</th><th>Địa chỉ</th><th>Trạng thái</th><th></th></tr></thead><tbody>{rows.map(r=><tr key={r.id}><td className="mono">{r.warehouseCode}</td><td>{r.name}</td><td>{r.address||'—'}</td><td><Badge value={r.status}/></td><td className="row-actions"><Link to={`/warehouses/${r.id}/edit`}>Sửa</Link></td></tr>)}</tbody></table></div>}</>}
