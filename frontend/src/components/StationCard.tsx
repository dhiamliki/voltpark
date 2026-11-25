import { Link } from 'react-router'
import { ArrowRight, EvCharger, MapPin, PlugZap } from 'lucide-react'
import type { Station } from '../api/voltpark'
import { CHARGER_LABELS, chargerTone, stationCounts } from '../lib/status'
import { ICON_STROKE, Pill, StatusDot } from './ui'

/** Fiche station : en-tete illustre, adresse, compteurs de bornes, liste des bornes, reservation. */
export function StationCard({ station, distance }: { station: Station; distance?: number | null }) {
  const counts = stationCounts(station)
  return (
    <article className="card station-card" aria-label={station.name}>
      <div className="station-card__art" aria-hidden>
        <span className="station-card__halo" />
        <EvCharger size={64} strokeWidth={1.25} className="station-card__icon" />
        <span className="station-card__pill-art">
          <Pill tone="accent">Ouvert · 24/7</Pill>
        </span>
      </div>

      <div className="station-card__body">
        <div className="station-card__heading">
          <h2>{station.name}</h2>
          <span className="station-card__pill-inline">
            <Pill tone="accent">Ouvert · 24/7</Pill>
          </span>
        </div>
        <p className="station-card__address">
          <MapPin size={16} strokeWidth={ICON_STROKE} aria-hidden />
          <span>
            {distance != null && <>{distance.toLocaleString('fr-FR', { maximumFractionDigits: 1 })} km · </>}
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

        <ul className="charger-list">
          {(station.chargers ?? []).map((charger) => (
            <li key={charger.id} className="charger-list__item">
              <StatusDot tone={chargerTone(charger.status)} />
              <span className="charger-list__id">{charger.id}</span>
              <span className="muted tabular">{charger.maxPowerKw} kW</span>
              <span className="charger-list__status">{CHARGER_LABELS[charger.status ?? ''] ?? charger.status}</span>
            </li>
          ))}
        </ul>

        <Link to={`/reservations?station=${station.id}`} className="btn btn--primary btn--block">
          Réserver une borne <ArrowRight size={16} strokeWidth={ICON_STROKE} />
        </Link>
      </div>
    </article>
  )
}
