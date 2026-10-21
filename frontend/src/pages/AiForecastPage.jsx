import ForecastChart from '../components/ForecastChart'
import { useEffect, useMemo, useRef, useState } from 'react'
import { aiForecastApi } from '../api/aiForecastApi'
import { productApi } from '../api/productApi'
import { getApiErrorMessage } from '../api/apiClient'
import { useAuth } from '../auth/AuthContext'
import PageHeader from '../components/PageHeader'
import StatCard from '../components/StatCard'
import Badge from '../components/Badge'
import { EmptyState, ErrorState, LoadingState } from '../components/States'
import { formatDate, formatNumber } from '../utils/formatters'

export default function AiForecastPage() {
  const { can } = useAuth()
  const [products, setProducts] = useState([])
  const [productId, setProductId] = useState('')
  const [forecast, setForecast] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState('')
  const forecasting = busy === 'forecast',
    refreshing = busy === 'risk',
    importing = busy === 'import'
  const sequence = useRef(0)
  const selection = useRef(productId)
  selection.current = productId
  useEffect(
    () => () => {
      sequence.current++
    },
    [],
  )
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [m5ItemId, setM5ItemId] = useState('FOODS_3_001')
  const [m5StoreId, setM5StoreId] = useState('CA_1')

  const canWrite = can('runIntelligence')
  const selectedProduct = useMemo(
    () => products.find((product) => product.id === productId),
    [products, productId],
  )

  useEffect(() => {
    const loadProducts = async () => {
      setLoading(true)
      setError('')
      try {
        const data = await productApi.getAll()
        setProducts(data)
        if (data.length > 0) setProductId(data[0].id)
      } catch (e) {
        setError(getApiErrorMessage(e, 'Không thể tải danh sách sản phẩm.'))
      } finally {
        setLoading(false)
      }
    }
    loadProducts()
  }, [])

  const run = async (kind) => {
    if (!productId || busy) return
    const key = productId,
      request = ++sequence.current
    const isCurrent = () =>
      sequence.current === request && selection.current === key
    setBusy(kind)
    setError('')
    setNotice('')
    setForecast(null)
    try {
      let result,
        message = ''
      if (kind === 'import') {
        if (!m5ItemId.trim()) return
        const imported = await aiForecastApi.importM5(
          key,
          m5ItemId.trim(),
          m5StoreId.trim() || 'CA_1',
        )
        if (!isCurrent()) return
        message = `Đã nhập ${imported.historyRows} ngày lịch sử cho ${imported.productCode}.`
      }
      result =
        kind === 'risk'
          ? await aiForecastApi.refreshRisk(key)
          : await aiForecastApi.getForecast(key)
      if (isCurrent()) {
        setForecast(result)
        setNotice(
          message ||
            (kind === 'risk' ? 'Đã cập nhật đánh giá rủi ro thiếu hàng.' : ''),
        )
      }
    } catch (e) {
      if (isCurrent())
        setError(getApiErrorMessage(e, 'Không thể lấy dự báo nhu cầu.'))
    } finally {
      if (isCurrent()) setBusy('')
    }
  }
  const loadForecast = () => run('forecast')
  const refreshRisk = () => run('risk')
  const importM5 = () => run('import')

  if (loading)
    return <LoadingState text="Đang tải sản phẩm cho AI Forecast..." />
  if (error && products.length === 0) return <ErrorState message={error} />

  return (
    <>
      <PageHeader
        title="Dự báo nhu cầu"
        description="Dự báo nhu cầu từ M5 thật và đánh giá nguy cơ thiếu hàng trên tồn kho doanh nghiệp."
        actions={
          <div className="button-row">
            <button
              className="btn btn-secondary"
              onClick={loadForecast}
              disabled={!productId || !!busy}
            >
              {forecasting ? 'Đang dự báo...' : 'Chạy dự báo'}
            </button>
            {canWrite && (
              <button
                className="btn btn-primary"
                onClick={refreshRisk}
                disabled={!productId || !!busy}
              >
                {refreshing ? 'Đang đánh giá...' : 'Cập nhật rủi ro'}
              </button>
            )}
          </div>
        }
      />

      {error && <div className="alert alert-danger">{error}</div>}
      {notice && <div className="alert ai-notice">{notice}</div>}

      <section className="panel ai-control-panel">
        <div className="panel-heading">
          <div>
            <h2>Chọn sản phẩm</h2>
            <p>
              Chọn sản phẩm để dự báo. M5 là dữ liệu nhu cầu tham chiếu, chưa
              phải lịch sử bán hàng thực tế của doanh nghiệp.
            </p>
          </div>
        </div>
        <div className="ai-control-grid">
          <label className="form-field">
            <span className="form-label">Sản phẩm</span>
            <select
              disabled={busy === 'risk' || busy === 'import'}
              value={productId}
              onChange={(e) => {
                sequence.current++
                selection.current = e.target.value
                setBusy('')
                setProductId(e.target.value)
                setForecast(null)
                setError('')
                setNotice('')
              }}
            >
              {products.map((product) => (
                <option key={product.id} value={product.id}>
                  {product.productCode} — {product.name}
                </option>
              ))}
            </select>
          </label>
          {canWrite && (
            <>
              <label className="form-field">
                <span className="form-label">M5 item ID</span>
                <input
                  value={m5ItemId}
                  onChange={(e) => setM5ItemId(e.target.value)}
                />
              </label>
              <label className="form-field">
                <span className="form-label">M5 store ID</span>
                <input
                  value={m5StoreId}
                  onChange={(e) => setM5StoreId(e.target.value)}
                />
              </label>
              <div className="form-field ai-control-action">
                <span className="form-label">Demand history</span>
                <button
                  className="btn btn-secondary"
                  onClick={importM5}
                  disabled={!productId || !!busy || !m5ItemId.trim()}
                >
                  {importing ? 'Đang import...' : 'Nhập lịch sử M5'}
                </button>
              </div>
            </>
          )}
        </div>
        {selectedProduct && (
          <div className="ai-context-line">
            <strong>{selectedProduct.productCode}</strong>
            <span>{selectedProduct.name}</span>
          </div>
        )}
      </section>

      {!forecast && busy ? (
        <LoadingState text="Đang xử lý dự báo…" />
      ) : !forecast ? (
        <div style={{ marginTop: 20 }}>
          <EmptyState
            title="Chưa có kết quả dự báo"
            description="Chọn sản phẩm rồi bấm “Chạy dự báo”. Nếu chưa có lịch sử nhu cầu, hãy nhập dữ liệu tham chiếu M5 trước."
          />
        </div>
      ) : (
        <>
          <section className="stats-grid ai-stats" style={{ marginTop: 20 }}>
            <StatCard
              label="Tồn kho hiện tại"
              value={formatNumber(forecast.currentInventory)}
            />
            <StatCard
              label="Ngưỡng tồn kho thấp"
              value={formatNumber(forecast.lowStockThreshold)}
              tone="warning"
            />
            <StatCard
              label="Rủi ro thiếu hàng"
              value={forecast.stockoutRisk}
              tone={
                forecast.stockoutRisk === 'CRITICAL' ||
                forecast.stockoutRisk === 'HIGH'
                  ? 'danger'
                  : 'warning'
              }
            />
            <StatCard
              label="Ngày dự kiến hết hàng"
              value={
                forecast.projectedStockoutDate
                  ? formatDate(forecast.projectedStockoutDate)
                  : 'Không có'
              }
            />
          </section>

          <section className="panel" style={{ marginBottom: 22 }}>
            <div className="panel-heading">
              <div>
                <span className="section-eyebrow">NHÌN VỀ PHÍA TRƯỚC</span>
                <h2>Diễn biến tồn kho dự kiến</h2>
                <p>
                  {forecast.dailyProjection?.length || 0} ngày dự báo · Đường
                  tham chiếu là ngưỡng cảnh báo tồn kho.
                </p>
              </div>
            </div>
            <ForecastChart
              rows={forecast.dailyProjection || []}
              threshold={forecast.lowStockThreshold}
            />
          </section>
          <section className="dashboard-grid ai-dashboard-grid">
            <article className="panel">
              <div className="panel-heading">
                <div>
                  <h2>Dự báo theo kỳ</h2>
                  <p>Nhu cầu, nguồn hàng sẽ về và tồn kho dự kiến.</p>
                </div>
                <Badge value={forecast.stockoutRisk} />
              </div>
              <div className="table-wrap">
                <table className="ai-horizon-table">
                  <thead>
                    <tr>
                      <th>Kỳ dự báo</th>
                      <th>Nhu cầu dự báo</th>
                      <th>Hàng sẽ về</th>
                      <th>Tồn kho dự kiến</th>
                    </tr>
                  </thead>
                  <tbody>
                    {[7, 14, 28].map((days) => (
                      <tr key={days}>
                        <td>
                          <strong>{days} ngày</strong>
                        </td>
                        <td>
                          {formatNumber(
                            forecast.forecastDemand?.[`next${days}Days`],
                          )}
                        </td>
                        <td>
                          {formatNumber(
                            forecast.incomingSupply?.[`next${days}Days`],
                          )}
                        </td>
                        <td
                          className={
                            Number(
                              forecast.projectedInventory?.[`next${days}Days`],
                            ) <= 0
                              ? 'text-danger strong'
                              : 'strong'
                          }
                        >
                          {formatNumber(
                            forecast.projectedInventory?.[`next${days}Days`],
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </article>

            <article className="panel">
              <div className="panel-heading">
                <div>
                  <h2>Thông tin mô hình</h2>
                  <p>Nguồn mô hình và lịch sử dùng cho lần dự báo này.</p>
                </div>
              </div>
              <div className="detail-grid">
                <div>
                  <small>Sản phẩm</small>
                  <strong>{forecast.productCode}</strong>
                </div>
                <div>
                  <small>Lịch sử đầu vào</small>
                  <strong>{forecast.historyDays} ngày</strong>
                </div>
                <div className="detail-span">
                  <small>Phiên bản mô hình</small>
                  <strong className="mono">{forecast.modelVersion}</strong>
                </div>
              </div>
            </article>

            <article className="panel panel--wide">
              <div className="panel-heading">
                <div>
                  <h2>Tồn kho dự kiến trong 28 ngày</h2>
                  <p>
                    Từng ngày: incoming supply được cộng trước, forecast demand
                    được trừ sau.
                  </p>
                </div>
              </div>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Ngày</th>
                      <th>Nhu cầu dự báo</th>
                      <th>Hàng sẽ về</th>
                      <th>Tồn kho dự kiến</th>
                      <th>Trạng thái</th>
                    </tr>
                  </thead>
                  <tbody>
                    {forecast.dailyProjection?.map((row) => (
                      <tr key={row.date}>
                        <td>{formatDate(row.date)}</td>
                        <td>{formatNumber(row.forecastDemand)}</td>
                        <td>{formatNumber(row.incomingSupply)}</td>
                        <td
                          className={
                            Number(row.projectedInventory) <= 0
                              ? 'text-danger strong'
                              : ''
                          }
                        >
                          {formatNumber(row.projectedInventory)}
                        </td>
                        <td>
                          <Badge
                            value={
                              Number(row.projectedInventory) <= 0
                                ? 'CRITICAL'
                                : Number(row.projectedInventory) <=
                                    Number(forecast.lowStockThreshold)
                                  ? 'MEDIUM'
                                  : 'LOW'
                            }
                          />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </article>
          </section>
        </>
      )}
    </>
  )
}
