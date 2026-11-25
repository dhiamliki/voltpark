import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { ArrowLeft, ChevronRight, FileText, RotateCw } from 'lucide-react'
import { ApiError } from '../api/client'
import { voltpark, type Invoice } from '../api/voltpark'
import { RequireDriver } from '../components/sessionParts'
import { Banner, CardSkeleton, EmptyState, ErrorBanner, ICON_STROKE, PageHeader, Pill } from '../components/ui'
import { formatDateTime, formatKwh, formatMoney } from '../lib/format'
import { INVOICE_LABELS, invoiceTone } from '../lib/status'
import { useLoad } from '../lib/useLoad'

function InvoiceList({ driverId }: { driverId: number }) {
  const { data, error, reload } = useLoad(() => voltpark.invoices(driverId), `invoices-${driverId}`)
  if (error) {
    return (
      <ErrorBanner
        error={error}
        title="Impossible de charger vos factures"
        actions={<button type="button" className="btn btn--ghost btn--small" onClick={reload}>Réessayer</button>}
      />
    )
  }
  if (!data) return <CardSkeleton lines={4} />
  if (data.length === 0) {
    return (
      <div className="card">
        <EmptyState
          icon={<FileText size={24} strokeWidth={ICON_STROKE} />}
          title="Aucune facture pour l’instant"
          text="Une facture est créée à la fin de chaque charge."
          action={
            <Link to="/sessions" className="btn btn--primary">
              Voir mes sessions
            </Link>
          }
        />
      </div>
    )
  }
  const sorted = [...data].sort((a, b) => (b.issuedAt ?? '').localeCompare(a.issuedAt ?? ''))
  return (
    <section className="card card--padded">
      <ul className="rows">
        {sorted.map((invoice) => (
          <li key={invoice.id}>
            <Link to={`/factures/${invoice.id}`} className="row row--link">
              <div className="row__main">
                <strong>Facture n° {invoice.id}</strong>
                <span className="muted tabular">
                  {formatDateTime(invoice.issuedAt)} · {formatKwh(invoice.totalEnergyKwh)} kWh
                </span>
              </div>
              <Pill tone={invoiceTone(invoice.status)}>{INVOICE_LABELS[invoice.status ?? 'ISSUED']}</Pill>
              <span className="row__amount tabular">{formatMoney(invoice.totalAmount, invoice.currency)}</span>
              <ChevronRight size={18} strokeWidth={ICON_STROKE} aria-hidden className="muted" />
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}

export function InvoicesPage() {
  return (
    <>
      <PageHeader title="Factures" subtitle="Le détail de chaque charge : énergie, tarifs et frais d’occupation." />
      <RequireDriver>{(driverId) => <InvoiceList driverId={driverId} />}</RequireDriver>
    </>
  )
}

/** Prix unitaire deduit du cout et de l'energie (la facture ne contient que les totaux). */
function unitPrice(cost?: number, kwh?: number) {
  return cost != null && kwh ? cost / kwh : null
}

function InvoiceDetail({ invoice, onUpdated }: { invoice: Invoice; onUpdated: (invoice: Invoice) => void }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const pending = invoice.status === 'PENDING_ENERGY'

  async function recalculate() {
    if (invoice.id == null) return
    setBusy(true)
    setError(null)
    try {
      onUpdated(await voltpark.recalculateInvoice(invoice.id))
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError(0, String(e)))
    } finally {
      setBusy(false)
    }
  }

  const peakPrice = unitPrice(invoice.peakEnergyCost, invoice.peakKwh)
  const offPeakPrice = unitPrice(invoice.offPeakEnergyCost, invoice.offPeakKwh)

  return (
    <article className="card card--padded invoice" aria-label={`Facture ${invoice.id}`}>
      <header className="invoice__header">
        <div>
          <h2>Facture n° {invoice.id}</h2>
          <p className="muted tabular">Émise le {formatDateTime(invoice.issuedAt)}</p>
        </div>
        <Pill tone={invoiceTone(invoice.status)}>{INVOICE_LABELS[invoice.status ?? 'ISSUED']}</Pill>
      </header>

      {pending && (
        <Banner
          title="Mesures d’énergie en attente"
          actions={
            <button type="button" className="btn btn--primary btn--small" onClick={recalculate} disabled={busy}>
              <RotateCw size={16} strokeWidth={ICON_STROKE} aria-hidden /> {busy ? 'Calcul…' : 'Recalculer'}
            </button>
          }
        >
          Le service de mesure était indisponible à la fin de la charge. Les montants seront calculés dès qu’il répondra.
        </Banner>
      )}
      <ErrorBanner error={error} title="Le recalcul a échoué" />

      <dl className="invoice__total">
        <dt className="muted">Total</dt>
        <dd className="number">{formatMoney(invoice.totalAmount, invoice.currency)}</dd>
      </dl>

      <table className="invoice__lines">
        <thead>
          <tr>
            <th scope="col">Détail</th>
            <th scope="col" className="num">Quantité</th>
            <th scope="col" className="num">Prix</th>
            <th scope="col" className="num">Montant</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <th scope="row">Énergie heures pleines</th>
            <td className="num">{formatKwh(invoice.peakKwh)} kWh</td>
            <td className="num">{peakPrice == null ? '—' : `${formatMoney(peakPrice, invoice.currency)}/kWh`}</td>
            <td className="num">{formatMoney(invoice.peakEnergyCost, invoice.currency)}</td>
          </tr>
          <tr>
            <th scope="row">Énergie heures creuses</th>
            <td className="num">{formatKwh(invoice.offPeakKwh)} kWh</td>
            <td className="num">{offPeakPrice == null ? '—' : `${formatMoney(offPeakPrice, invoice.currency)}/kWh`}</td>
            <td className="num">{formatMoney(invoice.offPeakEnergyCost, invoice.currency)}</td>
          </tr>
          <tr>
            <th scope="row">Frais d’occupation</th>
            <td className="num">{invoice.idleDurationMinutes ?? 0} min</td>
            <td className="num muted">après 15 min</td>
            <td className="num">{formatMoney(invoice.idleFee, invoice.currency)}</td>
          </tr>
        </tbody>
        <tfoot>
          <tr>
            <th scope="row">Total ({formatKwh(invoice.totalEnergyKwh)} kWh)</th>
            <td />
            <td />
            <td className="num">{formatMoney(invoice.totalAmount, invoice.currency)}</td>
          </tr>
        </tfoot>
      </table>
    </article>
  )
}

export function InvoiceDetailPage() {
  const { id } = useParams()
  const invoiceId = Number(id)
  const { data, error } = useLoad(() => voltpark.invoice(invoiceId), `invoice-${invoiceId}`)
  const [updated, setUpdated] = useState<Invoice | null>(null)
  const invoice = updated?.id === invoiceId ? updated : data

  return (
    <>
      <Link to="/factures" className="back-link">
        <ArrowLeft size={16} strokeWidth={ICON_STROKE} aria-hidden /> Factures
      </Link>
      {error ? (
        <ErrorBanner error={error} title="Facture introuvable" />
      ) : !invoice ? (
        <CardSkeleton lines={6} />
      ) : (
        <InvoiceDetail invoice={invoice} onUpdated={setUpdated} />
      )}
    </>
  )
}
