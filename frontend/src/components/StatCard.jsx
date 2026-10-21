import { Activity, ArrowUpRight } from 'lucide-react'
export default function StatCard({
  label,
  value,
  hint,
  tone = 'default',
  icon: Icon = Activity,
}) {
  return (
    <article className={`stat-card stat-card--${tone}`}>
      <div className="stat-top">
        <span className="stat-label">{label}</span>
        <span className="stat-icon">
          <Icon size={19} strokeWidth={1.7} />
        </span>
      </div>
      <strong>{value ?? '—'}</strong>
      {hint && <small>{hint}</small>}
    </article>
  )
}
