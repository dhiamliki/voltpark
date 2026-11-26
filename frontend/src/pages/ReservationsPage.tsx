import { useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import { CalendarCheck, CalendarClock, CalendarPlus, CalendarX, Hourglass, Play, X, Zap } from 'lucide-react'
import { ApiError } from '../api/client'
import { voltpark, type Reservation, type Station } from '../api/voltpark'
import { StartErrorBanner, RequireDriver } from '../components/sessionParts'
import { Banner, CardSkeleton, EmptyState, ErrorBanner, ICON_STROKE, Metric, MetricStrip, PageHeader, Pill, StatusDot } from '../components/ui'
import { formatDateTime, formatTime, shortName } from '../lib/format'
import { pageMeta } from '../lib/pages'
import { formatCountdown } from '../lib/simulation'
import { CHARGER_LABELS, chargerTone, RESERVATION_LABELS } from '../lib/status'
import { useStartSession } from '../lib/useStartSession'
import { canStartReservation } from '../lib/reservations'
import { useCharge } from '../state/ChargeContext'

const DURATIONS = [
  { minutes: 30, label: '30 min' },
  { minutes: 60, label: '1 h' },
  { minutes: 90, label: '1 h 30' },
  { minutes: 120, label: '2 h' },
]

const pad = (n: number) => String(n).padStart(2, '0')

/** Prochain creneau : maintenant + 10 min, arrondi aux 5 minutes, en heure locale. */
function defaultSlot() {
  const date = new Date(Date.now() + 10 * 60_000)
  date.setMinutes(Math.ceil(date.getMinutes() / 5) * 5, 0, 0)
  return {
    day: `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`,
    time: `${pad(date.getHours())}:${pad(date.getMinutes())}`,
  }
}

function ReservationForm({ driverId, stations, onCreated }: { driverId: number; stations: Station[]; onCreated: () => void }) {
  const [params] = useSearchParams()
  const initialStation = Number(params.get('station')) || stations[0]?.id
  const [stationId, setStationId] = useState<number | undefined>(initialStation)
  const station = stations.find((s) => s.id === stationId)
  const firstAvailable = station?.chargers?.find((c) => c.status === 'AVAILABLE') ?? station?.chargers?.[0]
  const [chargerId, setChargerId] = useState<string>(params.get('charger') ?? firstAvailable?.id ?? '')
  const [slot] = useState(defaultSlot)
  const [day, setDay] = useState(slot.day)
  const [time, setTime] = useState(slot.time)
  const [duration, setDuration] = useState(60)
  const [error, setError] = useState<ApiError | null>(null)
  const [saving, setSaving] = useState(false)
  const [created, setCreated] = useState<Reservation | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setError(null)
    setCreated(null)
    try {
      const start = new Date(`${day}T${time}`)
      const end = new Date(start.getTime() + duration * 60_000)
      const reservation = await voltpark.createReservation({
        driverId,
        chargerId,
        startTime: start.toISOString(),
        endTime: end.toISOString(),
      })
      setCreated(reservation)
      onCreated()
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError(0, String(e)))
    } finally {
      setSaving(false)
    }
  }

  const fieldError = (...fields: string[]) => {
    const message = fields.map((f) => error?.fieldErrors[f]).find(Boolean)
    return message ? <p className="field__error">{message}</p> : null
  }

  return (
    <form className="card card--padded form" onSubmit={submit} noValidate aria-label="Réserver pour plus tard">
      <h2 className="card__title">
        <CalendarPlus size={20} strokeWidth={ICON_STROKE} aria-hidden /> Réserver pour plus tard
      </h2>
      <p className="muted form__intro">Pour charger tout de suite, pas besoin de réserver : « Charger maintenant » depuis la carte.</p>
      {error && Object.keys(error.fieldErrors).length === 0 && <ErrorBanner error={error} title="Réservation impossible" />}
      {created && (
        <Banner title="Réservation confirmée">
          {created.chargerId}, le {formatDateTime(created.startTime)} jusqu’à {formatTime(created.endTime)}. « Démarrer » apparaîtra 15 min avant le créneau, ici et sur la carte.
        </Banner>
      )}

      <label className="field">
        <span className="field__label">Station</span>
        <select
          className="input"
          value={stationId ?? ''}
          onChange={(e) => {
            const next = stations.find((s) => s.id === Number(e.target.value))
            setStationId(next?.id)
            setChargerId(next?.chargers?.find((c) => c.status === 'AVAILABLE')?.id ?? next?.chargers?.[0]?.id ?? '')
          }}
        >
          {stations.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
      </label>

      <fieldset className="field charger-choice">
        <legend className="field__label">Borne</legend>
        <div className="charger-choice__options">
          {(station?.chargers ?? []).map((c) => (
            <label key={c.id} className={`charger-option ${chargerId === c.id ? 'is-selected' : ''} ${chargerTone(c.status) === 'out' ? 'is-disabled' : ''}`}>
              <input
                type="radio"
                name="charger"
                value={c.id}
                checked={chargerId === c.id}
                disabled={chargerTone(c.status) === 'out'}
                onChange={() => setChargerId(c.id ?? '')}
              />
              <span className="charger-option__top">
                <StatusDot tone={chargerTone(c.status)} />
                <strong>{c.id}</strong>
              </span>
              <span className="muted">
                {c.maxPowerKw} kW · {CHARGER_LABELS[c.status ?? ''] ?? c.status}
              </span>
            </label>
          ))}
        </div>
        {fieldError('chargerId')}
      </fieldset>

      <div className="form__row">
        <label className="field">
          <span className="field__label">Jour</span>
          <input className="input" type="date" value={day} onChange={(e) => setDay(e.target.value)} />
        </label>
        <label className="field">
          <span className="field__label">Début</span>
          <input className="input" type="time" value={time} onChange={(e) => setTime(e.target.value)} step={300} />
          {fieldError('startTime')}
        </label>
        <label className="field">
          <span className="field__label">Durée</span>
          <select className="input" value={duration} onChange={(e) => setDuration(Number(e.target.value))}>
            {DURATIONS.map((d) => (
              <option key={d.minutes} value={d.minutes}>
                {d.label}
              </option>
            ))}
          </select>
          {fieldError('endTime')}
        </label>
      </div>
      {fieldError('driverId')}

      <div className="form__actions">
        <button type="submit" className="btn btn--primary" disabled={saving || !chargerId}>
          {saving ? 'Réservation…' : 'Réserver'}
        </button>
      </div>
    </form>
  )
}

