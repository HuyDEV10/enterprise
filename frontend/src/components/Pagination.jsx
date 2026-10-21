import { ChevronLeft, ChevronRight } from 'lucide-react'
export default function Pagination({
  page,
  totalPages,
  totalElements,
  changePage,
}) {
  return (
    <div className="pagination">
      <span>{totalElements} kết quả · 20 mục mỗi trang</span>
      <div className="pagination-controls">
        <button
          className="btn btn-secondary"
          aria-label="Trang trước"
          disabled={page <= 0}
          onClick={() => changePage(page - 1)}
        >
          <ChevronLeft size={14} />
        </button>
        <span>
          Trang {totalPages ? page + 1 : 1} / {Math.max(1, totalPages)}
        </span>
        <button
          className="btn btn-secondary"
          aria-label="Trang sau"
          disabled={page + 1 >= totalPages}
          onClick={() => changePage(page + 1)}
        >
          <ChevronRight size={14} />
        </button>
      </div>
    </div>
  )
}
