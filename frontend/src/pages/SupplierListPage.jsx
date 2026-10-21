import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Plus } from 'lucide-react'
import { supplierApi } from '../api/supplierApi'
import { getApiErrorMessage } from '../api/apiClient'
import Can from '../auth/Can'
import PageHeader from '../components/PageHeader'
import Badge from '../components/Badge'
import Pagination from '../components/Pagination'
import usePagedList from '../hooks/usePagedList'
import { EmptyState, ErrorState, LoadingState } from '../components/States'
export default function SupplierListPage() {
  const [params, setParams] = useSearchParams(),
    riskLevel = params.get('riskLevel') || ''
  const list = usePagedList(supplierApi.getPage, riskLevel ? { riskLevel } : {})
  const [actionError, setActionError] = useState(''),
    [busy, setBusy] = useState(null)
  async function deactivate(item) {
    if (!window.confirm(`Ngừng hoạt động nhà cung cấp “${item.name}”?`)) return
    setBusy(item.id)
    setActionError('')
    try {
      await supplierApi.deactivate(item.id)
      await list.load()
    } catch (e) {
      setActionError(getApiErrorMessage(e))
    } finally {
      setBusy(null)
    }
  }
  return (
    <>
      <PageHeader
        title="Nhà cung cấp"
        description="Kết nối đối tác, theo dõi nguồn cung và chủ động quản lý rủi ro."
        actions={
          <Can permission="writeOperations">
            <Link className="btn btn-primary" to="/suppliers/new">
              <Plus size={15} />
              Thêm nhà cung cấp
            </Link>
          </Can>
        }
      />
      <div className="toolbar">
        <label>
          Mức rủi ro
          <select
            value={riskLevel}
            onChange={(e) =>
              setParams(e.target.value ? { riskLevel: e.target.value } : {})
            }
          >
            <option value="">Tất cả mức rủi ro</option>
            <option value="LOW">Thấp</option>
            <option value="MEDIUM">Trung bình</option>
            <option value="HIGH">Cao</option>
          </select>
        </label>
        <span className="toolbar-count">
          {list.totalElements} đối tác cung ứng
        </span>
      </div>
      {actionError && (
        <div className="alert alert-danger" role="alert">
          {actionError}
        </div>
      )}
      {list.loading ? (
        <LoadingState />
      ) : list.error ? (
        <ErrorState message={list.error} onRetry={list.load} />
      ) : (
        <div className="panel">
          {list.content.length === 0 ? (
            <EmptyState
              title="Chưa có nhà cung cấp phù hợp"
              description="Thử thay đổi bộ lọc hoặc thêm đối tác cung ứng đầu tiên."
              action={
                <Can permission="writeOperations">
                  <Link className="btn btn-primary" to="/suppliers/new">
                    Thêm nhà cung cấp
                  </Link>
                </Can>
              }
            />
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Mã nhà cung cấp</th>
                    <th>Đối tác</th>
                    <th>Quốc gia</th>
                    <th>Khu vực</th>
                    <th>Rủi ro</th>
                    <th>Trạng thái</th>
                    <th>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {list.content.map((item) => (
                    <tr key={item.id}>
                      <td className="mono">{item.supplierCode}</td>
                      <td>
                        <Link
                          className="table-link"
                          to={`/suppliers/${item.id}`}
                        >
                          {item.name}
                        </Link>
                      </td>
                      <td>{item.country || '—'}</td>
                      <td>{item.region || '—'}</td>
                      <td>
                        <Badge value={item.riskLevel} />
                      </td>
                      <td>
                        <Badge value={item.status} />
                      </td>
                      <td>
                        <div className="row-actions">
                          <Link to={`/suppliers/${item.id}`}>Chi tiết</Link>
                          <Can permission="writeOperations">
                            <Link to={`/suppliers/${item.id}/edit`}>Sửa</Link>
                            {item.status !== 'INACTIVE' && (
                              <button
                                className="link-button danger"
                                disabled={busy === item.id}
                                onClick={() => deactivate(item)}
                              >
                                Ngừng
                              </button>
                            )}
                          </Can>
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