function ReservationTable({ reservations, stations, onChange }: {
  reservations: Reservation[]
  stations: Station[]
  onChange: () => void
}) {
  const { start, busyKey, error: startError } = useStartSession()
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 15_000)
    return () => clearInterval(timer)
  }, [])
  const [cancelError, setCancelError] = useState<ApiError | null>(null)
  const [cancellingId, setCancellingId] = useState<number | null>(null)
  const [showAll, setShowAll] = useState(false)
  const stationOf = (chargerId?: string) => stations.find((s) => s.chargers?.some((c) => c.id === chargerId))

  const upcoming = reservations
    .filter((r) => r.status === 'CONFIRMED' || r.status === 'ACTIVE')
    .sort((a, b) => (a.startTime ?? '').localeCompare(b.startTime ?? ''))
  const past = reservations
    .filter((r) => r.status !== 'CONFIRMED' && r.status !== 'ACTIVE')
    .sort((a, b) => (b.startTime ?? '').localeCompare(a.startTime ?? ''))
  const rows = showAll ? [...upcoming, ...past] : upcoming

  async function cancel(reservation: Reservation) {
    if (reservation.id == null) return
    setCancellingId(reservation.id)
    setCancelError(null)
    try {
      await voltpark.cancelReservation(reservation.id) // 204 No Content
      onChange()
    } catch (e) {
      setCancelError(e instanceof ApiError ? e : new ApiError(0, String(e)))
    } finally {
      setCancellingId(null)
    }
  }

  return (
    <section className="card card--padded" aria-label="Mes réservations à venir">
      <h2 className="card__title">
        <CalendarCheck size={20} strokeWidth={ICON_STROKE} aria-hidden /> Mes réservations
        {past.length > 0 && (
          <button type="button" className="link-button card__count" onClick={() => setShowAll((v) => !v)}>
            {showAll ? 'À venir seulement' : `Afficher l’historique (${past.length})`}
          </button>
        )}
      </h2>
      <StartErrorBanner error={startError} />
      <ErrorBanner error={cancelError} title="Annulation impossible" />
      {rows.length === 0 ? (
        <EmptyState
          icon={<CalendarCheck size={24} strokeWidth={ICON_STROKE} />}
          title="Aucune réservation à venir"
          text="Choisissez une borne et un créneau pour réserver."
        />
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Borne</th>
                <th scope="col">Créneau</th>
                <th scope="col">Statut</th>
                <th scope="col" className="num">Actions</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td>
                    <strong>{r.chargerId}</strong>
                    <span className="table__sub">{shortName(stationOf(r.chargerId)?.name)}</span>
                  </td>
                  <td className="tabular">
                    {formatDateTime(r.startTime)}
                    <span className="table__sub">jusqu’à {formatTime(r.endTime)}</span>
                  </td>
                  <td>
                    <Pill tone={r.status === 'ACTIVE' ? 'accent' : r.status === 'CONFIRMED' ? 'success' : 'neutral'}>
                      {RESERVATION_LABELS[r.status ?? 'CONFIRMED']}
                    </Pill>
                  </td>
                  <td className="num">
                    {r.status === 'CONFIRMED' && (
                      <div className="row__actions row__actions--end">
                        {canStartReservation(r, now) ? (
                          <button
                            type="button"
                            className="btn btn--primary btn--small"
                            onClick={() => start(r.chargerId ?? '', r.id)}
                            disabled={busyKey !== null}
                          >
                            <Play size={14} strokeWidth={ICON_STROKE} fill="currentColor" aria-hidden />
                            {busyKey === `reservation-${r.id}` ? 'Démarrage…' : 'Démarrer'}
                          </button>
                        ) : (
                          <span className="muted row__wait" title="Démarrage possible 15 min avant le créneau">
                            <Hourglass size={14} strokeWidth={ICON_STROKE} aria-hidden />{' '}
                            {new Date(r.startTime ?? 0).getTime() > now ? formatCountdown(new Date(r.startTime ?? 0).getTime() - now) : 'créneau passé'}
                          </span>
                        )}
                        <button
                          type="button"
                          className="btn btn--ghost btn--small"
                          onClick={() => cancel(r)}
                          disabled={cancellingId !== null}
                          aria-label={`Annuler la réservation de ${r.chargerId} du ${formatDateTime(r.startTime)}`}
                        >
                          <X size={14} strokeWidth={ICON_STROKE} aria-hidden />
                          {cancellingId === r.id ? 'Annulation…' : 'Annuler'}
                        </button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function ReservationSummary({ reservations }: { reservations: Reservation[] }) {
  const count = (status: Reservation['status']) => reservations.filter((r) => r.status === status).length
  const next = reservations
    .filter((r) => r.status === 'CONFIRMED')
    .sort((a, b) => (a.startTime ?? '').localeCompare(b.startTime ?? ''))[0]
  return (
    <MetricStrip>
      <Metric icon={<CalendarClock size={16} strokeWidth={ICON_STROKE} />} label="À venir" value={count('CONFIRMED')} hint={next ? `prochaine : ${formatDateTime(next.startTime)}` : 'aucune'} />
      <Metric icon={<Zap size={16} strokeWidth={ICON_STROKE} />} label="En cours" value={count('ACTIVE')} hint="charge démarrée" />
      <Metric icon={<CalendarCheck size={16} strokeWidth={ICON_STROKE} />} label="Terminées" value={count('COMPLETED')} />
      <Metric icon={<CalendarX size={16} strokeWidth={ICON_STROKE} />} label="Annulées" value={count('CANCELLED')} />
    </MetricStrip>
  )
}

export function ReservationsPage() {
  return (
    <>
      <PageHeader title={pageMeta('/reservations').title} subtitle={pageMeta('/reservations').subtitle} />
      <RequireDriver>{(driverId) => <ReservationsContent driverId={driverId} />}</RequireDriver>
    </>
  )
}

function ReservationsContent({ driverId }: { driverId: number }) {
  const { stations, reservations, loaded, refresh } = useCharge()
  if (!loaded) {
    return (
      <div className="two-columns">
        <CardSkeleton lines={6} />
        <CardSkeleton lines={4} />
      </div>
    )
  }
  if (stations.length === 0) {
    return (
      <Banner tone="danger" title="Impossible de charger les stations">
        Vérifiez que la plateforme est démarrée, puis rechargez la page.
      </Banner>
    )
  }
  const onChange = () => void refresh()
  return (
    <div className="stack">
      <ReservationSummary reservations={reservations} />
      <div className="two-columns two-columns--form">
        <ReservationForm driverId={driverId} stations={stations} onCreated={onChange} />
        <ReservationTable reservations={reservations} stations={stations} onChange={onChange} />
      </div>
    </div>
  )
}
