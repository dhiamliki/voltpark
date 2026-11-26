import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { ChevronRight, MapPinOff, SearchX, Zap } from 'lucide-react'
import type { Charger, Station } from '../api/voltpark'
import { ChargeSheet } from '../components/ChargeSheet'
import { NearbyStations } from '../components/NearbyStations'
import { NextStep } from '../components/NextStep'
import { Overview } from '../components/Overview'
import { StationCard } from '../components/StationCard'
import { StationMap } from '../components/StationMap'
import { Banner, CardSkeleton, EmptyState, ICON_STROKE, Pill, Skeleton } from '../components/ui'
import { cityOf, distanceKm } from '../lib/format'
import { matchesFilter, STATION_FILTERS, type StationFilter } from '../lib/regions'
import { normalize, stationCounts } from '../lib/status'
import { useCharge } from '../state/ChargeContext'
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

/** Panneau du bas : vue d'ensemble du mois (la charge en cours est dans "prochaine etape"). */
function BottomPanel() {
  const { sessions, invoices, loaded } = useCharge()
  if (!loaded) {
    return (
      <div className="overview">
        <CardSkeleton lines={2} />
        <CardSkeleton lines={2} />
        <CardSkeleton lines={2} />
      </div>
    )
  }
  return <Overview sessions={sessions} invoices={invoices} />
}

export function MapPage() {
  const { query } = useSearch()
  const { stations: allStations, loaded, current, refresh, stationOf: stationOfCharger } = useCharge()
  const stations = loaded ? allStations : undefined
  const loading = !loaded
  const [selectedId, setSelectedId] = useState<number | undefined>()
  const [sheet, setSheet] = useState<{ stationId?: number; chargerId?: string } | null>(null)
  const cardRef = useRef<HTMLElement>(null)
  const userSelected = useRef(false)
  const [userPosition, setUserPosition] = useState<[number, number] | null>(null)
  const [tilesFailed, setTilesFailed] = useState(false)
  const [filter, setFilter] = useState<StationFilter>('all')

  const filtered = useMemo(() => {
    const q = normalize(query.trim())
    return (stations ?? [])
      .filter((s) => matchesFilter(s, filter))
      .filter((s) => !q || normalize(`${s.name} ${s.address ?? ''} ${s.operator ?? ''}`).includes(q))
  }, [stations, query, filter])

  // par defaut : la station de la charge en cours, sinon la premiere de la liste
  const chargingStationId = current ? stationOfCharger(current.chargerId)?.id : undefined
  const selected =
    filtered.find((s) => s.id === selectedId) ?? filtered.find((s) => s.id === chargingStationId) ?? filtered[0]
  const distance =
    userPosition && selected?.latitude != null && selected.longitude != null
      ? distanceKm({ lat: userPosition[0], lng: userPosition[1] }, { lat: selected.latitude, lng: selected.longitude })
      : null

  const select = useCallback((station: Station) => {
    userSelected.current = true
    setSelectedId(station.id)
  }, [])
  const onTilesFailed = useCallback(() => setTilesFailed(true), [])

  // une epingle choisie : la fiche de la station vient a l'ecran et prend le focus
  useEffect(() => {
    if (!userSelected.current || selectedId == null) return
    userSelected.current = false
    const card = cardRef.current
    if (!card) return
    card.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    card.focus({ preventScroll: true })
  }, [selectedId])

  // la feuille garde des identifiants : elle affiche les donnees a jour des stations
  const sheetStation = sheet ? allStations.find((s) => s.id === sheet.stationId) : undefined
  const sheetCharger: Charger | undefined = sheetStation?.chargers?.find((c) => c.id === sheet?.chargerId)
  const closeSheet = useCallback(() => setSheet(null), [])
  const chooseAnother = useCallback(() => {
    setSheet(null)
    void refresh()
    cardRef.current?.focus()
  }, [refresh])

  return (
    <div className="map-page">
      <div className="map-page__next">
        {loaded && <NextStep stations={filtered} userPosition={userPosition} />}
      </div>
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
            <StationCard
              ref={cardRef}
              station={selected}
              distance={distance}
              charging={current != null}
              myChargerId={current?.chargerId}
              onChargeNow={(charger) => setSheet({ stationId: selected.id, chargerId: charger.id })}
            />
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

      {sheetStation && sheetCharger && (
        <ChargeSheet station={sheetStation} charger={sheetCharger} onClose={closeSheet} onChooseAnother={chooseAnother} />
      )}
    </div>
  )
}
