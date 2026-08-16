import { useEffect, useState } from 'react'
import { riskMonitoringApi } from '../api/riskMonitoringApi'
import { getApiErrorMessage } from '../api/apiClient'
import { useAuth } from '../auth/AuthContext'
import PageHeader from '../components/PageHeader'
import StatCard from '../components/StatCard'
import Badge from '../components/Badge'
import { ErrorState, LoadingState } from '../components/States'
import { formatDateTime } from '../utils/formatters'

export default function RiskMonitoringPage() {
  const { hasRole } = useAuth()
  const [summary, setSummary] = useState(null)
  const [rules, setRules] = useState([])
  const [lastResult, setLastResult] = useState(null)
  const [loading, setLoading] = useState(true)
  const [running, setRunning] = useState(false)
  const [error, setError] = useState('')

  const canRun = hasRole('ADMIN', 'MANAGER', 'ANALYST')

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      const [summaryData, ruleData] = await Promise.all([
        riskMonitoringApi.getSummary(),
        riskMonitoringApi.getRules(),
      ])
      setSummary(summaryData)
      setRules(ruleData)
    } catch (e) {
      setError(getApiErrorMessage(e, 'Không thể tải trạng thái giám sát rủi ro.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  const runScan = async () => {
    setRunning(true)
    setError('')
    try {
      const result = await riskMonitoringApi.run()
      setLastResult(result)
      setSummary(await riskMonitoringApi.getSummary())
    } catch (e) {
      setError(getApiErrorMessage(e, 'Không thể chạy quét rủi ro.'))
    } finally {
      setRunning(false)
    }
  }

  if (loading) return <LoadingState text="Đang tải Risk Monitoring Engine..." />
  if (error && !summary) return <ErrorState message={error} onRetry={load} />

  return <>
    <PageHeader title="Giám sát rủi ro" description="Rule-based Risk Monitoring Engine tự động phát hiện, cập nhật và giải quyết rủi ro từ dữ liệu vận hành." actions={canRun ? <button className="btn btn-primary" onClick={runScan} disabled={running}>{running ? 'Đang quét...' : 'Quét rủi ro ngay'}</button> : null} />
    {error && <div className="state error">{error}</div>}
    <section className="panel" style={{ marginBottom: 20 }}>
      <div className="panel-heading"><div><h2>Trạng thái giám sát</h2><p>Scheduler chạy theo cấu hình backend; thao tác quét thủ công chỉ dành cho vai trò được cấp quyền.</p></div><Badge value={summary?.enabled ? 'ACTIVE' : 'INACTIVE'} /></div>
      <p><strong>Lần quét gần nhất:</strong> {summary?.lastScan ? formatDateTime(summary.lastScan) : 'Chưa có trong phiên chạy hiện tại'}</p>
    </section>
    <section className="stats-grid">
      <StatCard label="Rủi ro tự động đang mở" value={summary?.automaticActiveRisks ?? 0} tone="danger" />
      <StatCard label="Nghiêm trọng" value={summary?.criticalRisks ?? 0} tone="danger" />
      <StatCard label="Mức cao" value={summary?.highRisks ?? 0} tone="warning" />
      <StatCard label="Tồn kho thấp" value={summary?.lowStockRisks ?? 0} tone="warning" />
      <StatCard label="Đơn mua trễ" value={summary?.delayedOrderRisks ?? 0} tone="warning" />
      <StatCard label="Vận chuyển trễ" value={summary?.delayedShipmentRisks ?? 0} tone="warning" />
      <StatCard label="NCC rủi ro cao" value={summary?.highRiskSupplierRisks ?? 0} tone="danger" />
    </section>
    {lastResult && <section className="panel" style={{ marginTop: 20 }}>
      <div className="panel-heading"><div><h2>Kết quả lần quét vừa chạy</h2><p>{formatDateTime(lastResult.evaluatedAt)}</p></div></div>
      <div className="stats-grid">
        <StatCard label="Low stock phát hiện" value={lastResult.lowStockDetected} />
        <StatCard label="Đơn mua trễ" value={lastResult.delayedPurchaseOrders} />
        <StatCard label="Shipment trễ" value={lastResult.delayedShipments} />
        <StatCard label="NCC HIGH" value={lastResult.highRiskSuppliers} />
        <StatCard label="Risk mới" value={lastResult.riskEventsCreated} tone="danger" />
        <StatCard label="Risk cập nhật" value={lastResult.riskEventsUpdated} tone="warning" />
        <StatCard label="Risk đã resolve" value={lastResult.riskEventsResolved} />
        <StatCard label="Alert mới" value={lastResult.alertsCreated} tone="warning" />
        <StatCard label="Alert đã resolve" value={lastResult.alertsResolved} />
      </div>
    </section>}
    <section className="panel" style={{ marginTop: 20 }}>
      <div className="panel-heading"><div><h2>Quy tắc đang giám sát</h2><p>Giai đoạn 5 sử dụng rule-based monitoring, chưa sử dụng AI/Machine Learning.</p></div></div>
      <div className="compact-list">{rules.map((rule) => <div className="compact-row" key={rule.code}><div><strong>{rule.code}</strong><small>{rule.description}</small></div></div>)}</div>
    </section>
  </>
}
