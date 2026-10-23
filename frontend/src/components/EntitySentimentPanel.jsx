import { useEffect, useRef, useState } from 'react'
import { externalEventApi } from '../api/externalEventApi'
import { supplierApi } from '../api/supplierApi'
import { getApiErrorMessage } from '../api/apiClient'
import Badge from './Badge'
import { EmptyState, ErrorState, LoadingState } from './States'
import { formatDateTime, formatNumber } from '../utils/formatters'

export default function EntitySentimentPanel({
  eventId,
  canAnalyze,
  onAnalyzed,
  onBusyChange,
}) {
  const [rows, setRows] = useState([])
  const [suppliers, setSuppliers] = useState([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  const [entity, setEntity] = useState('')
  const [text, setText] = useState('')
  const [supplierId, setSupplierId] = useState('')
  const [language, setLanguage] = useState('auto')
  const analysisContext = useRef(null)
  const busyCallback = useRef(onBusyChange)
  busyCallback.current = onBusyChange

  useEffect(() => {
    const context = { active: true, busy: false }
    analysisContext.current = context
    setBusy(false)
    setEntity('')
    setText('')
    setSupplierId('')
    setLanguage('auto')
    return () => {
      context.active = false
      if (context.busy) busyCallback.current?.(false)
    }
  }, [eventId, canAnalyze])

  useEffect(() => {
    let active = true
    const controller = new AbortController()
    setLoading(true)
    setRows([])
    setSuppliers([])
    setError('')
    async function load() {
      try {
        const [analyses, partners] = await Promise.all([
          externalEventApi.getSentiments(eventId, {
            signal: controller.signal,
          }),
          canAnalyze ? supplierApi.getAll() : Promise.resolve([]),
        ])
        if (active) {
          setRows(analyses)
          setSuppliers(partners)
        }
      } catch (failure) {
        if (active)
          setError(
            getApiErrorMessage(failure, 'Không thể tải kết quả sentiment.'),
          )
      } finally {
        if (active) setLoading(false)
      }
    }
    load()
    return () => {
      active = false
      controller.abort()
    }
  }, [eventId, canAnalyze, retry])

  async function analyze(event) {
    event.preventDefault()
    const context = analysisContext.current
    if (!canAnalyze || !context?.active || context.busy || loading) return
    context.busy = true
    setBusy(true)
    busyCallback.current?.(true)
    setError('')
    try {
      const result = await externalEventApi.analyzeSentiment(eventId, {
        entity: entity.trim(),
        text: text.trim(),
        supplierId: supplierId || null,
        language,
      })
      if (!context.active) return
      const analyses = await externalEventApi.getSentiments(eventId)
      if (!context.active) return
      setRows(analyses)
      onAnalyzed?.(result)
    } catch (failure) {
      if (context.active)
        setError(
          getApiErrorMessage(
            failure,
            'Không thể phân tích sentiment của thực thể.',
          ),
        )
    } finally {
      context.busy = false
      if (context.active) {
        setBusy(false)
        busyCallback.current?.(false)
      }
    }
  }

  return (
    <article className="panel">
      <div className="panel-heading">
        <div>
          <h2>Sentiment theo từng công ty</h2>
          <p>
            Nhập tiêu đề hoặc đoạn tin có nhắc đúng thực thể. Liên kết nhà cung
            cấp để dùng kết quả khi tính ảnh hưởng.
          </p>
        </div>
      </div>
      {error && (
        <ErrorState
          message={error}
          onRetry={() => setRetry((value) => value + 1)}
        />
      )}
      {canAnalyze && (
        <form className="form-grid" onSubmit={analyze} style={{ padding: 20 }}>
          <label className="form-field">
            <span className="form-label">Tên thực thể xuất hiện trong tin</span>
            <input
              required
              maxLength={300}
              value={entity}
              onChange={(event) => setEntity(event.target.value)}
              disabled={busy}
              placeholder="Tên công ty hoặc mã chứng khoán trong nội dung"
            />
          </label>
          <label className="form-field">
            <span className="form-label">Nhà cung cấp liên kết</span>
            <select
              value={supplierId}
              onChange={(event) => setSupplierId(event.target.value)}
              disabled={busy}
            >
              <option value="">Chỉ phân tích thực thể</option>
              {suppliers.map((supplier) => (
                <option key={supplier.id} value={supplier.id}>
                  {supplier.supplierCode} · {supplier.name}
                </option>
              ))}
            </select>
          </label>
          <label className="form-field">
            <span className="form-label">Ngôn ngữ nội dung</span>
            <select
              value={language}
              onChange={(event) => setLanguage(event.target.value)}
              disabled={busy}
            >
              <option value="auto">Tự nhận diện Anh / Việt</option>
              <option value="en">Tiếng Anh</option>
              <option value="vi">Tiếng Việt</option>
            </select>
          </label>
          <label className="form-field" style={{ gridColumn: '1 / -1' }}>
            <span className="form-label">Nội dung tin</span>
            <textarea
              required
              rows={5}
              maxLength={20000}
              value={text}
              onChange={(event) => setText(event.target.value)}
              disabled={busy}
              placeholder="Dán nội dung nguồn tin, giữ nguyên tên công ty cần phân tích."
            />
          </label>
          <div className="button-row" style={{ gridColumn: '1 / -1' }}>
            <button
              className="btn btn-primary"
              type="submit"
              disabled={busy || loading || !entity.trim() || !text.trim()}
            >
              {busy ? 'Đang phân tích…' : 'Phân tích sentiment'}
            </button>
          </div>
        </form>
      )}
      {loading ? (
        <LoadingState text="Đang tải sentiment theo thực thể…" />
      ) : rows.length === 0 ? (
        <div style={{ padding: 20 }}>
          <EmptyState title="Chưa có kết quả theo thực thể" />
        </div>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Thực thể</th>
                <th>Nhà cung cấp</th>
                <th>Sentiment</th>
                <th>Tin cậy</th>
                <th>Trạng thái</th>
                <th>Cập nhật</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id}>
                  <td>
                    <strong>{row.entity}</strong>
                    <br />
                    <small>
                      {row.language === 'vi' ? 'Tiếng Việt' : 'Tiếng Anh'} ·{' '}
                      {row.modelVersion}
                    </small>
                  </td>
                  <td>
                    {row.supplierCode || 'Chưa liên kết'}
                    {row.supplierName && (
                      <>
                        <br />
                        <small>{row.supplierName}</small>
                      </>
                    )}
                  </td>
                  <td>
                    <Badge value={row.sentiment} />
                  </td>
                  <td>{formatNumber(Number(row.confidence) * 100)}%</td>
                  <td>
                    {row.needsReview ? 'Cần duyệt' : 'Đạt ngưỡng tin cậy'}
                    {row.reviewReason === 'DOMAIN_NOT_VALIDATED' && (
                      <>
                        <br />
                        <small>
                          Tin doanh nghiệp tiếng Việt cần người kiểm tra
                        </small>
                      </>
                    )}
                    {row.inputTruncated && (
                      <>
                        <br />
                        <small>Đã lấy đoạn quanh thực thể</small>
                      </>
                    )}
                  </td>
                  <td>{formatDateTime(row.analyzedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p style={{ padding: '0 20px 16px' }}>
            Kết quả cần duyệt chưa được dùng làm tín hiệu sentiment trong điểm
            ảnh hưởng. Độ tin cậy sentiment không phải xác suất nhà cung cấp gặp
            sự cố.
          </p>
        </div>
      )}
    </article>
  )
}
