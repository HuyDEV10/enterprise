import { useEffect, useMemo, useState } from 'react'
import { externalEventApi } from '../api/externalEventApi'
import { getApiErrorMessage } from '../api/apiClient'
import { useAuth } from '../auth/AuthContext'
import PageHeader from '../components/PageHeader'
import Badge from '../components/Badge'
import StatCard from '../components/StatCard'
import { EmptyState, ErrorState, LoadingState } from '../components/States'
import { formatDate, formatDateTime, formatNumber } from '../utils/formatters'

function isoDate(date) {
  const offset = date.getTimezoneOffset()
  return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 10)
}

export default function ExternalEventsPage() {
  const { hasRole } = useAuth()
  const today = useMemo(() => new Date(), [])
  const [from, setFrom] = useState(isoDate(new Date(today.getTime() - 2 * 86400000)))
  const [to, setTo] = useState(isoDate(today))
  const [events, setEvents] = useState([])
  const [selectedId, setSelectedId] = useState('')
  const [impacts, setImpacts] = useState([])
  const [loading, setLoading] = useState(true)
  const [working, setWorking] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [ingestion, setIngestion] = useState(null)

  const canWrite = hasRole('ADMIN', 'MANAGER', 'ANALYST')
  const selected = useMemo(() => events.find((event) => event.id === selectedId), [events, selectedId])

  const load = async () => {
    setLoading(true)
    setError('')
    try {
      const data = await externalEventApi.getAll(from, to)
      setEvents(data)
      if (data.length && !data.some((event) => event.id === selectedId)) setSelectedId(data[0].id)
      if (!data.length) setSelectedId('')
    } catch (e) {
      setError(getApiErrorMessage(e, 'Không thể tải external events.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  useEffect(() => {
    if (!selectedId) { setImpacts([]); return }
    externalEventApi.getImpacts(selectedId).then(setImpacts).catch(() => setImpacts([]))
  }, [selectedId])

  const ingest = async () => {
    setWorking(true)
    setError('')
    setNotice('')
    try {
      const result = await externalEventApi.ingestLatest({ limit: 100 })
      setIngestion(result)
      setNotice(`Đã đọc ${result.rowsRead ?? 0} dòng GDELT, tạo ${result.eventsCreated ?? 0} event mới, cập nhật ${result.eventsUpdated ?? 0}.`)
      await load()
    } catch (e) {
      setError(getApiErrorMessage(e, 'Không thể ingest GDELT latest feed.'))
    } finally {
      setWorking(false)
    }
  }

  const calculate = async () => {
    if (!selectedId) return
    setWorking(true)
    setError('')
    setNotice('')
    try {
      const data = await externalEventApi.calculateImpacts(selectedId)
      setImpacts(data)
      setNotice(`Đã tính Business Exposure cho ${data.length} supplier liên quan.`)
      const refreshed = await externalEventApi.getById(selectedId)
      setEvents((items) => items.map((item) => item.id === selectedId ? refreshed : item))
    } catch (e) {
      setError(getApiErrorMessage(e, 'Không thể tính Business Exposure.'))
    } finally {
      setWorking(false)
    }
  }

  if (loading && events.length === 0) return <LoadingState text="Đang tải External Event Intelligence..." />
  if (error && events.length === 0) return <ErrorState message={error} onRetry={load} />

  const highImpacts = impacts.filter((impact) => ['HIGH', 'CRITICAL'].includes(impact.impactLevel)).length

  return <>
    <PageHeader
      title="External Event Intelligence"
      description="Theo dõi sự kiện GDELT thật, sentiment theo entity và mức độ phơi nhiễm của doanh nghiệp."
      actions={canWrite ? <button className="btn btn-primary" onClick={ingest} disabled={working}>{working ? 'Đang xử lý...' : 'Ingest GDELT latest'}</button> : null}
    />

    {error && <div className="alert alert-danger">{error}</div>}
    {notice && <div className="alert ai-notice">{notice}</div>}

    <section className="panel external-filter-panel">
      <div className="toolbar external-toolbar">
        <label className="form-field"><span className="form-label">Từ ngày</span><input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
        <label className="form-field"><span className="form-label">Đến ngày</span><input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
        <button className="btn btn-secondary" onClick={load}>Tải sự kiện</button>
      </div>
      {ingestion && <div className="ai-context-line"><strong>GDELT latest</strong><span>{ingestion.sourceFile || 'latest export'} · matched {ingestion.rowsMatched ?? 0}</span></div>}
    </section>

    <section className="stats-grid" style={{ marginTop: 20 }}>
      <StatCard label="Events trong khoảng ngày" value={events.length} />
      <StatCard label="Impacts của event đang chọn" value={impacts.length} />
      <StatCard label="HIGH / CRITICAL exposure" value={highImpacts} tone={highImpacts ? 'danger' : undefined} />
      <StatCard label="Signal hiện tại" value={selected?.signal || 'NEUTRAL'} tone={selected?.signal === 'RISK' ? 'danger' : undefined} />
    </section>

    <section className="split-grid external-split">
      <article className="panel external-list-panel">
        <div className="panel-heading"><div><h2>External events</h2><p>Chọn một event để xem chi tiết và exposure.</p></div></div>
        {events.length === 0 ? <div style={{ padding: 20 }}><EmptyState title="Không có event trong khoảng ngày" /></div> : <div className="external-event-list">
          {events.map((event) => <button key={event.id} className={event.id === selectedId ? 'external-event-card active' : 'external-event-card'} onClick={() => setSelectedId(event.id)}>
            <div className="external-event-card-top"><strong>{event.externalEventId}</strong><Badge value={event.signal || event.sentiment || 'NEUTRAL'} /></div>
            <span>{event.actor1Name || 'Unknown actor'} {event.actor2Name ? `→ ${event.actor2Name}` : ''}</span>
            <small>{formatDate(event.eventDate)} · CAMEO {event.eventRootCode || '—'} · {event.location || event.country || event.countryCode || 'Không rõ vị trí'}</small>
          </button>)}
        </div>}
      </article>

      <div className="external-detail-stack">
        {!selected ? <EmptyState title="Chọn một external event" /> : <>
          <article className="panel">
            <div className="panel-heading"><div><h2>Event detail</h2><p>GDELT Event Database record đã lưu trong PostgreSQL.</p></div><div className="button-row"><Badge value={selected.sentiment} /><Badge value={selected.signal || 'NEUTRAL'} />{canWrite && <button className="btn btn-primary" onClick={calculate} disabled={working}>{working ? 'Đang tính...' : 'Tính Business Exposure'}</button>}</div></div>
            <div className="detail-grid external-detail-grid">
              <div><small>GDELT Event ID</small><strong className="mono">{selected.externalEventId}</strong></div>
              <div><small>Ngày</small><strong>{formatDate(selected.eventDate)}</strong></div>
              <div><small>Actor 1</small><strong>{selected.actor1Name || '—'}</strong></div>
              <div><small>Actor 2</small><strong>{selected.actor2Name || '—'}</strong></div>
              <div><small>CAMEO</small><strong>{selected.eventCode || '—'} / root {selected.eventRootCode || '—'}</strong></div>
              <div><small>Vị trí</small><strong>{selected.location || selected.country || selected.countryCode || '—'}</strong></div>
              <div><small>Goldstein</small><strong>{formatNumber(selected.goldsteinScore)}</strong></div>
              <div><small>Avg tone</small><strong>{formatNumber(selected.avgTone)}</strong></div>
              <div><small>Mentions / Sources / Articles</small><strong>{selected.numMentions ?? 0} / {selected.numSources ?? 0} / {selected.numArticles ?? 0}</strong></div>
              <div><small>Sentiment confidence</small><strong>{selected.sentimentConfidence == null ? 'Chưa phân tích' : `${formatNumber(Number(selected.sentimentConfidence) * 100)}%`}</strong></div>
              <div className="detail-span"><small>Sentiment model</small><strong className="mono">{selected.sentimentModelVersion || 'NOT_ANALYZED'}</strong></div>
              {selected.sourceUrl && <div className="detail-span"><small>Source URL</small><a className="table-link external-source-link" href={selected.sourceUrl} target="_blank" rel="noreferrer">Mở nguồn GDELT</a></div>}
            </div>
          </article>

          <article className="panel">
            <div className="panel-heading"><div><h2>Supplier Business Exposure</h2><p>Weighted score 0–100; đây là business rule score, không phải supervised ML prediction.</p></div></div>
            {impacts.length === 0 ? <div style={{ padding: 20 }}><EmptyState title="Chưa có supplier exposure" description={canWrite ? 'Bấm “Tính Business Exposure” để chạy relevance gate và scoring.' : 'Chưa có kết quả exposure cho event này.'} /></div> : <div className="table-wrap"><table><thead><tr><th>Supplier</th><th>Score</th><th>Level</th><th>Signal</th><th>Calculated</th></tr></thead><tbody>
              {impacts.map((impact) => <tr key={impact.id}><td><strong>{impact.supplierCode}</strong><br /><small>{impact.supplierName}</small></td><td className="strong">{formatNumber(impact.impactScore)}/100</td><td><Badge value={impact.impactLevel} /></td><td><Badge value={impact.impactType} /></td><td>{formatDateTime(impact.calculatedAt)}</td></tr>)}
            </tbody></table></div>}
            {impacts[0]?.explanation && <details className="exposure-explanation"><summary>Xem giải thích scoring</summary><pre>{impacts[0].explanation}</pre></details>}
          </article>
        </>}
      </div>
    </section>
  </>
}
