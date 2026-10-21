import { useState } from 'react'
import { statusLabels } from '../utils/workflows'
import { getApiErrorMessage } from '../api/apiClient'
// Separate selection and confirmation avoids accidental lifecycle transitions.
export default function StatusControl({
  value,
  transitions,
  onChange,
  label = 'Cập nhật trạng thái',
}) {
  const [next, setNext] = useState(''),
    [busy, setBusy] = useState(false),
    [error, setError] = useState('')
  const options = transitions[value] || []
  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await onChange(next)
      setNext('')
    } catch (e) {
      setError(getApiErrorMessage(e))
    } finally {
      setBusy(false)
    }
  }
  if (!options.length)
    return (
      <p className="workflow-note">Đã kết thúc. Không còn chuyển trạng thái.</p>
    )
  return (
    <form className="status-control" onSubmit={submit}>
      <label className="form-field">
        <span className="form-label">{label}</span>
        <select
          value={next}
          onChange={(e) => setNext(e.target.value)}
          required
          disabled={busy}
        >
          <option value="">Chọn bước tiếp theo</option>
          {options.map((status) => (
            <option value={status} key={status}>
              {statusLabels[status]}
            </option>
          ))}
        </select>
      </label>
      <button className="btn btn-secondary" disabled={busy || !next}>
        {busy ? 'Đang cập nhật…' : 'Xác nhận'}
      </button>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
    </form>
  )
}
