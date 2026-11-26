import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { CheckCircle2, Clock3, FileText, MapPin, Moon, RotateCw, Sun, Zap } from 'lucide-react'
import { ApiError } from '../api/client'
import { voltpark, type Invoice } from '../api/voltpark'
import { SimulationChip } from '../components/LiveSessionPanel'
import { Banner, Breadcrumbs, CardSkeleton, ErrorBanner, ICON_STROKE, PageHeader, Pill } from '../components/ui'
import { formatDateTime, formatDuration, formatKwh, formatMoney, shortName } from '../lib/format'
import { pageMeta } from '../lib/pages'
import { simulatedSeconds } from '../lib/simulation'
import { durationSeconds } from '../lib/stats'
import { INVOICE_LABELS, invoiceTone } from '../lib/status'
import { useLoad } from '../lib/useLoad'
import { useCharge } from '../state/ChargeContext'

/**
 * Charge terminee : ouvert automatiquement apres l'arret. Energie, duree, heures pleines / creuses,
 * total et statut de la facture, puis "Voir la facture" ou "Retour a la carte".
 */
export function ReceiptPage() {
  const { sessionId = '' } = useParams()
  const { stationOf, refresh } = useCharge()
  const session = useLoad(() => voltpark.session(sessionId), `receipt-session-${sessionId}`)
  const invoiceLoad = useLoad(() => voltpark.sessionInvoice(sessionId), `receipt-invoice-${sessionId}`)
  const [updated, setUpdated] = useState<Invoice | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const invoice = updated ?? invoiceLoad.data
  const meta = pageMeta('/charge/terminee')

  async function recalculate() {
    if (invoice?.id == null) return
    setBusy(true)
    setError(null)
    try {
      setUpdated(await voltpark.recalculateInvoice(invoice.id))
      await refresh()
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError(0, String(e)))
    } finally {
      setBusy(false)
    }
  }

  const s = session.data
  const station = stationOf(s?.chargerId)
  const real = s ? durationSeconds(s) : 0
  const pending = invoice?.status === 'PENDING_ENERGY'

  return (
    <>
      <Breadcrumbs items={[{ label: 'Ma charge', to: '/charge' }, { label: 'Charge terminée' }]} />
      <PageHeader title={meta.title} subtitle={meta.subtitle} />
      {session.error ? (
        <ErrorBanner error={session.error} title="Session introuvable" />
      ) : !s ? (
        <CardSkeleton lines={6} />
      ) : (
        <article className="card card--padded receipt" aria-label="Charge terminée">
          <header className="receipt__header">
            <span className="receipt__check" aria-hidden>
              <CheckCircle2 size={28} strokeWidth={ICON_STROKE} />
            </span>
            <div>
              <h2>Charge terminée</h2>
              <p className="muted">
                <MapPin size={14} strokeWidth={ICON_STROKE} aria-hidden /> {station ? shortName(station.name) : '—'} · borne {s.chargerId} ·{' '}
                {formatDateTime(s.stoppedAt ?? s.startedAt)}
              </p>
            </div>
            {invoice && <Pill tone={invoiceTone(invoice.status)}>{INVOICE_LABELS[invoice.status ?? 'ISSUED']}</Pill>}
          </header>

          {pending && (
            <Banner
              title="Montant en attente des mesures"
              actions={
                <button type="button" className="btn btn--primary btn--small" onClick={recalculate} disabled={busy}>
                  <RotateCw size={16} strokeWidth={ICON_STROKE} aria-hidden /> {busy ? 'Calcul…' : 'Recalculer'}
                </button>
              }
            >
              Le service de mesure d’énergie était indisponible à l’arrêt. La charge est bien terminée et la borne
              libérée ; le montant sera calculé dès que le service répondra.
            </Banner>
          )}
          <ErrorBanner error={error} title="Le recalcul a échoué" />

          <dl className="receipt__grid">
            <div>
              <dt>
                <Zap size={16} strokeWidth={ICON_STROKE} aria-hidden /> Énergie
              </dt>
              <dd className="number">
                {formatKwh(invoice?.totalEnergyKwh ?? s.totalEnergyKwh)} <span className="unit">kWh</span>
              </dd>
            </div>
            <div>
              <dt>
                <Clock3 size={16} strokeWidth={ICON_STROKE} aria-hidden /> Durée de charge
              </dt>
              <dd className="number">{formatDuration(simulatedSeconds(real))}</dd>
              <dd className="muted receipt__hint">
                <SimulationChip /> {formatDuration(real)} en temps réel
              </dd>
            </div>
            <div>
              <dt>
                <Sun size={16} strokeWidth={ICON_STROKE} aria-hidden /> Heures pleines
              </dt>
              <dd className="tabular">
                {formatKwh(invoice?.peakKwh)} kWh · {formatMoney(invoice?.peakEnergyCost, invoice?.currency)}
              </dd>
            </div>
            <div>
              <dt>
                <Moon size={16} strokeWidth={ICON_STROKE} aria-hidden /> Heures creuses
              </dt>
              <dd className="tabular">
                {formatKwh(invoice?.offPeakKwh)} kWh · {formatMoney(invoice?.offPeakEnergyCost, invoice?.currency)}
              </dd>
            </div>
          </dl>

          <dl className="receipt__total">
            <dt>Total</dt>
            <dd className="number">{invoiceLoad.data || updated ? formatMoney(invoice?.totalAmount, invoice?.currency) : '—'}</dd>
          </dl>

          <div className="receipt__actions">
            {invoice?.id != null && (
              <Link to={`/factures/${invoice.id}`} className="btn btn--ghost">
                <FileText size={16} strokeWidth={ICON_STROKE} aria-hidden /> Voir la facture
              </Link>
            )}
            <Link to="/" className="btn btn--primary">
              Retour à la carte
            </Link>
          </div>
        </article>
      )}
    </>
  )
}
