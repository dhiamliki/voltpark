import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { CalendarClock, ChevronLeft, ChevronRight, Clock3, History, Play, Zap } from 'lucide-react'
import { voltpark, type Invoice, type Session } from '../api/voltpark'
import { LiveSessionPanel } from '../components/LiveSessionPanel'
import { PendingSessionPanel } from '../components/PendingSessionPanel'
import { RequireDriver, StartErrorBanner } from '../components/sessionParts'
import { CardSkeleton, ErrorBanner, ICON_STROKE, Metric, MetricStrip, PageHeader, Pill } from '../components/ui'
import { formatDateTime, formatDuration, formatKwh, formatMoney } from '../lib/format'
import { durationSeconds, isFinished, latestFinished, monthSummary } from '../lib/stats'
import { SESSION_LABELS, sessionTone } from '../lib/status'
import { useLoad } from '../lib/useLoad'
import { useStartSession } from '../lib/useStartSession'

const PAGE_SIZE = 8

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
        <div className="idle">
          <span className="idle__icon">
            <Zap size={20} strokeWidth={ICON_STROKE} aria-hidden />
          </span>
          <div className="idle__text">
            <p className="idle__title">Aucune charge en cours</p>
            <p className="muted">Réservez une borne, puis démarrez la charge à votre arrivée.</p>
          </div>
          <Link to="/reservations" className="btn btn--primary btn--small">
            Réserver une borne
          </Link>
        </div>
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
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const pages = Math.max(1, Math.ceil(sessions.length / PAGE_SIZE))
  const current = Math.min(page, pages - 1)
  const visible = sessions.slice(current * PAGE_SIZE, current * PAGE_SIZE + PAGE_SIZE)
  const invoiceOf = (sessionId?: string) => invoices.find((i) => i.sessionId === sessionId)

  return (
    <section className="card card--padded" aria-label="Historique des sessions">
      <h2 className="card__title">
        <History size={20} strokeWidth={ICON_STROKE} aria-hidden /> Historique
        <span className="muted card__count tabular">{sessions.length} session{sessions.length > 1 ? 's' : ''}</span>
      </h2>
      {sessions.length === 0 ? (
        <p className="muted">Vos sessions terminées apparaîtront ici.</p>
      ) : (
        <>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">Date</th>
                  <th scope="col">Borne</th>
                  <th scope="col" className="num">Durée</th>
                  <th scope="col" className="num">Énergie</th>
                  <th scope="col">Statut</th>
                  <th scope="col" className="num">Montant</th>
                  <th scope="col"><span className="visually-hidden">Facture</span></th>
                </tr>
              </thead>
              <tbody>
                {visible.map((s) => {
                  const invoice = invoiceOf(s.id)
                  return (
                    <tr key={s.id} className={invoice ? 'is-clickable' : undefined} onClick={() => invoice && navigate(`/factures/${invoice.id}`)}>
                      <td className="tabular">{formatDateTime(s.startedAt)}</td>
                      <td>{s.chargerId}</td>
                      <td className="num">{isFinished(s) ? formatDuration(durationSeconds(s)) : '—'}</td>
                      <td className="num">{formatKwh(s.totalEnergyKwh)} kWh</td>
                      <td>
                        <Pill tone={sessionTone(s.status)}>{SESSION_LABELS[s.status ?? 'COMPLETED']}</Pill>
                      </td>
                      <td className="num strong">{invoice ? formatMoney(invoice.totalAmount, invoice.currency) : '—'}</td>
                      <td className="num">
                        {invoice ? (
                          <Link to={`/factures/${invoice.id}`} className="link-button" onClick={(e) => e.stopPropagation()}>
                            Facture <ChevronRight size={16} strokeWidth={ICON_STROKE} aria-hidden />
                          </Link>
                        ) : (
                          <span className="muted">Sans facture</span>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
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

function SessionsSummary({ sessions, invoices }: { sessions: Session[]; invoices: Invoice[] }) {
  const month = monthSummary(sessions, invoices)
  const finished = sessions.filter(isFinished)
  const average = finished.length ? finished.reduce((sum, s) => sum + durationSeconds(s), 0) / finished.length : 0
  const last = latestFinished(sessions)
  return (
    <MetricStrip>
      <Metric icon={<Zap size={16} strokeWidth={ICON_STROKE} />} label="Sessions ce mois" value={month.sessions} hint={`${finished.length} au total`} />
      <Metric icon={<Zap size={16} strokeWidth={ICON_STROKE} />} label="Énergie ce mois" value={formatKwh(month.energyKwh)} unit="kWh" />
      <Metric icon={<Clock3 size={16} strokeWidth={ICON_STROKE} />} label="Durée moyenne" value={formatDuration(average)} hint="par session" />
      <Metric icon={<CalendarClock size={16} strokeWidth={ICON_STROKE} />} label="Dernière charge" value={last ? formatDateTime(last.startedAt) : '—'} hint={last ? `${formatKwh(last.totalEnergyKwh)} kWh · borne ${last.chargerId}` : undefined} />
    </MetricStrip>
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
        <CardSkeleton lines={2} />
        <CardSkeleton lines={4} />
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
      <SessionsSummary sessions={sorted} invoices={invoices.data ?? []} />
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
