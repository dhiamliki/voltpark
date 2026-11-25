import { useEffect, useRef, useState } from 'react'
import { Check, UserPlus } from 'lucide-react'
import { initials } from '../lib/format'
import { useDriver } from '../state/DriverContext'
import { DriverForm } from './DriverForm'
import { ICON_STROKE } from './ui'

/** Avatar du conducteur (initiales) et menu pour choisir ou creer un conducteur. */
export function DriverMenu() {
  const { drivers, driver, selectDriver } = useDriver()
  const [open, setOpen] = useState(false)
  const [creating, setCreating] = useState(false)
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
        aria-label={driver ? `Conducteur : ${driver.name}` : 'Choisir un conducteur'}
        onClick={() => {
          setOpen((o) => !o)
          setCreating(false)
        }}
      >
        {initials(driver?.name)}
      </button>
      {open && (
        <div className="popover driver-menu__panel" role="menu">
          {creating ? (
            <>
              <p className="popover__title">Nouveau conducteur</p>
              <DriverForm onCreated={() => setOpen(false)} onCancel={() => setCreating(false)} />
            </>
          ) : (
            <>
              <p className="popover__title">Conducteur</p>
              <ul className="driver-list">
                {drivers.map((d) => (
                  <li key={d.id}>
                    <button
                      type="button"
                      role="menuitemradio"
                      aria-checked={d.id === driver?.id}
                      className="driver-list__item"
                      onClick={() => {
                        selectDriver(d.id ?? null)
                        setOpen(false)
                      }}
                    >
                      <span className="avatar avatar--small">{initials(d.name)}</span>
                      <span className="driver-list__text">
                        <span>{d.name}</span>
                        <span className="muted">{d.badgeId}</span>
                      </span>
                      {d.id === driver?.id && <Check size={16} strokeWidth={ICON_STROKE} />}
                    </button>
                  </li>
                ))}
              </ul>
              <button type="button" className="btn btn--ghost btn--block" onClick={() => setCreating(true)}>
                <UserPlus size={16} strokeWidth={ICON_STROKE} /> Nouveau conducteur
              </button>
            </>
          )}
        </div>
      )}
    </div>
  )
}
