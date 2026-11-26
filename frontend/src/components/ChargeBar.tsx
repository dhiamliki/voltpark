import { Link, useLocation } from 'react-router'
import { ChevronRight, Hourglass, RotateCw, Zap } from 'lucide-react'
import { formatKw, shortName } from '../lib/format'
import { useStartSession } from '../lib/useStartSession'
import { useCharge } from '../state/ChargeContext'
import { ICON_STROKE } from './ui'

/**
 * Barre collee en haut de chaque page tant qu'une charge est en cours ou en attente de demarrage :
 * elle ouvre Ma charge. Masquee sur Ma charge, qui affiche deja tout.
 */
export function ChargeBar() {
  const { current, live, stationOf } = useCharge()
  const { pathname } = useLocation()
  const { retry, busy } = useStartSession()
  if (!current || pathname === '/charge') return null

  const station = shortName(stationOf(current.chargerId)?.name) || current.chargerId

  if (current.status === 'PENDING_START') {
    return (
      <div className="charge-bar charge-bar--pending" role="status">
        <Link to="/charge" className="charge-bar__link">
          <Hourglass size={18} strokeWidth={ICON_STROKE} aria-hidden />
          <span>
            <strong>Démarrage en attente</strong> · {station}
          </span>
        </Link>
        <button type="button" className="btn btn--small charge-bar__action" onClick={() => retry(current)} disabled={busy}>
          <RotateCw size={14} strokeWidth={ICON_STROKE} aria-hidden /> {busy ? 'Nouvel essai…' : 'Réessayer'}
        </button>
      </div>
    )
  }

  const latest = live.latest
  return (
    <div className="charge-bar" role="status">
      <Link to="/charge" className="charge-bar__link" aria-label={`Charge en cours à ${station} : voir Ma charge`}>
        <span className="charge-bar__pulse" aria-hidden>
          <Zap size={16} strokeWidth={2} fill="currentColor" />
        </span>
        <span className="charge-bar__text">
          <strong>Charge en cours</strong> · {station}
          {latest && (
            <span className="tabular">
              {' '}
              · {latest.socPercent ?? '—'} % · {formatKw(latest.powerKw)} kW
            </span>
          )}
        </span>
        <span className="charge-bar__open">
          Voir <ChevronRight size={16} strokeWidth={ICON_STROKE} aria-hidden />
        </span>
      </Link>
    </div>
  )
}
