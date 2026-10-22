import { useEffect, useMemo, useRef, useState } from 'react'
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
  const { can } = useAuth()
  const today = useMemo(() => new Date(), [])
  const [from, setFrom] = useState(
    isoDate(new Date(today.getTime() - 2 * 86400000)),
  )
  const [to, setTo] = useState(isoDate(today))
  const [events, setEvents] = useState([])
  const [selectedId, setSelectedId] = useState('')
  const [impactState, setImpactState] = useState({
    id: '',
    rows: [],
    loading: false,
    error: '',
  })
  const [impactRetry, setImpactRetry] = useState(0)
  const selectedRef = useRef(selectedId)
  selectedRef.current = selectedId
  const listSequence = useRef(0)
  const impacts = impactState.id === selectedId ? impactState.rows : []
  const impactsLoading =
    !!selectedId && (impactState.id !== selectedId || impactState.loading)
  const impactsError = impactState.id === selectedId ? impactState.error : ''
  const [loading, setLoading] = useState(true)
  const [working, setWorking] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [ingestion, setIngestion] = useState(null)

  const canWrite = can('runIntelligence')
  const selected = useMemo(
    () => events.find((event) => event.id === selectedId),
    [events, selectedId],
  )

  const load = async () => {
    if (from && to && from > to) {
      setError('Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.')
      return
    }
    const request = ++listSequence.current
    setLoading(true)
    setError('')
    try {
      const data = await externalEventApi.getAll(from, to)
      if (request !== listSequence.current) return
      setEvents(data)
      if (data.length && !data.some((event) => event.id === selectedId))
        setSelectedId(data[0].id)
      if (!data.length) setSelectedId('')
    } catch (e) {
      if (request === listSequence.current)
        setError(getApiErrorMessage(e, 'Không thể tải sự kiện thị trường.'))
    } finally {
      if (request === listSequence.current) setLoading(false)
    }
  }

  useEffect(() => {
    load()
    return () => {
      listSequence.current++
    }
  }, [])

  useEffect(() => {
    if (!selectedId) {
      setImpactState({ id: '', rows: [], loading: false, error: '' })
      return
    }
    let active = true
    const controller = new AbortController()
    setImpactState({ id: selectedId, rows: [], loading: true, error: '' })
    externalEventApi
      .getImpacts(selectedId, { signal: controller.signal })
      .then((rows) => {
        if (active)
          setImpactState({ id: selectedId, rows, loading: false, error: '' })
      })
      .catch((error) => {
        if (active)
          setImpactState({
            id: selectedId,
            rows: [],
            loading: false,
            error: getApiErrorMessage(error),
          })
      })
    return () => {
      active = false
      controller.abort()
    }
  }, [selectedId, impactRetry])

  const ingest = async () => {
    setWorking(true)
    setError('')
    setNotice('')
    try {
      const result = await externalEventApi.ingestLatest({ limit: 100 })
      setIngestion(result)
      setNotice(
        `Đã đọc ${result.rowsRead ?? 0} dòng GDELT, tạo ${result.eventsCreated ?? 0} event mới, cập nhật ${result.eventsUpdated ?? 0}.`,
      )
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
      if (selectedRef.current !== selectedId) return
      setImpactState({ id: selectedId, rows: data, loading: false, error: '' })
      setNotice(
        `Đã tính Business Exposure cho ${data.length} supplier liên quan.`,
      )
      const refreshed = await externalEventApi.getById(selectedId)
      setEvents((items) =>
        items.map((item) => (item.id === selectedId ? refreshed : item)),
      )
    } catch (e) {
      setError(getApiErrorMessage(e, 'Không thể tính Business Exposure.'))
    } finally {
      setWorking(false)
    }
  }

  if (loading && events.length === 0)
    return <LoadingState text="Đang tải External Event Intelligence..." />
  if (error && events.length === 0)
    return <ErrorState message={error} onRetry={load} />

  const highImpacts = impacts.filter((impact) =>
    ['HIGH', 'CRITICAL'].includes(impact.impactLevel),
  ).length

  return (
    <>
      <PageHeader
        title="Tín hiệu thị trường"
        description="Theo dõi sự kiện thị trường và mức độ ảnh hưởng tới các đối tác cung ứng."
        actions={
          canWrite ? (
            <button
              className="btn btn-primary"
              onClick={ingest}
              disabled={working}
            >
              {working ? 'Đang xử lý...' : 'Đồng bộ sự kiện GDELT'}
            </button>
          ) : null
        }
      />

      {error && <div className="alert alert-danger">{error}</div>}
      {notice && <div className="alert ai-notice">{notice}</div>}

      <section className="panel external-filter-panel">
        <div className="toolbar external-toolbar">
          <label className="form-field">
            <span className="form-label">Từ ngày</span>
            <input
              type="date"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
            />
          </label>
          <label className="form-field">
            <span className="form-label">Đến ngày</span>
            <input
              type="date"
              min={from}
              value={to}
              onChange={(e) => setTo(e.target.value)}
            />
          </label>
          <button
            className="btn btn-secondary"
            onClick={load}
            disabled={loading || working}
          >
            Tải sự kiện
          </button>
        </div>
        {ingestion && (
          <div className="ai-context-line">
            <strong>GDELT latest</strong>
            <span>
              {ingestion.sourceFile || 'latest export'} · matched{' '}
              {ingestion.rowsMatched ?? 0}
            </span>
          </div>
        )}
      </section>

      <section className="stats-grid" style={{ marginTop: 20 }}>
        <StatCard label="Sự kiện trong khoảng ngày" value={events.length} />
        <StatCard label="Nhà cung cấp liên quan" value={impacts.length} />
        <StatCard
          label="Ảnh hưởng cao / nghiêm trọng"
          value={highImpacts}
          tone={highImpacts ? 'danger' : undefined}
        />
        <StatCard
          label="Tín hiệu hiện tại"
          value={selected?.signal || 'NEUTRAL'}
          tone={selected?.signal === 'RISK' ? 'danger' : undefined}
        />
      </section>

      <section className="split-grid external-split">
        <article className="panel external-list-panel">
          <div className="panel-heading">
            <div>
              <h2>Sự kiện thị trường</h2>
              <p>Chọn sự kiện để xem thông tin và ảnh hưởng tới nguồn cung.</p>
            </div>
          </div>
          {events.length === 0 ? (
            <div style={{ padding: 20 }}>
              <EmptyState title="Không có sự kiện trong khoảng ngày" />
            </div>
          ) : (
            <div className="external-event-list">
              {events.map((event) => (
                <button
                  key={event.id}
                  className={
                    event.id === selectedId
                      ? 'external-event-card active'
                      : 'external-event-card'
                  }
                  disabled={working}
                  onClick={() => {
                    selectedRef.current = event.id
                    setSelectedId(event.id)
                  }}
                >
                  <div className="external-event-card-top">
                    <strong>{event.externalEventId}</strong>
                    <Badge
                      value={event.signal || event.sentiment || 'NEUTRAL'}
                    />
                  </div>
                  <span>
                    {event.actor1Name || 'Unknown actor'}{' '}
                    {event.actor2Name ? `→ ${event.actor2Name}` : ''}
                  </span>
                  <small>
                    {formatDate(event.eventDate)} · CAMEO{' '}
                    {event.eventRootCode || '—'} ·{' '}
                    {event.location ||
                      event.country ||
                      event.countryCode ||
                      'Không rõ vị trí'}
                  </small>
                </button>
              ))}
            </div>
          )}
        </article>

        <div className="external-detail-stack">
          {!selected ? (
            <EmptyState title="Chọn sự kiện để xem chi tiết" />
          ) : (
            <>
              <article className="panel">
                <div className="panel-heading">
                  <div>
                    <h2>Chi tiết sự kiện</h2>
                    <p>
                      {selected.externalSource === 'DEMO_SYNTHETIC'
                        ? 'Tình huống mô phỏng để thử chức năng; không phải tin tức thật.'
                        : 'Thông tin sự kiện từ nguồn dữ liệu GDELT.'}
                    </p>
                  </div>
                  <div className="button-row">
                    <Badge value={selected.sentiment} />
                    <Badge value={selected.signal || 'NEUTRAL'} />
                    {canWrite && (
                      <button
                        className="btn btn-primary"
                        onClick={calculate}
                        disabled={working || impactsLoading}
                      >
                        {working ? 'Đang tính...' : 'Tính mức ảnh hưởng'}
                      </button>
                    )}
                  </div>
                </div>
                <div className="detail-grid external-detail-grid">
                  <div className="detail-span">
                    <small>Nguồn dữ liệu</small>
                    <strong>
                      {selected.externalSource === 'DEMO_SYNTHETIC'
                        ? 'Dữ liệu demo mô phỏng'
                        : selected.externalSource || 'Chưa xác định'}
                    </strong>
                  </div>
                  <div>
                    <small>ID nguồn sự kiện</small>
                    <strong className="mono">{selected.externalEventId}</strong>
                  </div>
                  <div>
                    <small>Ngày</small>
                    <strong>{formatDate(selected.eventDate)}</strong>
                  </div>
                  <div>
                    <small>Actor 1</small>
                    <strong>{selected.actor1Name || '—'}</strong>
                  </div>
                  <div>
                    <small>Actor 2</small>
                    <strong>{selected.actor2Name || '—'}</strong>
                  </div>
                  <div>
                    <small>CAMEO</small>
                    <strong>
                      {selected.eventCode || '—'} / root{' '}
                      {selected.eventRootCode || '—'}
                    </strong>
                  </div>
                  <div>
                    <small>Vị trí</small>
                    <strong>
                      {selected.location ||
                        selected.country ||
                        selected.countryCode ||
                        '—'}
                    </strong>
                  </div>
                  <div>
                    <small>Goldstein</small>
                    <strong>{formatNumber(selected.goldsteinScore)}</strong>
                  </div>
                  <div>
                    <small>Avg tone</small>
                    <strong>{formatNumber(selected.avgTone)}</strong>
                  </div>
                  <div>
                    <small>Mentions / Sources / Articles</small>
                    <strong>
                      {selected.numMentions ?? 0} / {selected.numSources ?? 0} /{' '}
                      {selected.numArticles ?? 0}
                    </strong>
                  </div>
                  <div>
                    <small>Độ tin cậy sắc thái</small>
                    <strong>
                      {selected.sentimentConfidence == null
                        ? 'Chưa phân tích'
                        : `${formatNumber(Number(selected.sentimentConfidence) * 100)}%`}
                    </strong>
                  </div>
                  <div className="detail-span">
                    <small>Mô hình sắc thái</small>
                    <strong className="mono">
                      {selected.sentimentModelVersion || 'NOT_ANALYZED'}
                    </strong>
                  </div>
                  {selected.sourceUrl && (
                    <div className="detail-span">
                      <small>Nguồn sự kiện</small>
                      <a
                        className="table-link external-source-link"
                        href={selected.sourceUrl}
                        target="_blank"
                        rel="noreferrer"
                      >
                        Mở nguồn GDELT
                      </a>
                    </div>
                  )}
                </div>
              </article>

              <article className="panel">
                <div className="panel-heading">
                  <div>
                    <h2>Mức ảnh hưởng tới nhà cung cấp</h2>
                    <p>
                      Điểm ảnh hưởng 0–100 được tính theo quy tắc nghiệp vụ;
                      không phải xác suất dự đoán từ mô hình AI.
                    </p>
                  </div>
                </div>
                {impactsLoading ? (
                  <LoadingState text="Đang tải mức ảnh hưởng…" />
                ) : impactsError ? (
                  <ErrorState
                    message={impactsError}
                    onRetry={() => setImpactRetry((value) => value + 1)}
                  />
                ) : impacts.length === 0 ? (
                  <div style={{ padding: 20 }}>
                    <EmptyState
                      title="Chưa có kết quả đánh giá ảnh hưởng"
                      description={
                        canWrite
                          ? 'Bấm “Tính mức ảnh hưởng” để đánh giá các nhà cung cấp liên quan.'
                          : 'Chưa có đánh giá ảnh hưởng cho sự kiện này.'
                      }
                    />
                  </div>
                ) : (
                  <div className="table-wrap">
                    <table>
                      <thead>
                        <tr>
                          <th>Nhà cung cấp</th>
                          <th>Điểm</th>
                          <th>Mức độ</th>
                          <th>Tín hiệu</th>
                          <th>Cập nhật</th>
                        </tr>
                      </thead>
                      <tbody>
                        {impacts.map((impact) => (
                          <tr key={impact.id}>
                            <td>
                              <strong>{impact.supplierCode}</strong>
                              {impact.explanation && (
                                <details className="impact-explanation">
                                  <summary>Giải thích</summary>
                                  <p>{impact.explanation}</p>
                                </details>
                              )}
                              <br />
                              <small>{impact.supplierName}</small>
                            </td>
                            <td className="strong">
                              {formatNumber(impact.impactScore)}/100
                            </td>
                            <td>
                              <Badge value={impact.impactLevel} />
                            </td>
                            <td>
                              <Badge value={impact.impactType} />
                            </td>
                            <td>{formatDateTime(impact.calculatedAt)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </article>
            </>
          )}
        </div>
      </section>
    </>
  )
}
