import { useId } from 'react'
import { formatKw } from '../lib/format'

const WIDTH = 600
const HEIGHT = 100
const PADDING_Y = 6

/**
 * Courbe lissee (Catmull-Rom convertie en courbes de Bezier cubiques) passant par les points.
 * Exportee pour les tests.
 */
export function smoothPath(points: Array<[number, number]>): string {
  if (points.length === 0) return ''
  if (points.length === 1) return `M${points[0][0]},${points[0][1]}`
  let d = `M${points[0][0].toFixed(1)},${points[0][1].toFixed(1)}`
  for (let i = 0; i < points.length - 1; i++) {
    const p0 = points[i - 1] ?? points[i]
    const p1 = points[i]
    const p2 = points[i + 1]
    const p3 = points[i + 2] ?? p2
    const c1x = p1[0] + (p2[0] - p0[0]) / 6
    const c1y = p1[1] + (p2[1] - p0[1]) / 6
    const c2x = p2[0] - (p3[0] - p1[0]) / 6
    const c2y = p2[1] - (p3[1] - p1[1]) / 6
    d += ` C${c1x.toFixed(1)},${c1y.toFixed(1)} ${c2x.toFixed(1)},${c2y.toFixed(1)} ${p2[0].toFixed(1)},${p2[1].toFixed(1)}`
  }
  return d
}

/**
 * Graphique de puissance (kW) en aire : ligne couleur accent, remplissage tres pale.
 * Axe vertical de 0 au maximum observe ; les points remplissent toute la largeur.
 */
export function PowerChart({ points, capacity = 120 }: { points: number[]; capacity?: number }) {
  const gradientId = `power-fill-${useId().replace(/:/g, '')}`
  const max = Math.max(1, ...points)
  const top = Math.ceil(max * 1.15 * 10) / 10
  const step = WIDTH / Math.max(1, Math.min(capacity, Math.max(points.length, 2)) - 1)
  const coords: Array<[number, number]> = points.map((value, i) => [
    i * step,
    HEIGHT - PADDING_Y - (value / top) * (HEIGHT - 2 * PADDING_Y),
  ])
  const line = smoothPath(coords)
  const last = coords[coords.length - 1]
  const area = coords.length > 1 ? `${line} L${last[0].toFixed(1)},${HEIGHT} L0,${HEIGHT} Z` : ''

  return (
    <figure className="chart" aria-label={`Puissance de charge, maximum ${formatKw(max)} kW`}>
      <div className="chart__axis" aria-hidden>
        <span>{formatKw(top)} kW</span>
        <span>0 kW</span>
      </div>
      <div className="chart__plot">
        {points.length < 2 ? (
          <p className="chart__empty">Les mesures apparaîtront ici dans quelques secondes.</p>
        ) : (
          <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} preserveAspectRatio="none" role="img" aria-hidden>
            <defs>
              <linearGradient id={gradientId} x1="0" x2="0" y1="0" y2="1">
                <stop offset="0%" className="chart__fill-top" />
                <stop offset="100%" className="chart__fill-bottom" />
              </linearGradient>
            </defs>
            <path className="chart__area" d={area} fill={`url(#${gradientId})`} />
            <path className="chart__line" d={line} fill="none" vectorEffect="non-scaling-stroke" />
          </svg>
        )}
      </div>
    </figure>
  )
}
