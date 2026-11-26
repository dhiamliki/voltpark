import { forwardRef } from 'react'
import { Link } from 'react-router'
import { Building2, CalendarPlus, EvCharger, Gauge, MapPin, Moon, Play, PlugZap, Sun } from 'lucide-react'
import type { Charger, Station } from '../api/voltpark'
import { connectorOf, formatKw, formatMoney, shortName } from '../lib/format'
import { isOnA1 } from '../lib/regions'
import { CHARGER_LABELS, maxPower, stationCounts } from '../lib/status'
import { ICON_STROKE, Pill, StatusDot } from './ui'

/** Bornes dans l'ordre de leur numero (CP-460375-1, CP-460375-2...). */
const sortedChargers = (station: Station) =>
  [...(station.chargers ?? [])].sort((a, b) => (a.id ?? '').localeCompare(b.id ?? '', 'fr', { numeric: true }))

const connectorName = (charger: Charger) => charger.connectorType ?? connectorOf(charger.maxPowerKw)

/** Connecteurs de la station regroupes par type et puissance, ex. "CCS · 50 kW ×2". */
function connectorSummary(station: Station) {
  const groups = new Map<string, { type: string; powerKw?: number; count: number }>()
  for (const charger of sortedChargers(station)) {
    const type = connectorName(charger)
    const key = `${type}|${charger.maxPowerKw}`
    const group = groups.get(key)
    if (group) group.count++
    else groups.set(key, { type, powerKw: charger.maxPowerKw, count: 1 })
  }
  return [...groups.values()]
}

/** Pourquoi une borne ne peut pas charger maintenant. */
function unavailableReason(charger: Charger): string {
  if (charger.status === 'OCCUPIED' || charger.status === 'CHARGING') return 'Occupée : un véhicule charge'
  if (charger.status === 'FAULTED') return 'Hors service : en panne'
  return 'Hors service'
}

/**
 * Fiche station : corridor, tarifs, puissance max, adresse, operateur et connecteurs reels
 * (Open Charge Map), puis la liste des bornes. Chaque borne libre a son bouton "Charger maintenant" ;
 * les autres sont grisees avec la raison. "Reserver pour plus tard" est un lien secondaire.
 */
