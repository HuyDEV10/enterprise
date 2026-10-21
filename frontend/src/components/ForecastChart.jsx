import { useId } from 'react'
import { formatDate, formatNumber } from '../utils/formatters'
export default function ForecastChart({ rows, threshold = 0 }) {
  const gradient = useId().replaceAll(':', '')
  if (!rows.length) return null
  const values = rows.map((row) => Number(row.projectedInventory))
  const low = Math.min(0, ...values),
    high = Math.max(1, Number(threshold), ...values),
    range = high - low || 1
  const x = (index) => 24 + (index / Math.max(1, rows.length - 1)) * 852,
    y = (value) => 205 - ((value - low) / range) * 170
  const points = values
    .map((value, index) => `${x(index)},${y(value)}`)
    .join(' ')
  return (
    <figure className="forecast-chart">
      <svg
        viewBox="0 0 900 240"
        role="img"
        aria-label={`Tồn kho dự kiến từ ${formatDate(rows[0].date)} đến ${formatDate(rows.at(-1).date)}. Thấp nhất ${formatNumber(Math.min(...values))}, cao nhất ${formatNumber(Math.max(...values))}. Xem bảng dữ liệu bên dưới để biết từng ngày.`}
      >
        <defs>
          <linearGradient id={gradient} x1="0" y1="0" x2="0" y2="1">
            <stop stopColor="#72ad8c" stopOpacity=".23" />
            <stop offset="1" stopColor="#72ad8c" stopOpacity="0" />
          </linearGradient>
        </defs>
        {[0, 0.5, 1].map((t) => (
          <g key={t}>
            <line
              x1="24"
              x2="876"
              y1={y(low + range * t)}
              y2={y(low + range * t)}
              stroke="#edf1e9"
            />
            <text
              x="24"
              y={y(low + range * t) - 7}
              fontSize="10"
              fill="#9aa992"
            >
              {formatNumber(low + range * t)}
            </text>
          </g>
        ))}
        <polygon
          points={`24,215 ${points} 876,215`}
          fill={`url(#${gradient})`}
        />
        <line
          x1="24"
          x2="876"
          y1={y(Number(threshold))}
          y2={y(Number(threshold))}
          stroke="#d5a67b"
          strokeDasharray="5 5"
        />
        <polyline
          points={points}
          fill="none"
          stroke="#4f9670"
          strokeWidth="2.5"
          strokeLinejoin="round"
        />
        {values.map((value, index) => (
          <circle
            key={index}
            cx={x(index)}
            cy={y(value)}
            r="3"
            fill="#fff"
            stroke="#4f9670"
          >
            <title>
              {formatDate(rows[index].date)}: {formatNumber(value)}
            </title>
          </circle>
        ))}
      </svg>
      <div className="forecast-chart-labels">
        <span>{formatDate(rows[0].date)}</span>
        <span>{formatDate(rows.at(-1).date)}</span>
      </div>
      <figcaption className="forecast-chart-legend">
        <span>
          <i />
          Tồn kho dự kiến
        </span>
        <span className="threshold">
          <i />
          Ngưỡng cảnh báo: {formatNumber(threshold)}
        </span>
      </figcaption>
    </figure>
  )
}
