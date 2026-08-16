const labels = {
  ACTIVE: 'Đang hoạt động', INACTIVE: 'Ngừng hoạt động', LOW: 'Thấp', MEDIUM: 'Trung bình', HIGH: 'Cao', CRITICAL: 'Nghiêm trọng',
  DRAFT: 'Nháp', ORDERED: 'Đã đặt', IN_TRANSIT: 'Đang vận chuyển', DELIVERED: 'Đã giao', CANCELLED: 'Đã hủy', DELAYED: 'Trễ',
  PENDING: 'Chờ xử lý', SHIPPING: 'Đang vận chuyển', ARRIVED: 'Đã đến', OPEN: 'Đang mở', INVESTIGATING: 'Đang điều tra', RESOLVED: 'Đã xử lý', CLOSED: 'Đã đóng', NEW: 'Mới', READ: 'Đã đọc',
  POSITIVE: 'Tích cực', NEGATIVE: 'Tiêu cực', NEUTRAL: 'Trung tính', NOT_ANALYZED: 'Chưa phân tích', RISK: 'Rủi ro', OPPORTUNITY_SIGNAL: 'Cơ hội',
}
export default function Badge({ value, fallback }) { if (!value) return <span className="badge badge--neutral">{fallback || '—'}</span>; const normalized = String(value).toUpperCase(); return <span className={`badge badge--${normalized.toLowerCase().replaceAll('_', '-')}`}>{labels[normalized] || value}</span> }
