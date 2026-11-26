import { useCallback, useMemo, useState } from 'react'
import { Link } from 'react-router'
import { ChevronRight, MapPinOff, SearchX, Zap } from 'lucide-react'
import { voltpark, type Station } from '../api/voltpark'
import { LiveSessionPanel } from '../components/LiveSessionPanel'
import { NearbyStations } from '../components/NearbyStations'
import { Overview } from '../components/Overview'
import { StationCard } from '../components/StationCard'
import { StationMap } from '../components/StationMap'
import { Banner, CardSkeleton, EmptyState, ErrorBanner, ICON_STROKE, Pill, Skeleton } from '../components/ui'
import { cityOf, distanceKm } from '../lib/format'
import { matchesFilter, STATION_FILTERS, type StationFilter } from '../lib/regions'
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
                  {cityOf(station.address)} · {counts.available}/{counts.total} disponibles
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

/** Filtres rapides au-dessus de la carte ; "Autoroute A1" cadre aussi la carte sur le corridor. */
function FilterChips({ value, onChange }: { value: StationFilter; onChange: (filter: StationFilter) => void }) {
  return (
    <div className="map-filters" role="group" aria-label="Filtrer les stations">
      {STATION_FILTERS.map((filter) => (
        <button
          key={filter.id}
          type="button"
          className={`filter-chip ${value === filter.id ? 'is-active' : ''}`}
          aria-pressed={value === filter.id}
          onClick={() => onChange(filter.id)}
        >
          {filter.label}
        </button>
      ))}
    </div>
  )
}

/** Panneau du bas : session en direct, session en attente, ou vue d'ensemble. */
function BottomPanel() {
  const { driver } = useDriver()
  const driverId = driver?.id
  const sessions = useLoad(driverId ? () => voltpark.sessions(driverId) : null, `sessions-${driverId}`)
  const invoices = useLoad(driverId ? () => voltpark.invoices(driverId) : null, `invoices-${driverId}`)
  const reservations = useLoad(driverId ? () => voltpark.reservations(driverId) : null, `reservations-${driverId}`)

  if (!driverId) {
    return (
      <Banner title="Choisissez un conducteur" actions={<Link to="/parametres" className="btn btn--primary btn--small">Choisir</Link>}>
        Sélectionnez votre profil pour voir votre consommation, vos sessions et vos factures.
      </Banner>
    )
  }
  if (!sessions.data || !invoices.data) {
    return (
      <div className="overview">
        <CardSkeleton lines={2} />
        <CardSkeleton lines={2} />
        <CardSkeleton lines={2} />
      </div>
    )
  }
  const active = sessions.data.find((s) => s.status === 'ACTIVE')
  const pending = sessions.data.find((s) => s.status === 'PENDING_START')
  if (active) return <LiveSessionPanel session={active} />
  return (
    <>
      {pending && (
        <Banner title="Une session attend de démarrer" actions={<Link to="/sessions" className="btn btn--primary btn--small">Voir la session</Link>}>
          La passerelle des bornes était injoignable au démarrage sur {pending.chargerId}.
        </Banner>
      )}
      <Overview sessions={sessions.data} invoices={invoices.data} reservations={reservations.data ?? []} />
    </>
  )
}

export function MapPage() {
  const { query } = useSearch()
  const { data: stations, error, loading, reload } = useLoad((signal) => voltpark.stations(signal), 'stations')
  const [selectedId, setSelectedId] = useState<number | undefined>()
  const [userPosition, setUserPosition] = useState<[number, number] | null>(null)
  const [tilesFailed, setTilesFailed] = useState(false)
  const [filter, setFilter] = useState<StationFilter>('all')

  const filtered = useMemo(() => {
    const q = normalize(query.trim())
    return (stations ?? [])
      .filter((s) => matchesFilter(s, filter))
      .filter((s) => !q || normalize(`${s.name} ${s.address ?? ''} ${s.operator ?? ''}`).includes(q))
  }, [stations, query, filter])

  const selected = filtered.find((s) => s.id === selectedId) ?? filtered[0]
  const distance =
    userPosition && selected?.latitude != null && selected.longitude != null
      ? distanceKm({ lat: userPosition[0], lng: userPosition[1] }, { lat: selected.latitude, lng: selected.longitude })
      : null

  const select = useCallback((station: Station) => setSelectedId(station.id), [])
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
      <div className="map-page__map">
        <FilterChips value={filter} onChange={setFilter} />
        {loading && !stations ? (
          <Skeleton height="100%" radius={16} />
        ) : filtered.length === 0 ? (
          <div className="card map-page__empty">
            <EmptyState
              icon={<SearchX size={24} strokeWidth={ICON_STROKE} />}
              title="Aucune station trouvée"
              text={
                query
                  ? `Aucune station ne correspond à « ${query} ».`
                  : filter !== 'all'
                    ? 'Aucune station dans cette zone.'
                    : 'Aucune station n’est encore enregistrée.'
              }
            />
          </div>
        ) : tilesFailed ? (
          <div className="card map-page__fallback">
            <Banner title="Carte indisponible">
              Les fonds de carte n’ont pas pu être chargés (pas d’accès à Internet). Voici la liste des stations.
            </Banner>
            <StationList stations={filtered} selectedId={selected?.id} onSelect={select} />
          </div>
        ) : (
          <StationMap
            stations={filtered}
            selectedId={selected?.id}
            popupId={selectedId}
            onSelect={select}
            onOpen={select}
            onLocated={setUserPosition}
            userPosition={userPosition}
            onTilesFailed={onTilesFailed}
            fitCorridor={filter === 'a1'}
          />
        )}
        {query && filtered.length > 0 && (
          <p className="map-page__count">
            <Pill tone="neutral">{filtered.length}</Pill> station{filtered.length > 1 ? 's' : ''} pour « {query} »
          </p>
        )}
      </div>

      <aside className="map-page__side" aria-label="Station sélectionnée">
        {loading && !stations ? (
          <CardSkeleton lines={6} />
        ) : selected ? (
          <>
            <StationCard station={selected} distance={distance} />
            <NearbyStations stations={filtered} selectedId={selected.id} userPosition={userPosition} onSelect={select} />
          </>
        ) : (
          <div className="card">
            <EmptyState icon={<MapPinOff size={24} strokeWidth={ICON_STROKE} />} title="Choisissez une station" text="Touchez une épingle sur la carte." />
          </div>
        )}
      </aside>

      <div className="map-page__bottom">
        <BottomPanel />
      </div>
    </div>
  )
}
