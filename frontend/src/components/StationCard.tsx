import { Link } from 'react-router'
import { ArrowRight, Building2, EvCharger, Gauge, MapPin, Moon, PlugZap, Sun } from 'lucide-react'
import type { Charger, Station } from '../api/voltpark'
import { connectorOf, formatKw, formatMoney, shortName } from '../lib/format'
import { isOnA1 } from '../lib/regions'
import { CHARGER_LABELS, chargerPillTone, maxPower, stationCounts } from '../lib/status'
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

/**
 * Fiche station : en-tete compact (illustration, corridor, tarifs, puissance max), adresse,
 * operateur et connecteurs reels (Open Charge Map), compteurs et liste des bornes, bouton de
 * reservation. Disponibilite et prix sont simules.
 */
export function StationCard({ station, distance }: { station: Station; distance?: number | null }) {
  const counts = stationCounts(station)
  const onA1 = isOnA1(station)
  return (
    <article className="card station-card" aria-label={station.name}>
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
          <h2>{shortName(station.name)}</h2>
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

        <dl className="stats">
          <div className="stats__item stats__item--total">
            <dt>Total</dt>
            <dd className="stats__value">
              <PlugZap size={18} strokeWidth={ICON_STROKE} aria-hidden />
              <span className="tabular">{counts.total}</span>
            </dd>
          </div>
          <div className="stats__item">
            <dt>Disponibles</dt>
            <dd className="stats__value">
              <StatusDot tone="available" />
              <span className="tabular">{counts.available}</span>
            </dd>
          </div>
          <div className="stats__item">
            <dt>Occupées</dt>
            <dd className="stats__value">
              <StatusDot tone="occupied" />
              <span className="tabular">{counts.occupied}</span>
            </dd>
          </div>
        </dl>

        <ul className="charger-list" aria-label="Bornes de la station">
          {sortedChargers(station).map((charger) => (
            <li key={charger.id} className="charger-list__item">
              <span className="charger-list__id">{charger.id}</span>
              <span className="muted tabular">{formatKw(charger.maxPowerKw)} kW</span>
              <span className="muted">{connectorName(charger)}</span>
              <Pill tone={chargerPillTone(charger.status)}>{CHARGER_LABELS[charger.status ?? ''] ?? charger.status}</Pill>
            </li>
          ))}
        </ul>

        <Link to={`/reservations?station=${station.id}`} className="btn btn--primary btn--block station-card__cta">
          Réserver une borne <ArrowRight size={16} strokeWidth={ICON_STROKE} />
        </Link>
        <p className="station-card__note">Localisation réelle · disponibilité simulée</p>
      </div>
    </article>
  )
}
