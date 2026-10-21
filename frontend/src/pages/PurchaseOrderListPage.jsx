import { Link } from 'react-router-dom'
import { Plus } from 'lucide-react'
import { purchaseOrderApi } from '../api/purchaseOrderApi'
import Can from '../auth/Can'
import PageHeader from '../components/PageHeader'
import Badge from '../components/Badge'
import Pagination from '../components/Pagination'
import usePagedList from '../hooks/usePagedList'
import { EmptyState, ErrorState, LoadingState } from '../components/States'
import { formatCurrency, formatDate } from '../utils/formatters'
export default function PurchaseOrderListPage() {
  const list = usePagedList(purchaseOrderApi.getPage)
  return (
    <>
      <PageHeader
        title="Đơn mua hàng"
        description="Theo dõi tiến độ đặt hàng, giá trị và lịch giao của từng đơn."
        actions={
          <Can permission="writeOperations">
            <Link className="btn btn-primary" to="/purchase-orders/new">
              <Plus size={15} />
              Tạo đơn mua hàng
            </Link>
          </Can>
        }
      />
      {list.loading ? (
        <LoadingState />
      ) : list.error ? (
        <ErrorState message={list.error} onRetry={list.load} />
      ) : (
        <div className="panel">
          {list.content.length === 0 ? (
            <EmptyState
              title="Chưa có đơn mua hàng"
              description="Bắt đầu với một đơn nháp để chuẩn bị đặt hàng."
            />
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Mã đơn</th>
                    <th>Nhà cung cấp</th>
                    <th>Ngày đặt</th>
                    <th>Dự kiến giao</th>
                    <th>Tổng tiền</th>
                    <th>Trạng thái</th>
                    <th>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {list.content.map((item) => (
                    <tr key={item.id}>
                      <td>
                        <Link
                          className="table-link mono"
                          to={`/purchase-orders/${item.id}`}
                        >
                          {item.orderCode}
                        </Link>
                      </td>
                      <td>{item.supplierName}</td>
                      <td>{formatDate(item.orderDate)}</td>
                      <td>{formatDate(item.expectedDeliveryDate)}</td>
                      <td className="strong">
                        {formatCurrency(item.totalAmount)}
                      </td>
                      <td>
                        <Badge value={item.status} />
                      </td>
                      <td>
                        <div className="row-actions">
                          <Link to={`/purchase-orders/${item.id}`}>
                            Chi tiết
                          </Link>
                          {item.status === 'DRAFT' && (
                            <Can permission="writeOperations">
                              <Link to={`/purchase-orders/${item.id}/edit`}>
                                Sửa
                              </Link>
                            </Can>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <Pagination {...list} />
        </div>
      )}
    </>
  )
}
