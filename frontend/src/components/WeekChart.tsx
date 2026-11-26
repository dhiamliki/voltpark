import type { DayEnergy } from '../lib/stats'
import { formatKwh } from '../lib/format'

const BAR = 10
const GAP = 4
const HEIGHT = 60

/**
 * Barres de l'energie chargee par jour (SVG maison, barres couleur accent, aujourd'hui plus foncee).
 * Le SVG s'etire sur toute la largeur ; les jours sont ecrits en HTML pour garder une taille lisible.
 */
export function WeekChart({ days }: { days: DayEnergy[] }) {
  const max = Math.max(1, ...days.map((d) => d.energyKwh))
  const width = days.length * BAR + (days.length - 1) * GAP
  const total = days.reduce((sum, d) => sum + d.energyKwh, 0)

  return (
    <figure className="week-chart" aria-label={`Énergie des 7 derniers jours : ${formatKwh(total)} kWh`}>
      <svg viewBox={`0 0 ${width} ${HEIGHT}`} preserveAspectRatio="none" role="img" aria-hidden>
        {days.map((day, i) => {
          const h = day.energyKwh > 0 ? Math.max(3, (day.energyKwh / max) * HEIGHT) : 2
          const today = i === days.length - 1
          return (
            <rect
              key={day.date.toISOString()}
              className={`week-chart__bar ${day.energyKwh === 0 ? 'week-chart__bar--empty' : ''} ${today ? 'week-chart__bar--today' : ''}`}
              x={i * (BAR + GAP)}
              y={HEIGHT - h}
              width={BAR}
              height={h}
            >
              <title>{`${day.label} : ${formatKwh(day.energyKwh)} kWh`}</title>
            </rect>
          )
        })}
      </svg>
      <div className="week-chart__labels" aria-hidden>
        {days.map((day) => (
          <span key={day.date.toISOString()}>{day.label}</span>
        ))}
      </div>
    </figure>
  )
}
