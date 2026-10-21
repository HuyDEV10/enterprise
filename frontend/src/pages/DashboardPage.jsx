import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  ArrowDownRight,
  ArrowRight,
  ArrowUpRight,
  Bell,
  Boxes,
  Building2,
  CalendarDays,
  RefreshCw,
  ShieldCheck,
  ShoppingCart,
  Truck,
  Zap,
} from 'lucide-react'
import { dashboardApi } from '../api/dashboardApi'
import { riskEventApi } from '../api/riskEventApi'
import { alertApi } from '../api/alertApi'
import { inventoryApi } from '../api/inventoryApi'
import { riskMonitoringApi } from '../api/riskMonitoringApi'
import { getApiErrorMessage } from '../api/apiClient'
import { useAuth } from '../auth/AuthContext'
import StatCard from '../components/StatCard'
import Badge from '../components/Badge'
import { ErrorState, LoadingState, EmptyState } from '../components/States'
import { formatDateTime, formatNumber } from '../utils/formatters'

export default function DashboardPage() {
  const { user, can } = useAuth()
  const [widgets, setWidgets] = useState({})
  const [refreshing, setRefreshing] = useState(false)
  const [updated, setUpdated] = useState(null)
  const sequence = useRef(0)
  const readRisks = can('readRisks'),
    readAlerts = can('readAlerts')
  async function load() {
    const current = ++sequence.current
    const jobs = {
      summary: dashboardApi.getSummary,
      monitoring: riskMonitoringApi.getSummary,
      stock: inventoryApi.getLowStock,
      ...(readRisks ? { risks: riskEventApi.getAll } : {}),
      ...(readAlerts ? { alerts: alertApi.getAll } : {}),
    }
    setRefreshing(true)
    setWidgets(
      Object.fromEntries(
        Object.keys(jobs).map((key) => [key, { loading: true }]),
      ),
    )
    await Promise.all(
      Object.entries(jobs).map(async ([key, request]) => {
        try {
          const data = await request()
          if (sequence.current === current)
            setWidgets((previous) => ({ ...previous, [key]: { data } }))
        } catch (error) {
          if (sequence.current === current)
            setWidgets((previous) => ({
              ...previous,
              [key]: { error: getApiErrorMessage(error) },
            }))
        }
      }),
    )
    if (sequence.current === current) {
      setRefreshing(false)
      setUpdated(new Date())
    }
  }
  useEffect(() => {
    load()
    return () => {
      sequence.current++
    }
  }, [readRisks, readAlerts])
  const data = widgets.summary?.data,
    monitoring = widgets.monitoring?.data
  const risks = [...(widgets.risks?.data || [])]
    .sort((a, b) => new Date(b.detectedAt) - new Date(a.detectedAt))
    .slice(0, 4)
  const alerts = (widgets.alerts?.data || [])
    .filter(
      (a) =>
        ['HIGH', 'CRITICAL'].includes(a.priority) && a.status !== 'RESOLVED',
    )
    .slice(0, 4)
  const stock = (widgets.stock?.data || []).slice(0, 5)
  const breakdown = [
    {
      label: 'Tồn kho thấp',
      value: monitoring?.lowStockRisks,
      color: '#e6a04b',
    },
    {
      label: 'Đơn mua trễ',
      value: monitoring?.delayedOrderRisks,
      color: '#7e83e9',
    },
    {
      label: 'Vận chuyển trễ',
      value: monitoring?.delayedShipmentRisks,
      color: '#e17e83',
    },
    {
      label: 'Rủi ro nhà cung cấp',
      value: monitoring?.highRiskSupplierRisks,
      color: '#24a894',
    },
  ]
  const total = breakdown.reduce((sum, row) => sum + Number(row.value || 0), 0)
  let cursor = 0
  const segments = breakdown
    .map((row) => {
      const start = cursor
      cursor += total ? (Number(row.value || 0) / total) * 100 : 0
      return `${row.color} ${start}% ${cursor}%`
    })
    .join(', ')
  const date = new Intl.DateTimeFormat('vi-VN', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(new Date())
  function state(key, children) {
    const item = widgets[key]
    if (!item || item.loading) return <LoadingState />
    if (item.error) return <ErrorState message={item.error} onRetry={load} />
    return children
  }
  return (
    <>
      <div className="dashboard-heading">
        <div>
          <span className="page-eyebrow">BỨC TRANH VẬN HÀNH CỦA BẠN</span>
          <h1>
            Tổng quan chuỗi cung ứng<span className="title-dot">.</span>
          </h1>
          <p>
            Xin chào {user?.fullName || user?.username}, cùng nắm bắt những điều
            cần chú ý hôm nay.
          </p>
        </div>
        <div className="dashboard-heading-actions">
          <span className="date-chip">
            <CalendarDays size={15} />
            {date}
          </span>
          <button
            className="icon-button refresh-button"
            aria-label="Làm mới tổng quan"
            onClick={load}
            disabled={refreshing}
          >
            <RefreshCw size={17} className={refreshing ? 'rotating' : ''} />
          </button>
        </div>
      </div>
      <section className="overview-banner">
        <div className="banner-icon">
          <ShieldCheck size={26} />
        </div>
        <div>
          <span className="eyebrow">CHỦ ĐỘNG TRONG MỌI QUYẾT ĐỊNH</span>
          <h2>Hiểu dữ liệu. Đón đầu thay đổi.</h2>
          <p>Theo dõi vận hành và nhận diện sớm các điểm cần hành động.</p>
        </div>
        <Link to="/risk-monitoring" className="banner-link">
          Mở giám sát <ArrowUpRight size={17} />
        </Link>
        <div className="banner-orbit" aria-hidden="true" />
      </section>
      {widgets.summary?.error && (
        <div className="alert alert-danger" role="alert">
          {widgets.summary.error}{' '}
          <button className="link-button" onClick={load}>
            Thử lại
          </button>
        </div>
      )}
      <section className="stats-grid dashboard-stats">
        <StatCard
          label="Nhà cung cấp"
          value={data?.totalSuppliers}
          hint={
            data
              ? `${data.highRiskSuppliers} đối tác có rủi ro cao`
              : 'Đang cập nhật dữ liệu'
          }
          icon={Building2}
        />
        <StatCard
          label="Đơn mua hàng"
          value={data?.totalPurchaseOrders}
          hint={
            data
              ? `${data.delayedPurchaseOrders} đơn cần chú ý tiến độ`
              : 'Đang cập nhật dữ liệu'
          }
          icon={ShoppingCart}
        />
        <StatCard
          label="Mặt hàng tồn kho thấp"
          value={data?.lowStockItems}
          hint="Theo ngưỡng cảnh báo từng kho"
          tone="warning"
          icon={Boxes}
        />
        <StatCard
          label="Rủi ro đang mở"
          value={data?.openRiskEvents}
          hint={
            data
              ? `${data.unresolvedAlerts} cảnh báo chưa xử lý`
              : 'Đang cập nhật dữ liệu'
          }
          tone="danger"
          icon={ShieldCheck}
        />
      </section>
      <section className="dashboard-analysis-grid">
        <article className="panel risk-overview">
          <div className="panel-heading">
            <div>
              <span className="section-eyebrow">RISK MONITORING</span>
              <h2>Phân bố rủi ro vận hành</h2>
              <p>Các tín hiệu tự động đang cần theo dõi</p>
            </div>
            <Link to="/risk-monitoring" className="subtle-link">
              Chi tiết <ArrowUpRight size={15} />
            </Link>
          </div>
          {state(
            'monitoring',
            <div className="risk-chart-content">
              <div
                className="risk-donut"
                style={{
                  background: total ? `conic-gradient(${segments})` : '#edf2f1',
                }}
                role="img"
                aria-label={`Tổng ${total} rủi ro thuộc bốn nhóm vận hành`}
              >
                <div>
                  <strong>{formatNumber(total)}</strong>
                  <span>rủi ro vận hành</span>
                </div>
              </div>
              <div className="risk-legend">
                {breakdown.map((row) => (
                  <div key={row.label}>
                    <span
                      className="legend-dot"
                      style={{ background: row.color }}
                    />
                    <span>{row.label}</span>
                    <strong>{formatNumber(row.value ?? 0)}</strong>
                  </div>
                ))}
              </div>
            </div>,
          )}
        </article>
        <article className="quick-actions-panel">
          <span className="quick-icon">
            <Zap size={21} />
          </span>
          <span className="section-eyebrow">BẮT ĐẦU TỪ ĐÂY</span>
          <h2>
            Mỗi hành động,
            <br />
            một bước chủ động.
          </h2>
          <p>Truy cập nhanh công việc thường ngày của bạn.</p>
          <div className="quick-actions">
            {[
              [
                can('writeOperations')
                  ? '/purchase-orders/new'
                  : '/purchase-orders',
                'Đơn mua hàng',
                ShoppingCart,
              ],
              ['/inventory?filter=low', 'Kiểm tra tồn kho', Boxes],
              ['/ai-forecast', 'Xem dự báo nhu cầu', ArrowUpRight],
            ].map(([to, label, Icon]) => (
              <Link to={to} key={to}>
                <Icon size={17} />
                <span>{label}</span>
                <ArrowRight size={15} />
              </Link>
            ))}
          </div>
        </article>
      </section>
      <section className="dashboard-grid">
        {readRisks && (
          <article className="panel">
            <div className="panel-heading">
              <div>
                <span className="section-eyebrow">CẬP NHẬT GẦN ĐÂY</span>
                <h2>Sự kiện rủi ro</h2>
              </div>
              <Link className="subtle-link" to="/risks">
                Xem tất cả <ArrowUpRight size={15} />
              </Link>
            </div>
            {state(
              'risks',
              risks.length ? (
                <div className="compact-list">
                  {risks.map((item) => (
                    <Link
                      to={`/risks/${item.id}`}
                      className="compact-row"
                      key={item.id}
                    >
                      <span
                        className={`event-indicator ${['HIGH', 'CRITICAL'].includes(item.impactLevel) ? 'event-indicator--danger' : ''}`}
                      >
                        <ShieldCheck size={17} />
                      </span>
                      <div>
                        <strong>{item.title}</strong>
                        <small>
                          {item.autoGenerated
                            ? 'Tự động phát hiện'
                            : 'Ghi nhận thủ công'}{' '}
                          · {formatDateTime(item.detectedAt)}
                        </small>
                      </div>
                      <Badge value={item.impactLevel} />
                    </Link>
                  ))}
                </div>
              ) : (
                <EmptyState
                  title="Chưa ghi nhận rủi ro"
                  description="Các sự kiện rủi ro mới sẽ được hiển thị tại đây."
                />
              ),
            )}
          </article>
        )}
        {readAlerts && (
          <article className="panel">
            <div className="panel-heading">
              <div>
                <span className="section-eyebrow">CẦN ƯU TIÊN</span>
                <h2>Cảnh báo quan trọng</h2>
              </div>
              <Link className="subtle-link" to="/alerts">
                Xem tất cả <ArrowUpRight size={15} />
              </Link>
            </div>
            {state(
              'alerts',
              alerts.length ? (
                <div className="compact-list">
                  {alerts.map((item) => (
                    <div className="compact-row" key={item.id}>
                      <span className="event-indicator event-indicator--warning">
                        <Bell size={17} />
                      </span>
                      <div>
                        <strong>{item.title}</strong>
                        <small>{item.message || 'Cần theo dõi và xử lý'}</small>
                      </div>
                      <Badge value={item.priority} />
                    </div>
                  ))}
                </div>
              ) : (
                <EmptyState
                  title="Không có cảnh báo ưu tiên"
                  description="Bạn đã nắm được các cập nhật quan trọng."
                />
              ),
            )}
          </article>
        )}
        <article className="panel panel--wide">
          <div className="panel-heading">
            <div>
              <span className="section-eyebrow">INVENTORY WATCH</span>
              <h2>Tồn kho cần bổ sung</h2>
              <p>Các mặt hàng đã chạm hoặc thấp hơn ngưỡng cảnh báo</p>
            </div>
            <Link className="subtle-link" to="/inventory?filter=low">
              Mở tồn kho <ArrowUpRight size={15} />
            </Link>
          </div>
          {state(
            'stock',
            stock.length ? (
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Sản phẩm</th>
                      <th>Kho hàng</th>
                      <th>Số lượng</th>
                      <th>Ngưỡng cảnh báo</th>
                      <th>Tình trạng</th>
                    </tr>
                  </thead>
                  <tbody>
                    {stock.map((item) => (
                      <tr key={item.id}>
                        <td>
                          <span className="product-cell">
                            <span className="product-icon">
                              <Boxes size={17} />
                            </span>
                            <strong>{item.productName}</strong>
                          </span>
                        </td>
                        <td>{item.warehouseName}</td>
                        <td className="text-danger strong">
                          {formatNumber(item.quantity)}
                        </td>
                        <td>{formatNumber(item.lowStockThreshold)}</td>
                        <td>
                          <span className="badge badge--high">Cần bổ sung</span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <EmptyState
                title="Tồn kho đang trong ngưỡng an toàn"
                description="Không có mặt hàng nào chạm ngưỡng cảnh báo."
              />
            ),
          )}
        </article>
      </section>
      <div className="data-timestamp">
        <span className="status-dot" />
        {updated
          ? `Lần tải gần nhất · ${formatDateTime(updated)}`
          : 'Đang đồng bộ dữ liệu…'}
      </div>
    </>
  )
}
