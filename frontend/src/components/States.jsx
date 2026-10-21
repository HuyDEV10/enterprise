import { AlertCircle, Inbox } from 'lucide-react'
export function LoadingState({ text = 'Đang tải dữ liệu…' }) {
  return (
    <div className="state-box" role="status">
      <span className="spinner" />
      <strong>{text}</strong>
      <span>Chỉ một chút nữa thôi.</span>
    </div>
  )
}
export function ErrorState({ message, onRetry }) {
  return (
    <div className="state-box state-box--error" role="alert">
      <span className="state-icon">
        <AlertCircle size={25} />
      </span>
      <strong>Chưa thể tải dữ liệu</strong>
      <span>{message}</span>
      {onRetry && (
        <button className="btn btn-secondary" onClick={onRetry}>
          Thử lại
        </button>
      )}
    </div>
  )
}
export function EmptyState({ title = 'Chưa có dữ liệu', description, action }) {
  return (
    <div className="state-box state-box--empty">
      <span className="state-icon">
        <Inbox size={26} strokeWidth={1.5} />
      </span>
      <strong>{title}</strong>
      <span>
        {description || 'Dữ liệu sẽ xuất hiện tại đây khi được cập nhật.'}
      </span>
      {action}
    </div>
  )
}
