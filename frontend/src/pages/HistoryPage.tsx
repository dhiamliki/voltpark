import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { CalendarClock, ChevronLeft, ChevronRight, Clock3, FileText, History, Zap } from 'lucide-react'
import { voltpark, type Invoice, type Session } from '../api/voltpark'
import { RequireDriver } from '../components/sessionParts'
import { CardSkeleton, ErrorBanner, ICON_STROKE, Metric, MetricStrip, PageHeader, Pill } from '../components/ui'
import { formatDateTime, formatDuration, formatKwh, formatMoney } from '../lib/format'
import { pageMeta } from '../lib/pages'
import { durationSeconds, isFinished, latestFinished, monthSummary } from '../lib/stats'
import { SESSION_LABELS, sessionTone } from '../lib/status'
import { useLoad } from '../lib/useLoad'
import { InvoicesTab } from './InvoicesPage'

const PAGE_SIZE = 8

type Tab = 'charges' | 'factures'

function byStartDesc(a: Session, b: Session) {
  return (b.startedAt ?? '').localeCompare(a.startedAt ?? '')
}

function SessionHistory({ sessions, invoices }: { sessions: Session[]; invoices: Invoice[] }) {
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const pages = Math.max(1, Math.ceil(sessions.length / PAGE_SIZE))
  const current = Math.min(page, pages - 1)
  const visible = sessions.slice(current * PAGE_SIZE, current * PAGE_SIZE + PAGE_SIZE)
  const invoiceOf = (sessionId?: string) => invoices.find((i) => i.sessionId === sessionId)

  return (
    <section className="card card--padded" aria-label="Historique des charges">
      <h2 className="card__title">
        <History size={20} strokeWidth={ICON_STROKE} aria-hidden /> Charges passées
        <span className="muted card__count tabular">{sessions.length} charge{sessions.length > 1 ? 's' : ''}</span>
      </h2>
      {sessions.length === 0 ? (
        <p className="muted">Vos charges terminées apparaîtront ici.</p>
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
      <Metric icon={<Zap size={16} strokeWidth={ICON_STROKE} />} label="Charges ce mois" value={month.sessions} hint={`${finished.length} au total`} />
      <Metric icon={<Zap size={16} strokeWidth={ICON_STROKE} />} label="Énergie ce mois" value={formatKwh(month.energyKwh)} unit="kWh" />
      <Metric icon={<Clock3 size={16} strokeWidth={ICON_STROKE} />} label="Durée moyenne" value={formatDuration(average)} hint="par session" />
      <Metric icon={<CalendarClock size={16} strokeWidth={ICON_STROKE} />} label="Dernière charge" value={last ? formatDateTime(last.startedAt) : '—'} hint={last ? `${formatKwh(last.totalEnergyKwh)} kWh · borne ${last.chargerId}` : undefined} />
    </MetricStrip>
  )
}

function ChargesTab({ driverId }: { driverId: number }) {
  const sessions = useLoad(() => voltpark.sessions(driverId), `sessions-${driverId}`)
  const invoices = useLoad(() => voltpark.invoices(driverId), `invoices-${driverId}`)

  if (sessions.error) {
    return (
      <ErrorBanner
        error={sessions.error}
        title="Impossible de charger vos charges"
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
  const past = sorted.filter((s) => s.status !== 'ACTIVE' && s.status !== 'PENDING_START')
  return (
    <div className="stack">
      <SessionsSummary sessions={sorted} invoices={invoices.data ?? []} />
      <SessionHistory sessions={past} invoices={invoices.data ?? []} />
    </div>
  )
}

const TABS: Array<{ id: Tab; label: string; icon: typeof History }> = [
  { id: 'charges', label: 'Charges', icon: History },
  { id: 'factures', label: 'Factures', icon: FileText },
]

/** Historique et factures : les charges passees et leurs factures, dans deux onglets. */
export function HistoryPage() {
  const [params, setParams] = useSearchParams()
  const tab: Tab = params.get('onglet') === 'factures' ? 'factures' : 'charges'
  const meta = pageMeta('/historique')
  return (
    <>
      <PageHeader title={meta.title} subtitle={meta.subtitle} />
      <div className="tabs" role="tablist" aria-label="Historique et factures">
        {TABS.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            type="button"
            role="tab"
            id={`tab-${id}`}
            aria-selected={tab === id}
            aria-controls={`panel-${id}`}
            className={`tabs__tab ${tab === id ? 'is-active' : ''}`}
            onClick={() => setParams(id === 'charges' ? {} : { onglet: id }, { replace: true })}
          >
            <Icon size={16} strokeWidth={ICON_STROKE} aria-hidden /> {label}
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`panel-${tab}`} aria-labelledby={`tab-${tab}`}>
        <RequireDriver>
          {(driverId) => (tab === 'factures' ? <InvoicesTab driverId={driverId} /> : <ChargesTab driverId={driverId} />)}
        </RequireDriver>
      </div>
    </>
  )
}
