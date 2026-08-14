import { useEffect,useState } from 'react'
import { Link } from 'react-router-dom'
import { purchaseOrderApi } from '../api/purchaseOrderApi'
import { getApiErrorMessage } from '../api/apiClient'
import PageHeader from '../components/PageHeader'
import Badge from '../components/Badge'
import { EmptyState,ErrorState,LoadingState } from '../components/States'
import { formatCurrency,formatDate } from '../utils/formatters'
export default function PurchaseOrderListPage(){const[rows,setRows]=useState([]),[loading,setLoading]=useState(true),[error,setError]=useState('');const load=async()=>{setLoading(true);try{setRows(await purchaseOrderApi.getAll());setError('')}catch(e){setError(getApiErrorMessage(e,'Không thể tải đơn mua hàng.'))}finally{setLoading(false)}};useEffect(()=>{load()},[]);return <><PageHeader title="Đơn mua hàng" description="Theo dõi đơn đặt nhà cung cấp và tiến độ giao hàng." actions={<Link className="btn btn-primary" to="/purchase-orders/new">+ Tạo đơn mua hàng</Link>}/>{loading?<LoadingState/>:error?<ErrorState message={error} onRetry={load}/>:rows.length===0?<EmptyState title="Chưa có đơn mua hàng nào."/>:<div className="panel table-wrap"><table><thead><tr><th>Mã đơn</th><th>Nhà cung cấp</th><th>Ngày đặt</th><th>Dự kiến giao</th><th>Tổng tiền</th><th>Trạng thái</th><th></th></tr></thead><tbody>{rows.map(r=><tr key={r.id}><td><Link className="table-link mono" to={`/purchase-orders/${r.id}`}>{r.orderCode}</Link></td><td>{r.supplierName}</td><td>{formatDate(r.orderDate)}</td><td>{formatDate(r.expectedDeliveryDate)}</td><td>{formatCurrency(r.totalAmount)}</td><td><Badge value={r.status}/></td><td className="row-actions"><Link to={`/purchase-orders/${r.id}`}>Xem</Link><Link to={`/purchase-orders/${r.id}/edit`}>Sửa</Link></td></tr>)}</tbody></table></div>}</>}
