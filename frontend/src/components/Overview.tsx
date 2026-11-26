import { Link } from 'react-router'
import { BarChart3, ChevronRight, Leaf, Play, Receipt, Wallet, Zap } from 'lucide-react'
import type { Invoice, Reservation, Session } from '../api/voltpark'
import { formatDateTime, formatDuration, formatKwh, formatMoney, formatWhole } from '../lib/format'
import { durationSeconds, lastSevenDays, latestFinished, monthSummary } from '../lib/stats'
import { useStartSession } from '../lib/useStartSession'
import { StartErrorBanner } from './sessionParts'
import { ICON_STROKE, Metric } from './ui'
import { WeekChart } from './WeekChart'

const monthName = new Intl.DateTimeFormat('fr-FR', { month: 'long' }).format(new Date())

/**
 * Vue d'ensemble quand aucune charge n'est en cours : indicateurs du mois, energie des
 * 7 derniers jours, derniere session et demarrage depuis la prochaine reservation.
 */
export function Overview({ sessions, invoices, reservations }: {
  sessions: Session[]
  invoices: Invoice[]
  reservations: Reservation[]
}) {
  const month = monthSummary(sessions, invoices)
  const days = lastSevenDays(sessions)
  const weekTotal = days.reduce((sum, d) => sum + d.energyKwh, 0)
  const last = latestFinished(sessions)
  const lastInvoice = invoices.find((i) => i.sessionId === last?.id)
  const next = reservations
    .filter((r) => r.status === 'CONFIRMED')
    .sort((a, b) => (a.startTime ?? '').localeCompare(b.startTime ?? ''))[0]
  const { start, startingId, error } = useStartSession()

  return (
    <section className="overview" aria-label="Vue d’ensemble">
      <div className="overview__metrics">
        <Metric icon={<Zap size={16} strokeWidth={ICON_STROKE} />} label="Énergie ce mois" value={formatKwh(month.energyKwh)} unit="kWh" hint={monthName} />
        <Metric icon={<Wallet size={16} strokeWidth={ICON_STROKE} />} label="Dépenses ce mois" value={formatMoney(month.spent).replace(' DT', '')} unit="DT" hint={`${month.pendingInvoices} facture${month.pendingInvoices > 1 ? 's' : ''} en attente`} />
        <Metric icon={<BarChart3 size={16} strokeWidth={ICON_STROKE} />} label="Sessions" value={month.sessions} hint="terminées ce mois" />
        <Metric icon={<Leaf size={16} strokeWidth={ICON_STROKE} />} label="CO₂ évité" value={formatWhole(month.co2Kg)} unit="kg" hint="estimation, 0,4 kg/kWh" />
      </div>

      <div className="card overview__week">
        <div className="overview__card-head">
          <span className="metric__label">
            <BarChart3 size={16} strokeWidth={ICON_STROKE} aria-hidden /> 7 derniers jours
          </span>
          <span className="tabular overview__week-total">{formatKwh(weekTotal)} kWh</span>
        </div>
        <WeekChart days={days} />
      </div>

      <div className="card overview__last">
        <div className="overview__card-head">
          <span className="metric__label">
            <Receipt size={16} strokeWidth={ICON_STROKE} aria-hidden /> Dernière session
          </span>
        </div>
        {last ? (
          <>
            <p className="overview__last-date tabular">{formatDateTime(last.startedAt)}</p>
            <p className="muted">
              Borne {last.chargerId} · <span className="tabular">{formatKwh(last.totalEnergyKwh)} kWh · {formatDuration(durationSeconds(last))}</span>
            </p>
            {lastInvoice && (
              <Link to={`/factures/${lastInvoice.id}`} className="link-button overview__invoice">
                <span className="tabular">{formatMoney(lastInvoice.totalAmount, lastInvoice.currency)}</span> · Voir la facture
                <ChevronRight size={16} strokeWidth={ICON_STROKE} aria-hidden />
              </Link>
            )}
          </>
        ) : (
          <p className="muted">Votre première charge apparaîtra ici.</p>
        )}
        <div className="overview__cta">
          <StartErrorBanner error={error} />
          {next ? (
            <button type="button" className="btn btn--primary btn--block overview__start" onClick={() => start(next)} disabled={startingId !== null}>
              <Play size={14} strokeWidth={ICON_STROKE} fill="currentColor" aria-hidden />
              {startingId === next.id ? (
                'Démarrage…'
              ) : (
                <span aria-label={`Démarrer une charge sur la borne ${next.chargerId}`}>
                  Démarrer · <span className="nowrap">{next.chargerId}</span>
                </span>
              )}
            </button>
          ) : (
            <Link to="/reservations" className="btn btn--ghost btn--block">
              Réserver une borne
            </Link>
          )}
        </div>
      </div>
    </section>
  )
}
