import { useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import { CalendarCheck, CalendarPlus, Play, X } from 'lucide-react'
import { ApiError } from '../api/client'
import { voltpark, type Reservation, type Station } from '../api/voltpark'
import { StartErrorBanner, RequireDriver } from '../components/sessionParts'
import { Banner, CardSkeleton, EmptyState, ErrorBanner, ICON_STROKE, PageHeader, Pill, StatusDot } from '../components/ui'
import { formatDateTime, formatTime } from '../lib/format'
import { CHARGER_LABELS, chargerTone, RESERVATION_LABELS } from '../lib/status'
import { useLoad } from '../lib/useLoad'
import { useStartSession } from '../lib/useStartSession'

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
    <form className="card card--padded form" onSubmit={submit} noValidate aria-label="Nouvelle réservation">
      <h2 className="card__title">
        <CalendarPlus size={20} strokeWidth={ICON_STROKE} aria-hidden /> Nouvelle réservation
      </h2>
      {error && Object.keys(error.fieldErrors).length === 0 && <ErrorBanner error={error} title="Réservation impossible" />}
      {created && (
        <Banner title="Réservation confirmée">
          {created.chargerId}, le {formatDateTime(created.startTime)} jusqu’à {formatTime(created.endTime)}. Démarrez la charge depuis « Mes réservations ».
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
            <label key={c.id} className={`charger-option ${chargerId === c.id ? 'is-selected' : ''}`}>
              <input type="radio" name="charger" value={c.id} checked={chargerId === c.id} onChange={() => setChargerId(c.id ?? '')} />
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

function UpcomingReservations({ reservations, stations, onChange }: {
  reservations: Reservation[]
  stations: Station[]
  onChange: () => void
}) {
  const { start, startingId, error: startError } = useStartSession()
  const [cancelError, setCancelError] = useState<ApiError | null>(null)
  const [cancellingId, setCancellingId] = useState<number | null>(null)
  const stationOf = (chargerId?: string) => stations.find((s) => s.chargers?.some((c) => c.id === chargerId))

  const upcoming = reservations
    .filter((r) => r.status === 'CONFIRMED' || r.status === 'ACTIVE')
    .sort((a, b) => (a.startTime ?? '').localeCompare(b.startTime ?? ''))

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
      </h2>
      <StartErrorBanner error={startError} />
      <ErrorBanner error={cancelError} title="Annulation impossible" />
      {upcoming.length === 0 ? (
        <EmptyState
          icon={<CalendarCheck size={24} strokeWidth={ICON_STROKE} />}
          title="Aucune réservation à venir"
          text="Choisissez une borne et un créneau pour réserver."
        />
      ) : (
        <ul className="rows">
          {upcoming.map((r) => (
            <li key={r.id} className="row">
              <div className="row__main">
                <strong>{r.chargerId}</strong>
                <span className="muted">{stationOf(r.chargerId)?.name}</span>
                <span className="tabular">
                  {formatDateTime(r.startTime)} → {formatTime(r.endTime)}
                </span>
              </div>
              <Pill tone={r.status === 'ACTIVE' ? 'accent' : 'neutral'}>{RESERVATION_LABELS[r.status ?? 'CONFIRMED']}</Pill>
              {r.status === 'CONFIRMED' && (
                <div className="row__actions">
                  <button type="button" className="btn btn--primary btn--small" onClick={() => start(r)} disabled={startingId !== null}>
                    <Play size={14} strokeWidth={ICON_STROKE} fill="currentColor" aria-hidden />
                    {startingId === r.id ? 'Démarrage…' : 'Démarrer la charge'}
                  </button>
                  <button type="button" className="btn btn--ghost btn--small" onClick={() => cancel(r)} disabled={cancellingId !== null}>
                    <X size={14} strokeWidth={ICON_STROKE} aria-hidden />
                    {cancellingId === r.id ? 'Annulation…' : 'Annuler'}
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export function ReservationsPage() {
  return (
    <>
      <PageHeader title="Réservations" subtitle="Réservez une borne, puis démarrez la charge à votre arrivée." />
      <RequireDriver>{(driverId) => <ReservationsContent driverId={driverId} />}</RequireDriver>
    </>
  )
}

function ReservationsContent({ driverId }: { driverId: number }) {
  const stations = useLoad((signal) => voltpark.stations(signal), 'stations')
  const reservations = useLoad(() => voltpark.reservations(driverId), `reservations-${driverId}`)

  if (stations.error) return <ErrorBanner error={stations.error} title="Impossible de charger les stations" />
  if (!stations.data) {
    return (
      <div className="two-columns">
        <CardSkeleton lines={6} />
        <CardSkeleton lines={4} />
      </div>
    )
  }
  return (
    <div className="two-columns">
      <ReservationForm driverId={driverId} stations={stations.data} onCreated={reservations.reload} />
      {reservations.error ? (
        <ErrorBanner error={reservations.error} title="Impossible de charger vos réservations" />
      ) : !reservations.data ? (
        <CardSkeleton lines={4} />
      ) : (
        <UpcomingReservations
          reservations={reservations.data}
          stations={stations.data}
          onChange={() => {
            reservations.reload()
            stations.reload()
          }}
        />
      )}
    </div>
  )
}
