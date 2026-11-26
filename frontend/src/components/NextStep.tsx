import { useEffect, useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { CalendarClock, ChevronRight, FileWarning, Hourglass, MapPin, Play, RotateCw, Zap } from 'lucide-react'
import { ApiError } from '../api/client'
import { voltpark, type Reservation, type Station } from '../api/voltpark'
import { distanceKm, formatKw, formatKwh, formatTime, shortName } from '../lib/format'
import { canStartReservation, nextReservation } from '../lib/reservations'
import { formatCountdown } from '../lib/simulation'
import { useStartSession } from '../lib/useStartSession'
import { useCharge } from '../state/ChargeContext'
import { StartErrorBanner } from './sessionParts'
import { ErrorBanner, ICON_STROKE } from './ui'

/** Rayon de "a proximite" quand la position est connue. */
const NEARBY_KM = 30

function useNow(intervalMs = 15_000) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), intervalMs)
    return () => clearInterval(timer)
  }, [intervalMs])
  return now
}

function Step({ icon, tone = 'default', title, children, actions }: {
  icon: ReactNode
  tone?: 'default' | 'live' | 'warning'
  title: ReactNode
  children?: ReactNode
  actions?: ReactNode
}) {
  return (
    <section className={`card next-step next-step--${tone}`} aria-label="Prochaine étape">
      <span className="next-step__icon" aria-hidden>
        {icon}
      </span>
      <div className="next-step__text">
        <p className="next-step__title">{title}</p>
        {children && <div className="muted next-step__detail">{children}</div>}
      </div>
      {actions && <div className="next-step__actions">{actions}</div>}
    </section>
  )
}

/**
 * Carte "prochaine etape" en haut de la Carte, toujours une seule chose a faire :
 * charge en cours -> mini suivi ; demarrage en attente -> reessayer ; reservation proche -> demarrer ;
 * facture en attente des mesures -> recalculer ; reservation plus tard -> compte a rebours ;
 * sinon -> trouver une borne disponible.
 */
