import { useState } from 'react'
import { Link } from 'react-router'
import { ChevronLeft, ChevronRight, History, Play, Zap } from 'lucide-react'
import { voltpark, type Invoice, type Session } from '../api/voltpark'
import { LiveSessionPanel } from '../components/LiveSessionPanel'
import { PendingSessionPanel } from '../components/PendingSessionPanel'
import { RequireDriver, StartErrorBanner } from '../components/sessionParts'
import { CardSkeleton, EmptyState, ErrorBanner, ICON_STROKE, PageHeader, Pill } from '../components/ui'
import { formatDateTime, formatKwh } from '../lib/format'
import { SESSION_LABELS, sessionTone } from '../lib/status'
import { useLoad } from '../lib/useLoad'
import { useStartSession } from '../lib/useStartSession'

const PAGE_SIZE = 5

function byStartDesc(a: Session, b: Session) {
  return (b.startedAt ?? '').localeCompare(a.startedAt ?? '')
}

/** Aucune session en cours : demarrer depuis une reservation confirmee. */
function StartFromReservation({ driverId }: { driverId: number }) {
  const { data: reservations } = useLoad(() => voltpark.reservations(driverId), `reservations-${driverId}`)
  const { start, startingId, error } = useStartSession()
  const confirmed = (reservations ?? []).filter((r) => r.status === 'CONFIRMED')

  return (
    <section className="card card--padded">
      <StartErrorBanner error={error} />
      {confirmed.length === 0 ? (
        <EmptyState
          icon={<Zap size={24} strokeWidth={ICON_STROKE} />}
          title="Aucune charge en cours"
          text="Réservez une borne, puis démarrez la charge à votre arrivée."
          action={
            <Link to="/reservations" className="btn btn--primary">
              Réserver une borne
            </Link>
          }
        />
      ) : (
        <>
          <h2 className="card__title">
            <Play size={20} strokeWidth={ICON_STROKE} aria-hidden /> Démarrer une charge
          </h2>
          <ul className="rows">
            {confirmed.map((r) => (
              <li key={r.id} className="row">
                <div className="row__main">
                  <strong>{r.chargerId}</strong>
                  <span className="muted tabular">Réservée à partir de {formatDateTime(r.startTime)}</span>
                </div>
                <button type="button" className="btn btn--primary btn--small" onClick={() => start(r)} disabled={startingId !== null}>
                  <Play size={14} strokeWidth={ICON_STROKE} fill="currentColor" aria-hidden />
                  {startingId === r.id ? 'Démarrage…' : 'Démarrer la charge'}
                </button>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  )
}

function SessionHistory({ sessions, invoices }: { sessions: Session[]; invoices: Invoice[] }) {
  const [page, setPage] = useState(0)
  const pages = Math.max(1, Math.ceil(sessions.length / PAGE_SIZE))
  const current = Math.min(page, pages - 1)
  const visible = sessions.slice(current * PAGE_SIZE, current * PAGE_SIZE + PAGE_SIZE)
  const invoiceOf = (sessionId?: string) => invoices.find((i) => i.sessionId === sessionId)

  return (
    <section className="card card--padded" aria-label="Historique des sessions">
      <h2 className="card__title">
        <History size={20} strokeWidth={ICON_STROKE} aria-hidden /> Historique
      </h2>
      {sessions.length === 0 ? (
        <p className="muted">Vos sessions terminées apparaîtront ici.</p>
      ) : (
        <>
          <ul className="rows">
            {visible.map((s) => {
              const invoice = invoiceOf(s.id)
              return (
                <li key={s.id} className="row">
                  <div className="row__main">
                    <strong className="tabular">{formatDateTime(s.startedAt)}</strong>
                    <span className="muted">
                      Borne {s.chargerId} · <span className="tabular">{formatKwh(s.totalEnergyKwh)} kWh</span>
                    </span>
                  </div>
                  <Pill tone={sessionTone(s.status)}>{SESSION_LABELS[s.status ?? 'COMPLETED']}</Pill>
                  {invoice ? (
                    <Link to={`/factures/${invoice.id}`} className="link-button">
                      Facture <ChevronRight size={16} strokeWidth={ICON_STROKE} aria-hidden />
                    </Link>
                  ) : (
                    <span className="link-button link-button--muted">Sans facture</span>
                  )}
                </li>
              )
            })}
          </ul>
          {pages > 1 && (
            <nav className="pager" aria-label="Pages de l’historique">
              <button type="button" className="icon-button" onClick={() => setPage(current - 1)} disabled={current === 0} aria-label="Page précédente">
                <ChevronLeft size={18} strokeWidth={ICON_STROKE} />
              </button>
              <span className="muted tabular">
                {current + 1} / {pages}
              </span>
              <button type="button" className="icon-button" onClick={() => setPage(current + 1)} disabled={current >= pages - 1} aria-label="Page suivante">
                <ChevronRight size={18} strokeWidth={ICON_STROKE} />
              </button>
            </nav>
          )}
        </>
      )}
    </section>
  )
}

function SessionsContent({ driverId }: { driverId: number }) {
  const sessions = useLoad(() => voltpark.sessions(driverId), `sessions-${driverId}`)
  const invoices = useLoad(() => voltpark.invoices(driverId), `invoices-${driverId}`)

  if (sessions.error) {
    return (
      <ErrorBanner
        error={sessions.error}
        title="Impossible de charger vos sessions"
        actions={<button type="button" className="btn btn--ghost btn--small" onClick={sessions.reload}>Réessayer</button>}
      />
    )
  }
  if (!sessions.data) {
    return (
      <div className="stack">
        <CardSkeleton lines={4} />
        <CardSkeleton lines={3} />
      </div>
    )
  }

  const sorted = [...sessions.data].sort(byStartDesc)
  const active = sorted.find((s) => s.status === 'ACTIVE')
  const pending = sorted.find((s) => s.status === 'PENDING_START')
  const past = sorted.filter((s) => s.status !== 'ACTIVE' && s.status !== 'PENDING_START')
  const refresh = () => {
    sessions.reload()
    invoices.reload()
  }

  return (
    <div className="stack">
      {active && <LiveSessionPanel session={active} />}
      {pending && <PendingSessionPanel session={pending} onChange={refresh} />}
      {!active && !pending && <StartFromReservation driverId={driverId} />}
      <SessionHistory sessions={past} invoices={invoices.data ?? []} />
    </div>
  )
}

export function SessionsPage() {
  return (
    <>
      <PageHeader title="Mes sessions" subtitle="Suivez votre charge en direct et retrouvez vos sessions passées." />
      <RequireDriver>{(driverId) => <SessionsContent driverId={driverId} />}</RequireDriver>
    </>
  )
}