export const StationCard = forwardRef<HTMLElement, {
  station: Station
  distance?: number | null
  onChargeNow: (charger: Charger) => void
  /** le conducteur a deja une charge en cours : pas de second demarrage */
  charging?: boolean
  /** borne de la charge en cours du conducteur (affichee "Votre charge") */
  myChargerId?: string
}>(function StationCard({ station, distance, onChargeNow, charging = false, myChargerId }, ref) {
  const counts = stationCounts(station)
  const onA1 = isOnA1(station)
  return (
    <article className="card station-card" aria-labelledby={`station-${station.id}`} ref={ref} tabIndex={-1}>
      <div className="station-card__head">
        <span className="station-card__icon" aria-hidden>
          <EvCharger size={40} strokeWidth={1.4} />
        </span>
        <div className="station-card__chips">
          {onA1 && (
            <Pill tone="accent" dot>
              Autoroute A1
            </Pill>
          )}
          <span className="chip" title="Prix en heures pleines">
            <Sun size={14} strokeWidth={ICON_STROKE} aria-hidden /> HP {formatMoney(station.peakPricePerKwh)}/kWh
          </span>
          <span className="chip" title="Prix en heures creuses">
            <Moon size={14} strokeWidth={ICON_STROKE} aria-hidden /> HC {formatMoney(station.offPeakPricePerKwh)}/kWh
          </span>
          <span className="chip">
            <Gauge size={14} strokeWidth={ICON_STROKE} aria-hidden /> Jusqu’à {formatKw(maxPower(station))} kW
          </span>
        </div>
      </div>

      <div className="station-card__body">
        <div className="station-card__heading">
          <h2 id={`station-${station.id}`}>{shortName(station.name)}</h2>
          {onA1 && (
            <span className="station-card__pill-inline">
              <Pill tone="accent">Autoroute A1</Pill>
            </span>
          )}
        </div>
        <p className="station-card__address">
          <MapPin size={16} strokeWidth={ICON_STROKE} aria-hidden />
          <span>
            {distance != null && (
              <strong className="tabular">{distance.toLocaleString('fr-FR', { maximumFractionDigits: 1 })} km · </strong>
            )}
            {station.address}
          </span>
        </p>
        <p className="station-card__operator">
          <Building2 size={16} strokeWidth={ICON_STROKE} aria-hidden />
          <span>{station.operator ?? 'Opérateur non renseigné'}</span>
        </p>
        <ul className="station-card__connectors" aria-label="Connecteurs">
          {connectorSummary(station).map((c) => (
            <li key={`${c.type}-${c.powerKw}`} className="chip">
              <PlugZap size={14} strokeWidth={ICON_STROKE} aria-hidden />
              {c.type} · {formatKw(c.powerKw)} kW{c.count > 1 ? ` ×${c.count}` : ''}
            </li>
          ))}
        </ul>

        <p className="station-card__count">
          <StatusDot tone={counts.available > 0 ? 'available' : 'occupied'} />
          <span className="tabular">
            {counts.available} borne{counts.available > 1 ? 's' : ''} disponible{counts.available > 1 ? 's' : ''} sur {counts.total}
          </span>
        </p>

        <ul className="charger-rows" aria-label="Bornes de la station">
          {sortedChargers(station).map((charger) => {
            const available = charger.status === 'AVAILABLE'
            if (charger.id === myChargerId) {
              return (
                <li key={charger.id} className="charger-row charger-row--mine">
                  <span className="charger-row__info">
                    <strong>
                      {connectorName(charger)} · <span className="tabular">{formatKw(charger.maxPowerKw)} kW</span>
                    </strong>
                    <span className="muted">{charger.id} · votre charge en cours</span>
                  </span>
                  <Link to="/charge" className="btn btn--ghost btn--small">
                    Voir ma charge
                  </Link>
                </li>
              )
            }
            return (
              <li key={charger.id} className={`charger-row ${available ? '' : 'is-unavailable'}`}>
                <span className="charger-row__info">
                  <strong>
                    {connectorName(charger)} · <span className="tabular">{formatKw(charger.maxPowerKw)} kW</span>
                  </strong>
                  <span className="muted">
                    {charger.id}
                    {!available && <> · {unavailableReason(charger)}</>}
                  </span>
                </span>
                {available ? (
                  <button
                    type="button"
                    className="btn btn--primary btn--small charger-row__go"
                    onClick={() => onChargeNow(charger)}
                    disabled={charging}
                    title={charging ? 'Vous avez déjà une charge en cours' : undefined}
                    aria-label={`Charger maintenant sur la borne ${charger.id}`}
                  >
                    <Play size={14} strokeWidth={ICON_STROKE} fill="currentColor" aria-hidden /> Charger maintenant
                  </button>
                ) : (
                  <Pill tone={charger.status === 'OCCUPIED' || charger.status === 'CHARGING' ? 'neutral' : 'danger'}>
                    {CHARGER_LABELS[charger.status ?? ''] ?? charger.status}
                  </Pill>
                )}
              </li>
            )
          })}
        </ul>
        {charging && <p className="muted station-card__hint">Une charge est déjà en cours : arrêtez-la pour en démarrer une autre.</p>}

        <Link to={`/reservations?station=${station.id}`} className="link-button station-card__later">
          <CalendarPlus size={16} strokeWidth={ICON_STROKE} aria-hidden /> Réserver pour plus tard
        </Link>
        <p className="station-card__note">Localisation réelle · disponibilité simulée</p>
      </div>
    </article>
  )
})
