import Can from '../auth/Can'
import StatusControl from '../components/StatusControl'
import { orderTransitions } from '../utils/workflows'
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { purchaseOrderApi } from '../api/purchaseOrderApi'
import { getApiErrorMessage } from '../api/apiClient'
import PageHeader from '../components/PageHeader'
import Badge from '../components/Badge'
import { ErrorState, LoadingState } from '../components/States'
import { formatCurrency, formatDate } from '../utils/formatters'
export default function PurchaseOrderDetailPage() {
  const { id } = useParams()
  const [item, setItem] = useState(null),
    [error, setError] = useState('')
  const load = async () => {
    try {
      setItem(await purchaseOrderApi.getById(id))
      setError('')
    } catch (e) {
      setError(getApiErrorMessage(e))
    }
  }
  useEffect(() => {
    load()
  }, [id])
  if (error) return <ErrorState message={error} onRetry={load} />
  if (!item) return <LoadingState />
  return (
    <>
      <PageHeader
        title={item.orderCode}
        description={`Đơn mua hàng · ${item.supplierName}`}
        actions={
          <Can permission="writeOperations">
            {item.status === 'DRAFT' && (
              <Link
                className="btn btn-primary"
                to={`/purchase-orders/${id}/edit`}
              >
                Chỉnh sửa
              </Link>
            )}
          </Can>
        }
      />
      <div className="panel detail-grid">
        <div>
          <small>Nhà cung cấp</small>
          <strong>{item.supplierName}</strong>
        </div>
        <div>
          <small>Trạng thái</small>
          <Badge value={item.status} />
        </div>
        <div>
          <small>Ngày đặt</small>
          <strong>{formatDate(item.orderDate)}</strong>
        </div>
        <div>
          <small>Giao dự kiến</small>
          <strong>{formatDate(item.expectedDeliveryDate)}</strong>
        </div>
        <div>
          <small>Tổng tiền</small>
          <strong>{formatCurrency(item.totalAmount)}</strong>
        </div>
        <Can permission="writeOperations">
          <div className="detail-span">
            <StatusControl
              value={item.status}
              transitions={orderTransitions}
              onChange={async (status) =>
                setItem(await purchaseOrderApi.updateStatus(id, status))
              }
            />
          </div>
        </Can>
        <div className="detail-span">
          <small>Ghi chú</small>
          <strong>{item.notes || '—'}</strong>
        </div>
      </div>
      <div className="panel table-wrap">
        <table>
          <thead>
            <tr>
              <th>Sản phẩm</th>
              <th>Số lượng</th>
              <th>Đơn giá</th>
              <th>Thành tiền</th>
            </tr>
          </thead>
          <tbody>
            {item.items.map((r) => (
              <tr key={r.id}>
                <td>{r.productName}</td>
                <td>{r.quantity}</td>
                <td>{formatCurrency(r.unitPrice)}</td>
                <td className="strong">{formatCurrency(r.lineTotal)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <td colSpan="3">TOTAL</td>
              <td>{formatCurrency(item.totalAmount)}</td>
            </tr>
          </tfoot>
        </table>
      </div>
    </>
  )
}
