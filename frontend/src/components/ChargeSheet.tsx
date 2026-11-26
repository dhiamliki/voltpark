import { useEffect, useRef } from 'react'
import { Link } from 'react-router'
import { BadgeCheck, Gauge, Moon, Play, PlugZap, RotateCw, Sun, X } from 'lucide-react'
import type { Charger, Station } from '../api/voltpark'
import { connectorOf, formatKw, formatMoney, shortName } from '../lib/format'
import { useStartSession } from '../lib/useStartSession'
import { useDriver } from '../state/DriverContext'
import { startErrorTitle, StartErrorBanner } from './sessionParts'
import { Banner, ICON_STROKE, Pill } from './ui'

/**
 * Confirmation avant de charger : station, borne, puissance, connecteur, prix et badge du conducteur.
 * "Demarrer la charge" envoie POST /sessions sans reservation, puis ouvre Ma charge.
 * Panneau lateral sur ordinateur, feuille du bas sur mobile. Les refus s'affichent dans la feuille,
 * avec l'action suivante (changer de compte, choisir une autre borne, reessayer).
 */
export function ChargeSheet({ station, charger, onClose, onChooseAnother }: {
  station: Station
  charger: Charger
  onClose: () => void
  /** refus sur la borne (occupee, reservee...) : fermer et revenir a la liste des bornes */
  onChooseAnother: () => void
}) {
  const { driver, selectDriver } = useDriver()
  const { start, retry, busyKey, error, pending } = useStartSession()
  const startButton = useRef<HTMLButtonElement>(null)
  const kind = error ? startErrorTitle(error).kind : null

  // focus sur l'action principale, Echap ferme, focus rendu a l'element d'origine
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    startButton.current?.focus()
    const onKey = (event: KeyboardEvent) => event.key === 'Escape' && onClose()
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('keydown', onKey)
      previous?.focus?.()
    }
  }, [onClose])

  const connector = charger.connectorType ?? connectorOf(charger.maxPowerKw)

  return (
    <div className="sheet-layer">
      <button type="button" className="sheet-backdrop" aria-label="Fermer" tabIndex={-1} onClick={onClose} />
      <section className="sheet" role="dialog" aria-modal="true" aria-labelledby="charge-sheet-title">
        <span className="sheet__handle" aria-hidden />
        <header className="sheet__header">
          <div>
            <p className="muted sheet__eyebrow">Charger maintenant</p>
            <h2 id="charge-sheet-title">{shortName(station.name)}</h2>
            <p className="muted">{station.address}</p>
          </div>
          <button type="button" className="icon-button" aria-label="Fermer" onClick={onClose}>
            <X size={20} strokeWidth={ICON_STROKE} />
          </button>
        </header>

        <dl className="sheet__facts">
          <div>
            <dt>Borne</dt>
            <dd>{charger.id}</dd>
          </div>
          <div>
            <dt>
              <Gauge size={14} strokeWidth={ICON_STROKE} aria-hidden /> Puissance
            </dt>
            <dd>{formatKw(charger.maxPowerKw)} kW</dd>
          </div>
          <div>
            <dt>
              <PlugZap size={14} strokeWidth={ICON_STROKE} aria-hidden /> Connecteur
            </dt>
            <dd>{connector}</dd>
          </div>
          <div>
            <dt>
              <Sun size={14} strokeWidth={ICON_STROKE} aria-hidden /> Heures pleines
            </dt>
            <dd>{formatMoney(station.peakPricePerKwh)}/kWh</dd>
          </div>
          <div>
            <dt>
              <Moon size={14} strokeWidth={ICON_STROKE} aria-hidden /> Heures creuses
            </dt>
            <dd>{formatMoney(station.offPeakPricePerKwh)}/kWh</dd>
          </div>
          <div>
            <dt>
              <BadgeCheck size={14} strokeWidth={ICON_STROKE} aria-hidden /> Badge
            </dt>
            <dd>
              {driver?.badgeId} <span className="muted">· {driver?.name}</span>
            </dd>
          </div>
        </dl>

        {pending ? (
          <Banner
            title="Démarrage en attente"
            actions={
              <>
                <button type="button" className="btn btn--primary btn--small" onClick={() => retry(pending)} disabled={busyKey !== null}>
                  <RotateCw size={16} strokeWidth={ICON_STROKE} aria-hidden />
                  {busyKey === `retry-${pending.id}` ? 'Nouvel essai…' : 'Réessayer'}
                </button>
                <Link to="/charge" className="btn btn--ghost btn--small" onClick={onClose}>
                  Voir Ma charge
                </Link>
              </>
            }
          >
            La borne n’a pas répondu : la passerelle des bornes est injoignable. La borne reste gardée pour vous,
            réessayez dans un instant.
          </Banner>
        ) : (
          <StartErrorBanner
            error={error}
            actions={
              kind === 'badge' ? (
                <button type="button" className="btn btn--primary btn--small" onClick={() => selectDriver(null)}>
                  Changer de compte
                </button>
              ) : kind === 'charger' ? (
                <button type="button" className="btn btn--primary btn--small" onClick={onChooseAnother}>
                  Choisir une autre borne
                </button>
              ) : undefined
            }
          />
        )}

        {!pending && (
          <div className="sheet__actions">
            <button
              ref={startButton}
              type="button"
              className="btn btn--primary btn--block sheet__start"
              onClick={() => start(charger.id ?? '')}
              disabled={busyKey !== null || kind === 'charger' || kind === 'badge'}
            >
              <Play size={16} strokeWidth={ICON_STROKE} fill="currentColor" aria-hidden />
              {busyKey === charger.id ? 'Démarrage…' : 'Démarrer la charge'}
            </button>
            <p className="muted sheet__note">
              <Pill tone="neutral">Sans réservation</Pill> La borne vérifie votre badge, puis la charge démarre.
            </p>
          </div>
        )}
      </section>
    </div>
  )
}
