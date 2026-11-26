import { useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router'
import { Bell, FileWarning, Hourglass } from 'lucide-react'
import { voltpark } from '../api/voltpark'
import { formatDateTime } from '../lib/format'
import { useLoad } from '../lib/useLoad'
import { useDriver } from '../state/DriverContext'
import { ICON_STROKE } from './ui'

/**
 * Notifications du conducteur : sessions en attente de demarrage et factures en attente
 * des mesures d'energie. Recalculees a chaque changement de page.
 */
export function NotificationBell() {
  const { driver } = useDriver()
  const location = useLocation()
  const driverId = driver?.id
  const key = `${driverId}-${location.pathname}`
  const sessions = useLoad(driverId ? () => voltpark.sessions(driverId) : null, `bell-sessions-${key}`)
  const invoices = useLoad(driverId ? () => voltpark.invoices(driverId) : null, `bell-invoices-${key}`)
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const close = (event: MouseEvent) => ref.current && !ref.current.contains(event.target as Node) && setOpen(false)
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [open])

  const pendingStarts = (sessions.data ?? []).filter((s) => s.status === 'PENDING_START')
  const pendingInvoices = (invoices.data ?? []).filter((i) => i.status === 'PENDING_ENERGY')
  const count = pendingStarts.length + pendingInvoices.length

  return (
    <div className="bell" ref={ref}>
      <button
        type="button"
        className="icon-button bell__button"
        aria-label={count ? `Notifications : ${count}` : 'Notifications'}
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
      >
        <Bell size={20} strokeWidth={ICON_STROKE} />
        {count > 0 && <span className="bell__count tabular">{count}</span>}
      </button>
      {open && (
        <div className="popover bell__panel" role="dialog" aria-label="Notifications">
          <p className="popover__title">Notifications</p>
          {count === 0 ? (
            <p className="muted bell__empty">Rien à signaler : aucune session ni facture en attente.</p>
          ) : (
            <ul className="bell__list">
              {pendingStarts.map((s) => (
                <li key={s.id}>
                  <Link to="/sessions" className="bell__item" onClick={() => setOpen(false)}>
                    <Hourglass size={18} strokeWidth={ICON_STROKE} aria-hidden />
                    <span>
                      <strong>Démarrage en attente</strong>
                      <span className="muted">Borne {s.chargerId} · {formatDateTime(s.startedAt)}</span>
                    </span>
                  </Link>
                </li>
              ))}
              {pendingInvoices.map((i) => (
                <li key={i.id}>
                  <Link to={`/factures/${i.id}`} className="bell__item" onClick={() => setOpen(false)}>
                    <FileWarning size={18} strokeWidth={ICON_STROKE} aria-hidden />
                    <span>
                      <strong>Facture n° {i.id} à recalculer</strong>
                      <span className="muted">Mesures d’énergie en attente</span>
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