export function NextStep({ stations, userPosition }: { stations: Station[]; userPosition: [number, number] | null }) {
  const { current, live, reservations, invoices, stationOf, refresh } = useCharge()
  const { start, retry, busyKey, error } = useStartSession()
  const [invoiceError, setInvoiceError] = useState<ApiError | null>(null)
  const [recalculating, setRecalculating] = useState(false)
  const now = useNow()

  if (current?.status === 'ACTIVE') {
    const latest = live.latest
    return (
      <Step
        tone="live"
        icon={<Zap size={20} strokeWidth={2} fill="currentColor" />}
        title={<>Charge en cours · {shortName(stationOf(current.chargerId)?.name) || current.chargerId}</>}
        actions={
          <Link to="/charge" className="btn btn--primary btn--small">
            Voir ma charge <ChevronRight size={16} strokeWidth={ICON_STROKE} aria-hidden />
          </Link>
        }
      >
        <span className="tabular">
          {latest
            ? `${latest.socPercent ?? '—'} % · ${formatKw(latest.powerKw)} kW · ${formatKwh(latest.energyKwh)} kWh`
            : `Borne ${current.chargerId} · connexion aux mesures…`}
        </span>
      </Step>
    )
  }

  if (current?.status === 'PENDING_START') {
    return (
      <Step
        tone="warning"
        icon={<Hourglass size={20} strokeWidth={ICON_STROKE} />}
        title="Démarrage en attente"
        actions={
          <>
            <button type="button" className="btn btn--primary btn--small" onClick={() => retry(current)} disabled={busyKey !== null}>
              <RotateCw size={16} strokeWidth={ICON_STROKE} aria-hidden /> {busyKey ? 'Nouvel essai…' : 'Réessayer'}
            </button>
            <Link to="/charge" className="btn btn--ghost btn--small">
              Détails
            </Link>
          </>
        }
      >
        La borne {current.chargerId} n’a pas répondu. Elle reste gardée pour vous : réessayez dans un instant.
        <StartErrorBanner error={error} />
      </Step>
    )
  }

  const next = nextReservation(reservations, now)
  const pendingInvoice = invoices.find((i) => i.status === 'PENDING_ENERGY')

  if (next && canStartReservation(next, now)) {
    return <ReservationStep reservation={next} now={now} start={start} busyKey={busyKey} error={error} stationOf={stationOf} />
  }

  if (pendingInvoice) {
    const recalculate = async () => {
      if (pendingInvoice.id == null) return
      setRecalculating(true)
      setInvoiceError(null)
      try {
        await voltpark.recalculateInvoice(pendingInvoice.id)
        await refresh()
      } catch (e) {
        setInvoiceError(e instanceof ApiError ? e : new ApiError(0, String(e)))
      } finally {
        setRecalculating(false)
      }
    }
    return (
      <Step
        tone="warning"
        icon={<FileWarning size={20} strokeWidth={ICON_STROKE} />}
        title={`Facture n° ${pendingInvoice.id} en attente des mesures`}
        actions={
          <>
            <button type="button" className="btn btn--primary btn--small" onClick={recalculate} disabled={recalculating}>
              <RotateCw size={16} strokeWidth={ICON_STROKE} aria-hidden /> {recalculating ? 'Calcul…' : 'Recalculer'}
            </button>
            <Link to={`/factures/${pendingInvoice.id}`} className="btn btn--ghost btn--small">
              Voir la facture
            </Link>
          </>
        }
      >
        Le service de mesure était indisponible à la fin de la charge : recalculez le montant maintenant.
        <ErrorBanner error={invoiceError} title="Le recalcul a échoué" />
      </Step>
    )
  }

  if (next) {
    return <ReservationStep reservation={next} now={now} start={start} busyKey={busyKey} error={error} stationOf={stationOf} />
  }

  // aucune action en cours : trouver une borne
  const withAvailable = stations.filter((s) => s.chargers?.some((c) => c.status === 'AVAILABLE'))
  const nearby = userPosition
    ? withAvailable.filter(
        (s) =>
          s.latitude != null &&
          s.longitude != null &&
          distanceKm({ lat: userPosition[0], lng: userPosition[1] }, { lat: s.latitude, lng: s.longitude }) <= NEARBY_KM,
      )
    : withAvailable
  const chargers = nearby.reduce((sum, s) => sum + (s.chargers ?? []).filter((c) => c.status === 'AVAILABLE').length, 0)
  return (
    <Step
      icon={<MapPin size={20} strokeWidth={ICON_STROKE} />}
      title="Trouvez une borne disponible"
      actions={
        <Link to="/reservations" className="btn btn--ghost btn--small">
          Réserver pour plus tard
        </Link>
      }
    >
      <span className="tabular">
        {chargers} borne{chargers > 1 ? 's' : ''} disponible{chargers > 1 ? 's' : ''}{' '}
        {userPosition ? `à moins de ${NEARBY_KM} km` : `dans ${nearby.length} station${nearby.length > 1 ? 's' : ''} sur la carte`}
      </span>{' '}
      · touchez une épingle, puis « Charger maintenant ».
    </Step>
  )
}

function ReservationStep({ reservation, now, start, busyKey, error, stationOf }: {
  reservation: Reservation
  now: number
  start: (chargerId: string, reservationId?: number) => Promise<unknown>
  busyKey: string | null
  error: ApiError | null
  stationOf: (chargerId?: string) => Station | undefined
}) {
  const startsIn = new Date(reservation.startTime ?? 0).getTime() - now
  const startable = canStartReservation(reservation, now)
  const station = shortName(stationOf(reservation.chargerId)?.name)
  return (
    <Step
      icon={<CalendarClock size={20} strokeWidth={ICON_STROKE} />}
      title={
        <>
          Réservation à {formatTime(reservation.startTime)} · {station || reservation.chargerId}
        </>
      }
      actions={
        startable ? (
          <button
            type="button"
            className="btn btn--primary btn--small"
            onClick={() => start(reservation.chargerId ?? '', reservation.id)}
            disabled={busyKey !== null}
          >
            <Play size={14} strokeWidth={ICON_STROKE} fill="currentColor" aria-hidden />
            {busyKey === `reservation-${reservation.id}` ? 'Démarrage…' : 'Démarrer'}
          </button>
        ) : (
          <Link to="/reservations" className="btn btn--ghost btn--small">
            Mes réservations
          </Link>
        )
      }
    >
      <span className="tabular">
        Borne {reservation.chargerId} · {startsIn > 0 ? formatCountdown(startsIn) : 'créneau en cours'}
        {!startable && ' · démarrage possible 15 min avant'}
      </span>
      <StartErrorBanner error={error} />
    </Step>
  )
}
