import { useEffect, useMemo, useState } from 'react'
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
  const { hasRole } = useAuth()
  const [products, setProducts] = useState([])
  const [productId, setProductId] = useState('')
  const [forecast, setForecast] = useState(null)
  const [loading, setLoading] = useState(true)
  const [forecasting, setForecasting] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [importing, setImporting] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [m5ItemId, setM5ItemId] = useState('FOODS_3_001')
  const [m5StoreId, setM5StoreId] = useState('CA_1')

  const canWrite = hasRole('ADMIN', 'MANAGER', 'ANALYST')
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

  const loadForecast = async () => {
    if (!productId) return
    setForecasting(true)
    setError('')
    setNotice('')
    try {
      setForecast(await aiForecastApi.getForecast(productId))
    } catch (e) {
      setForecast(null)
      setError(getApiErrorMessage(e, 'Không thể lấy dự báo tồn kho AI.'))
    } finally {
      setForecasting(false)
    }
  }

  const refreshRisk = async () => {
    setRefreshing(true)
    setError('')
    setNotice('')
    try {
      const data = await aiForecastApi.refreshRisk(productId)
      setForecast(data)
      setNotice(`Đã đánh giá predictive risk cho ${data.productCode}.`)
    } catch (e) {
      setError(getApiErrorMessage(e, 'Không thể refresh predictive risk.'))
    } finally {
      setRefreshing(false)
    }
  }

  const importM5 = async () => {
    if (!m5ItemId.trim()) return
    setImporting(true)
    setError('')
    setNotice('')
    try {
      const result = await aiForecastApi.importM5(productId, m5ItemId.trim(), m5StoreId.trim() || 'CA_1')
      setNotice(`Đã import ${result.historyRows} ngày M5 thật cho ${result.productCode}: ${result.sourceItemId}/${result.sourceLocationId}.`)
      await loadForecast()
    } catch (e) {
      setError(getApiErrorMessage(e, 'Không thể import M5 demand history.'))
    } finally {
      setImporting(false)
    }
  }

  if (loading) return <LoadingState text="Đang tải sản phẩm cho AI Forecast..." />
  if (error && products.length === 0) return <ErrorState message={error} />

  return <>
    <PageHeader
      title="AI Forecast"
      description="Dự báo nhu cầu từ M5 thật và đánh giá nguy cơ thiếu hàng trên tồn kho doanh nghiệp."
      actions={<div className="button-row">
        <button className="btn btn-secondary" onClick={loadForecast} disabled={!productId || forecasting}>
          {forecasting ? 'Đang dự báo...' : 'Chạy forecast'}
        </button>
        {canWrite && <button className="btn btn-primary" onClick={refreshRisk} disabled={!productId || refreshing}>
          {refreshing ? 'Đang đánh giá...' : 'Refresh AI risk'}
        </button>}
      </div>}
    />

    {error && <div className="alert alert-danger">{error}</div>}
    {notice && <div className="alert ai-notice">{notice}</div>}

    <section className="panel ai-control-panel">
      <div className="panel-heading"><div><h2>Chọn sản phẩm</h2><p>Product doanh nghiệp được map vào một demand series M5 thật để phục vụ demo và nghiên cứu.</p></div></div>
      <div className="ai-control-grid">
        <label className="form-field"><span className="form-label">Sản phẩm</span>
          <select value={productId} onChange={(e) => { setProductId(e.target.value); setForecast(null); setError(''); setNotice('') }}>
            {products.map((product) => <option key={product.id} value={product.id}>{product.productCode} — {product.name}</option>)}
          </select>
        </label>
        {canWrite && <>
          <label className="form-field"><span className="form-label">M5 item ID</span><input value={m5ItemId} onChange={(e) => setM5ItemId(e.target.value)} /></label>
          <label className="form-field"><span className="form-label">M5 store ID</span><input value={m5StoreId} onChange={(e) => setM5StoreId(e.target.value)} /></label>
          <div className="form-field ai-control-action"><span className="form-label">Demand history</span><button className="btn btn-secondary" onClick={importM5} disabled={!productId || importing}>{importing ? 'Đang import...' : 'Import M5 thật'}</button></div>
        </>}
      </div>
      {selectedProduct && <div className="ai-context-line"><strong>{selectedProduct.productCode}</strong><span>{selectedProduct.name}</span></div>}
    </section>

    {!forecast ? <div style={{ marginTop: 20 }}><EmptyState title="Chưa có kết quả forecast" description="Chọn sản phẩm rồi bấm “Chạy forecast”. Nếu sản phẩm chưa có demand mapping, hãy import một M5 series thật trước." /></div> : <>
      <section className="stats-grid ai-stats" style={{ marginTop: 20 }}>
        <StatCard label="Tồn kho hiện tại" value={formatNumber(forecast.currentInventory)} />
        <StatCard label="Ngưỡng tồn kho thấp" value={formatNumber(forecast.lowStockThreshold)} tone="warning" />
        <StatCard label="Rủi ro thiếu hàng" value={forecast.stockoutRisk} tone={forecast.stockoutRisk === 'CRITICAL' || forecast.stockoutRisk === 'HIGH' ? 'danger' : 'warning'} />
        <StatCard label="Ngày dự kiến stockout" value={forecast.projectedStockoutDate ? formatDate(forecast.projectedStockoutDate) : 'Không có'} />
      </section>

      <section className="dashboard-grid ai-dashboard-grid">
        <article className="panel">
          <div className="panel-heading"><div><h2>Dự báo theo horizon</h2><p>Forecast demand, incoming supply và projected inventory.</p></div><Badge value={forecast.stockoutRisk} /></div>
          <div className="table-wrap"><table className="ai-horizon-table"><thead><tr><th>Horizon</th><th>Forecast demand</th><th>Incoming</th><th>Projected inventory</th></tr></thead><tbody>
            {[7, 14, 28].map((days) => <tr key={days}><td><strong>{days} ngày</strong></td><td>{formatNumber(forecast.forecastDemand?.[`next${days}Days`])}</td><td>{formatNumber(forecast.incomingSupply?.[`next${days}Days`])}</td><td className={Number(forecast.projectedInventory?.[`next${days}Days`]) <= 0 ? 'text-danger strong' : 'strong'}>{formatNumber(forecast.projectedInventory?.[`next${days}Days`])}</td></tr>)}
          </tbody></table></div>
        </article>

        <article className="panel">
          <div className="panel-heading"><div><h2>Model context</h2><p>Thông tin inference đang dùng trong FastAPI.</p></div></div>
          <div className="detail-grid">
            <div><small>Product</small><strong>{forecast.productCode}</strong></div>
            <div><small>History gửi sang model</small><strong>{forecast.historyDays} ngày</strong></div>
            <div className="detail-span"><small>Model version</small><strong className="mono">{forecast.modelVersion}</strong></div>
          </div>
        </article>

        <article className="panel panel--wide">
          <div className="panel-heading"><div><h2>Projected inventory 28 ngày</h2><p>Từng ngày: incoming supply được cộng trước, forecast demand được trừ sau.</p></div></div>
          <div className="table-wrap"><table><thead><tr><th>Ngày</th><th>Forecast demand</th><th>Incoming supply</th><th>Projected inventory</th><th>Trạng thái</th></tr></thead><tbody>
            {forecast.dailyProjection?.map((row) => <tr key={row.date}><td>{formatDate(row.date)}</td><td>{formatNumber(row.forecastDemand)}</td><td>{formatNumber(row.incomingSupply)}</td><td className={Number(row.projectedInventory) <= 0 ? 'text-danger strong' : ''}>{formatNumber(row.projectedInventory)}</td><td><Badge value={Number(row.projectedInventory) <= 0 ? 'CRITICAL' : Number(row.projectedInventory) <= Number(forecast.lowStockThreshold) ? 'MEDIUM' : 'LOW'} /></td></tr>)}
          </tbody></table></div>
        </article>
      </section>
    </>}
  </>
}
