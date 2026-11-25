import { useCallback, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router'
import { ChevronRight, MapPinOff, SearchX, Zap } from 'lucide-react'
import { voltpark, type Station } from '../api/voltpark'
import { LiveSessionPanel } from '../components/LiveSessionPanel'
import { StationCard } from '../components/StationCard'
import { StationMap } from '../components/StationMap'
import { Banner, CardSkeleton, EmptyState, ErrorBanner, ICON_STROKE, Pill, Skeleton } from '../components/ui'
import { cityOf, distanceKm } from '../lib/format'
import { normalize, stationCounts } from '../lib/status'
import { useLoad } from '../lib/useLoad'
import { useDriver } from '../state/DriverContext'
import { useSearch } from '../state/SearchContext'

/** Liste des stations : remplace la carte quand les tuiles ne peuvent pas etre chargees (hors ligne). */
function StationList({ stations, selectedId, onSelect }: { stations: Station[]; selectedId?: number; onSelect: (s: Station) => void }) {
  return (
    <ul className="station-list">
      {stations.map((station) => {
        const counts = stationCounts(station)
        return (
          <li key={station.id}>
            <button
              type="button"
              className={`station-list__item ${station.id === selectedId ? 'is-selected' : ''}`}
              onClick={() => onSelect(station)}
            >
              <span className="station-list__pin">
                <Zap size={16} strokeWidth={2} fill="currentColor" aria-hidden />
              </span>
              <span className="station-list__text">
                <strong>{station.name}</strong>
                <span className="muted">
                  {cityOf(station.address)} · {counts.available} disponible{counts.available > 1 ? 's' : ''}
                </span>
              </span>
              <ChevronRight size={18} strokeWidth={ICON_STROKE} aria-hidden />
            </button>
          </li>
        )
      })}
    </ul>
  )
}

/** Session en cours du conducteur, affichee sous la carte comme dans la maquette. */
function CurrentSession() {
  const { driver } = useDriver()
  const driverId = driver?.id
  const { data: sessions } = useLoad(driverId ? () => voltpark.sessions(driverId) : null, `sessions-${driverId}`)
  if (!driverId || !sessions) return null
  const active = sessions.find((s) => s.status === 'ACTIVE')
  const pending = sessions.find((s) => s.status === 'PENDING_START')
  if (active) return <LiveSessionPanel session={active} />
  if (pending) {
    return (
      <Banner title="Une session attend de démarrer" actions={<Link to="/sessions" className="btn btn--primary btn--small">Voir la session</Link>}>
        La passerelle des bornes était injoignable au démarrage sur {pending.chargerId}.
      </Banner>
    )
  }
  return (
    <section className="card idle-session">
      <span className="idle-session__icon">
        <Zap size={20} strokeWidth={ICON_STROKE} aria-hidden />
      </span>
      <div>
        <p className="idle-session__title">Aucune charge en cours</p>
        <p className="muted">Réservez une borne, puis démarrez la charge depuis vos réservations.</p>
      </div>
      <Link to="/reservations" className="btn btn--ghost btn--small">
        Mes réservations
      </Link>
    </section>
  )
}

export function MapPage() {
  const { query } = useSearch()
  const { data: stations, error, loading, reload } = useLoad((signal) => voltpark.stations(signal), 'stations')
  const [selectedId, setSelectedId] = useState<number | undefined>()
  const [userPosition, setUserPosition] = useState<[number, number] | null>(null)
  const [tilesFailed, setTilesFailed] = useState(false)
  const cardRef = useRef<HTMLDivElement>(null)

  const filtered = useMemo(() => {
    const q = normalize(query.trim())
    return (stations ?? []).filter((s) => !q || normalize(`${s.name} ${s.address ?? ''}`).includes(q))
  }, [stations, query])

  const selected = filtered.find((s) => s.id === selectedId) ?? filtered[0]
  const distance =
    userPosition && selected?.latitude != null && selected.longitude != null
      ? distanceKm({ lat: userPosition[0], lng: userPosition[1] }, { lat: selected.latitude, lng: selected.longitude })
      : null

  const open = useCallback((station: Station) => {
    setSelectedId(station.id)
    cardRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }, [])
  const onTilesFailed = useCallback(() => setTilesFailed(true), [])

  if (error && !stations) {
    return (
      <ErrorBanner
        error={error}
        title="Impossible de charger les stations"
        actions={<button type="button" className="btn btn--ghost btn--small" onClick={reload}>Réessayer</button>}
      />
    )
  }

  return (
    <div className="map-page">
      <div className="map-page__grid">
        <div className="map-page__map">
          {loading && !stations ? (
            <Skeleton height="100%" radius={16} />
          ) : filtered.length === 0 ? (
            <div className="card map-page__empty">
              <EmptyState
                icon={<SearchX size={24} strokeWidth={ICON_STROKE} />}
                title="Aucune station trouvée"
                text={query ? `Aucune station ne correspond à « ${query} ».` : 'Aucune station n’est encore enregistrée.'}
              />
            </div>
          ) : tilesFailed ? (
            <div className="card map-page__fallback">
              <Banner title="Carte indisponible">
                Les fonds de carte n’ont pas pu être chargés (pas d’accès à OpenStreetMap). Voici la liste des stations.
              </Banner>
              <StationList stations={filtered} selectedId={selected?.id} onSelect={open} />
            </div>
          ) : (
            <StationMap
              stations={filtered}
              selectedId={selected?.id}
              onSelect={(s) => setSelectedId(s.id)}
              onOpen={open}
              onLocated={setUserPosition}
              userPosition={userPosition}
              onTilesFailed={onTilesFailed}
            />
          )}
        </div>
        <div className="map-page__card" ref={cardRef}>
          {loading && !stations ? (
            <CardSkeleton lines={5} />
          ) : selected ? (
            <StationCard station={selected} distance={distance} />
          ) : (
            <div className="card">
              <EmptyState icon={<MapPinOff size={24} strokeWidth={ICON_STROKE} />} title="Choisissez une station" text="Touchez une épingle sur la carte." />
            </div>
          )}
        </div>
      </div>
      {filtered.length > 0 && query && (
        <p className="map-page__count muted">
          <Pill tone="neutral">{filtered.length}</Pill> station{filtered.length > 1 ? 's' : ''} pour « {query} »
        </p>
      )}
      <CurrentSession />
    </div>
  )
}
