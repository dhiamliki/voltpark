import { Link } from 'react-router'
import { ArrowRight, EvCharger, Gauge, MapPin, Moon, PlugZap, Sun } from 'lucide-react'
import type { Station } from '../api/voltpark'
import { connectorOf, formatKw, formatMoney, shortName } from '../lib/format'
import { CHARGER_LABELS, chargerPillTone, maxPower, stationCounts } from '../lib/status'
import { ICON_STROKE, Pill, StatusDot } from './ui'

/**
 * Fiche station : en-tete compact (illustration, horaires, tarifs, puissance max), adresse,
 * compteurs de bornes, liste des bornes et bouton de reservation.
 */
export function StationCard({ station, distance }: { station: Station; distance?: number | null }) {
  const counts = stationCounts(station)
  return (
    <article className="card station-card" aria-label={station.name}>
      <div className="station-card__head">
        <span className="station-card__icon" aria-hidden>
          <EvCharger size={40} strokeWidth={1.4} />
        </span>
        <div className="station-card__chips">
          <Pill tone="accent" dot>
            Ouvert · 24/7
          </Pill>
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
          <span className="station-card__pill-inline">
            <Pill tone="accent">Ouvert · 24/7</Pill>
          </span>
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
          {(station.chargers ?? []).map((charger) => (
            <li key={charger.id} className="charger-list__item">
              <span className="charger-list__id">{charger.id}</span>
              <span className="muted tabular">{formatKw(charger.maxPowerKw)} kW</span>
              <span className="muted">{connectorOf(charger.maxPowerKw)}</span>
              <Pill tone={chargerPillTone(charger.status)}>{CHARGER_LABELS[charger.status ?? ''] ?? charger.status}</Pill>
            </li>
          ))}
        </ul>

        <Link to={`/reservations?station=${station.id}`} className="btn btn--primary btn--block station-card__cta">
          Réserver une borne <ArrowRight size={16} strokeWidth={ICON_STROKE} />
        </Link>
      </div>
    </article>
  )
}
