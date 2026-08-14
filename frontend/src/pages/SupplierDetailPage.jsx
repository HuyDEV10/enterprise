import { useEffect,useState } from 'react'
import { Link,useParams } from 'react-router-dom'
import { supplierApi } from '../api/supplierApi'
import { getApiErrorMessage } from '../api/apiClient'
import PageHeader from '../components/PageHeader'
import Badge from '../components/Badge'
import { ErrorState,LoadingState } from '../components/States'
import { formatDateTime } from '../utils/formatters'
export default function SupplierDetailPage(){const{id}=useParams();const[item,setItem]=useState(null),[error,setError]=useState('');const load=()=>supplierApi.getById(id).then(setItem).catch(e=>setError(getApiErrorMessage(e)));useEffect(()=>{load()},[id]);if(error)return <ErrorState message={error} onRetry={load}/>;if(!item)return <LoadingState/>;return <><PageHeader title={item.name} description={`Nhà cung cấp ${item.supplierCode}`} actions={<Link className="btn btn-primary" to={`/suppliers/${id}/edit`}>Chỉnh sửa</Link>}/><div className="detail-grid panel"><div><small>Mã nhà cung cấp</small><strong>{item.supplierCode}</strong></div><div><small>Trạng thái</small><Badge value={item.status}/></div><div><small>Email</small><strong>{item.email||'—'}</strong></div><div><small>Điện thoại</small><strong>{item.phone||'—'}</strong></div><div><small>Quốc gia</small><strong>{item.country||'—'}</strong></div><div><small>Khu vực</small><strong>{item.region||'—'}</strong></div><div><small>Mức rủi ro</small><Badge value={item.riskLevel}/></div><div><small>Cập nhật</small><strong>{formatDateTime(item.updatedAt)}</strong></div><div className="detail-span"><small>Địa chỉ</small><strong>{item.address||'—'}</strong></div><div className="detail-span"><small>Ghi chú</small><strong>{item.notes||'—'}</strong></div></div></>}
