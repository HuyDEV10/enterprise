import Can from '../auth/Can'
import StatusControl from '../components/StatusControl'
import { shipmentTransitions } from '../utils/workflows'
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { shipmentApi } from '../api/shipmentApi'
import { getApiErrorMessage } from '../api/apiClient'
import PageHeader from '../components/PageHeader'
import Badge from '../components/Badge'
import { ErrorState, LoadingState } from '../components/States'
import { formatDate } from '../utils/formatters'
export default function ShipmentDetailPage() {
  const { id } = useParams()
  const [item, setItem] = useState(null),
    [error, setError] = useState('')
  const load = async () => {
    try {
      setItem(await shipmentApi.getById(id))
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
        title={item.shipmentCode}
        description={`Vận chuyển cho đơn ${item.orderCode}`}
        actions={
          <Can permission="writeOperations">
            {!['ARRIVED', 'CANCELLED'].includes(item.status) && (
              <Link className="btn btn-primary" to={`/shipments/${id}/edit`}>
                Chỉnh sửa
              </Link>
            )}
          </Can>
        }
      />
      <div className="panel detail-grid">
        <div>
          <small>Đơn mua hàng</small>
          <Link
            className="table-link"
            to={`/purchase-orders/${item.purchaseOrderId}`}
          >
            {item.orderCode}
          </Link>
        </div>
        <div>
          <small>Trạng thái</small>
          <Badge value={item.status} />
        </div>
        <div>
          <small>Đơn vị vận chuyển</small>
          <strong>{item.carrierName || '—'}</strong>
        </div>
        <div>
          <small>Vị trí hiện tại</small>
          <strong>{item.currentLocation || '—'}</strong>
        </div>
        <div>
          <small>Khởi hành</small>
          <strong>{formatDate(item.departureDate)}</strong>
        </div>
        <div>
          <small>Đến dự kiến</small>
          <strong>{formatDate(item.expectedArrivalDate)}</strong>
        </div>
        <div>
          <small>Đến thực tế</small>
          <strong>{formatDate(item.actualArrivalDate)}</strong>
        </div>
        <Can permission="writeOperations">
          <div className="detail-span">
            <StatusControl
              value={item.status}
              transitions={shipmentTransitions}
              onChange={async (status) =>
                setItem(await shipmentApi.updateStatus(id, status))
              }
            />
          </div>
        </Can>
        <div className="detail-span">
          <small>Ghi chú</small>
          <strong>{item.notes || '—'}</strong>
        </div>
      </div>
    </>
  )
}
