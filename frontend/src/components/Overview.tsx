import { Link } from 'react-router'
import { BarChart3, ChevronRight, History, Leaf, Receipt, Wallet, Zap } from 'lucide-react'
import type { Invoice, Session } from '../api/voltpark'
import { formatDateTime, formatDuration, formatKwh, formatMoney, formatWhole } from '../lib/format'
import { durationSeconds, lastSevenDays, latestFinished, monthSummary } from '../lib/stats'
import { ICON_STROKE, Metric } from './ui'
import { WeekChart } from './WeekChart'

const monthName = new Intl.DateTimeFormat('fr-FR', { month: 'long' }).format(new Date())

/**
 * Vue d'ensemble : indicateurs du mois, energie des 7 derniers jours et derniere charge.
 * (Demarrer une charge se fait depuis la fiche station ou la carte "prochaine etape".)
 */
export function Overview({ sessions, invoices }: { sessions: Session[]; invoices: Invoice[] }) {
  const month = monthSummary(sessions, invoices)
  const days = lastSevenDays(sessions)
  const weekTotal = days.reduce((sum, d) => sum + d.energyKwh, 0)
  const last = latestFinished(sessions)
  const lastInvoice = invoices.find((i) => i.sessionId === last?.id)

  return (
    <section className="overview" aria-label="Vue d’ensemble">
      <div className="overview__metrics">
        <Metric icon={<Zap size={16} strokeWidth={ICON_STROKE} />} label="Énergie ce mois" value={formatKwh(month.energyKwh)} unit="kWh" hint={monthName} />
        <Metric icon={<Wallet size={16} strokeWidth={ICON_STROKE} />} label="Dépenses ce mois" value={formatMoney(month.spent).replace(' DT', '')} unit="DT" hint={`${month.pendingInvoices} facture${month.pendingInvoices > 1 ? 's' : ''} en attente`} />
        <Metric icon={<BarChart3 size={16} strokeWidth={ICON_STROKE} />} label="Charges" value={month.sessions} hint="terminées ce mois" />
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
            <Receipt size={16} strokeWidth={ICON_STROKE} aria-hidden /> Dernière charge
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
          <Link to="/historique" className="btn btn--ghost btn--block">
            <History size={16} strokeWidth={ICON_STROKE} aria-hidden /> Historique et factures
          </Link>
        </div>
      </div>
    </section>
  )
}
