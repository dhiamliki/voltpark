import a1Path from '../data/a1-path.json'
import type { Station } from '../api/voltpark'
import { distanceKm } from './format'

/**
 * Trace approximatif de l'autoroute A1 Tunis - Sfax (copie de data/a1-path.json) :
 * dessine sur la carte, il sert aussi a cadrer la carte sur le corridor.
 */
export const A1_PATH: [number, number][] = a1Path.points.map((p) => [p.lat, p.lon])

export type StationFilter = 'all' | 'a1' | 'tunis' | 'sfax'

export const STATION_FILTERS: Array<{ id: StationFilter; label: string }> = [
  { id: 'all', label: 'Toutes' },
  { id: 'a1', label: 'Autoroute A1' },
  { id: 'tunis', label: 'Grand Tunis' },
  { id: 'sfax', label: 'Sfax' },
]

/** Zones des filtres : un cercle autour du centre-ville (Grand Tunis : Tunis, Ariana, Ben Arous, La Marsa). */
const AREAS = {
  tunis: { center: { lat: 36.8065, lng: 10.1815 }, radiusKm: 25 },
  sfax: { center: { lat: 34.7406, lng: 10.7603 }, radiusKm: 30 },
}

export const isOnA1 = (station: Station) => station.corridor === 'A1'

export function matchesFilter(station: Station, filter: StationFilter): boolean {
  if (filter === 'all') return true
  if (filter === 'a1') return isOnA1(station)
  if (station.latitude == null || station.longitude == null) return false
  const area = AREAS[filter]
  return distanceKm(area.center, { lat: station.latitude, lng: station.longitude }) <= area.radiusKm
}
