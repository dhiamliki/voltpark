/** Anneau de progression (charge de la batterie) avec un texte au centre. */
export function ProgressRing({ percent, label, size = 120, stroke = 9 }: {
  percent: number | null
  label?: string
  size?: number
  stroke?: number
}) {
  const radius = (size - stroke) / 2
  const circumference = 2 * Math.PI * radius
  const value = percent == null ? 0 : Math.min(100, Math.max(0, percent))
  return (
    <div className="ring" style={{ width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden>
        <circle className="ring__track" cx={size / 2} cy={size / 2} r={radius} strokeWidth={stroke} fill="none" />
        <circle
          className="ring__value"
          cx={size / 2}
          cy={size / 2}
          r={radius}
          strokeWidth={stroke}
          fill="none"
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - value / 100)}
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
      </svg>
      <div className="ring__center">
        <span className="ring__percent tabular" aria-label={`Batterie ${percent ?? '—'} %`}>
          {percent == null ? '—' : `${Math.round(value)}%`}
        </span>
        {label && <span className="ring__label">{label}</span>}
      </div>
    </div>
  )
}
