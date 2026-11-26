import { Navigation } from 'lucide-react'
import type { Station } from '../api/voltpark'
import { cityOf, distanceKm, formatKw, shortName } from '../lib/format'
import { maxPower, stationCounts } from '../lib/status'
import { ICON_STROKE, Pill } from './ui'

/**
 * Autres stations, triees par distance si l'utilisateur s'est localise,
 * sinon par nombre de bornes disponibles. Un clic selectionne la station sur la carte.
 */
export function NearbyStations({ stations, selectedId, userPosition, onSelect }: {
  stations: Station[]
  selectedId?: number
  userPosition: [number, number] | null
  onSelect: (station: Station) => void
}) {
  const distanceOf = (s: Station) =>
    userPosition && s.latitude != null && s.longitude != null
      ? distanceKm({ lat: userPosition[0], lng: userPosition[1] }, { lat: s.latitude, lng: s.longitude })
      : null

  const others = stations
    .filter((s) => s.id !== selectedId)
    .map((s) => ({ station: s, distance: distanceOf(s), counts: stationCounts(s) }))
    .sort((a, b) =>
      a.distance != null && b.distance != null
        ? a.distance - b.distance
        : b.counts.available - a.counts.available || b.counts.total - a.counts.total,
    )

  if (others.length === 0) return null

  return (
    <section className="card nearby" aria-label="Stations à proximité">
      <h3 className="nearby__title">
        <Navigation size={16} strokeWidth={ICON_STROKE} aria-hidden />
        Stations à proximité
        <span className="muted nearby__sort">{userPosition ? 'par distance' : 'par disponibilité'}</span>
      </h3>
      <ul className="nearby__list">
        {others.map(({ station, distance, counts }) => (
          <li key={station.id}>
            <button type="button" className="nearby__item" onClick={() => onSelect(station)}>
              <span className="nearby__text">
                <strong>{shortName(station.name)}</strong>
                <span className="muted">
                  {distance != null && <span className="tabular">{distance.toLocaleString('fr-FR', { maximumFractionDigits: 1 })} km · </span>}
                  {cityOf(station.address)} · <span className="tabular">{formatKw(maxPower(station))} kW max</span>
                </span>
              </span>
              <Pill tone={counts.available > 0 ? 'success' : 'neutral'}>
                <span className="tabular">
                  {counts.available}/{counts.total}
                </span>{' '}
                disponible{counts.available > 1 ? 's' : ''}
              </Pill>
            </button>
          </li>
        ))}
      </ul>
    </section>
  )
}
