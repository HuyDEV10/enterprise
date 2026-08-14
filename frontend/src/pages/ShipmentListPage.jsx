import { useEffect,useState } from 'react'
import { Link } from 'react-router-dom'
import { shipmentApi } from '../api/shipmentApi'
import { getApiErrorMessage } from '../api/apiClient'
import PageHeader from '../components/PageHeader'
import Badge from '../components/Badge'
import { EmptyState,ErrorState,LoadingState } from '../components/States'
import { formatDate } from '../utils/formatters'
export default function ShipmentListPage(){const[rows,setRows]=useState([]),[loading,setLoading]=useState(true),[error,setError]=useState('');const load=async()=>{setLoading(true);try{setRows(await shipmentApi.getAll());setError('')}catch(e){setError(getApiErrorMessage(e,'Không thể tải vận chuyển.'))}finally{setLoading(false)}};useEffect(()=>{load()},[]);return <><PageHeader title="Vận chuyển" description="Theo dõi lô hàng gắn với đơn mua hàng." actions={<Link className="btn btn-primary" to="/shipments/new">+ Tạo vận chuyển</Link>}/>{loading?<LoadingState/>:error?<ErrorState message={error} onRetry={load}/>:rows.length===0?<EmptyState title="Chưa có lô vận chuyển nào."/>:<div className="panel table-wrap"><table><thead><tr><th>Mã lô</th><th>Đơn mua</th><th>Đơn vị vận chuyển</th><th>Khởi hành</th><th>Dự kiến đến</th><th>Trạng thái</th><th>Vị trí</th><th></th></tr></thead><tbody>{rows.map(r=><tr key={r.id}><td><Link className="table-link mono" to={`/shipments/${r.id}`}>{r.shipmentCode}</Link></td><td>{r.orderCode}</td><td>{r.carrierName||'—'}</td><td>{formatDate(r.departureDate)}</td><td>{formatDate(r.expectedArrivalDate)}</td><td><Badge value={r.status}/></td><td>{r.currentLocation||'—'}</td><td className="row-actions"><Link to={`/shipments/${r.id}`}>Xem</Link><Link to={`/shipments/${r.id}/edit`}>Sửa</Link></td></tr>)}</tbody></table></div>}</>}
