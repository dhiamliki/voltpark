import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { History, Settings, Users } from 'lucide-react'
import { initials } from '../lib/format'
import { useDriver } from '../state/DriverContext'
import { ICON_STROKE } from './ui'

/** Avatar du compte (initiales) : nom et badge, Historique, Parametres et "Changer de compte". */
export function DriverMenu() {
  const { driver, selectDriver } = useDriver()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const close = (event: MouseEvent) => {
      if (ref.current && !ref.current.contains(event.target as Node)) setOpen(false)
    }
    const escape = (event: KeyboardEvent) => event.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', close)
    document.addEventListener('keydown', escape)
    return () => {
      document.removeEventListener('mousedown', close)
      document.removeEventListener('keydown', escape)
    }
  }, [open])

  return (
    <div className="driver-menu" ref={ref}>
      <button
        type="button"
        className="avatar"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={driver ? `Compte : ${driver.name}` : 'Compte'}
        onClick={() => setOpen((o) => !o)}
      >
        {initials(driver?.name)}
      </button>
      {open && (
        <div className="popover driver-menu__panel" role="menu">
          <div className="driver-menu__who">
            <span className="avatar avatar--small">{initials(driver?.name)}</span>
            <span className="driver-list__text">
              <strong>{driver?.name}</strong>
              <span className="muted">Badge {driver?.badgeId}</span>
            </span>
          </div>
          <Link to="/historique" role="menuitem" className="driver-menu__item" onClick={() => setOpen(false)}>
            <History size={16} strokeWidth={ICON_STROKE} aria-hidden /> Historique et factures
          </Link>
          <Link to="/parametres" role="menuitem" className="driver-menu__item" onClick={() => setOpen(false)}>
            <Settings size={16} strokeWidth={ICON_STROKE} aria-hidden /> Paramètres
          </Link>
          <button
            type="button"
            role="menuitem"
            className="driver-menu__item"
            onClick={() => {
              setOpen(false)
              selectDriver(null)
            }}
          >
            <Users size={16} strokeWidth={ICON_STROKE} aria-hidden /> Changer de compte
          </button>
        </div>
      )}
    </div>
  )
}
