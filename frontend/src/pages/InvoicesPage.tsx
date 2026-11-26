import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ArrowLeft, CheckCircle2, ChevronRight, Clock3, FileText, Hourglass, MapPin, RotateCw, Wallet, Zap } from 'lucide-react'
import { ApiError } from '../api/client'
import { voltpark, type Invoice, type Session } from '../api/voltpark'
import { RequireDriver } from '../components/sessionParts'
import { Banner, CardSkeleton, EmptyState, ErrorBanner, ICON_STROKE, Metric, MetricStrip, PageHeader, Pill } from '../components/ui'
import { formatDateTime, formatDuration, formatKwh, formatMoney, shortName } from '../lib/format'
import { durationSeconds, monthSummary } from '../lib/stats'
import { INVOICE_LABELS, invoiceTone } from '../lib/status'
import { useLoad } from '../lib/useLoad'

function InvoiceSummary({ invoices, sessions }: { invoices: Invoice[]; sessions: Session[] }) {
  const month = monthSummary(sessions, invoices)
  const paid = invoices.filter((i) => i.status === 'PAID')
  const toPay = invoices.filter((i) => i.status === 'ISSUED')
  const energy = invoices.reduce((sum, i) => sum + (i.totalEnergyKwh ?? 0), 0)
  return (
    <MetricStrip>
      <Metric icon={<Wallet size={16} strokeWidth={ICON_STROKE} />} label="Dépenses ce mois" value={formatMoney(month.spent).replace(' DT', '')} unit="DT" />
      <Metric icon={<CheckCircle2 size={16} strokeWidth={ICON_STROKE} />} label="Payées" value={paid.length} hint={formatMoney(paid.reduce((s, i) => s + (i.totalAmount ?? 0), 0))} />
      <Metric icon={<Hourglass size={16} strokeWidth={ICON_STROKE} />} label="À régler" value={toPay.length} hint={month.pendingInvoices ? `${month.pendingInvoices} en attente des mesures` : formatMoney(toPay.reduce((s, i) => s + (i.totalAmount ?? 0), 0))} />
      <Metric icon={<Zap size={16} strokeWidth={ICON_STROKE} />} label="Énergie facturée" value={formatKwh(energy)} unit="kWh" hint={`${invoices.length} facture${invoices.length > 1 ? 's' : ''}`} />
    </MetricStrip>
  )
}

function InvoiceList({ driverId }: { driverId: number }) {
  const navigate = useNavigate()
  const { data, error, reload } = useLoad(() => voltpark.invoices(driverId), `invoices-${driverId}`)
  const sessions = useLoad(() => voltpark.sessions(driverId), `sessions-${driverId}`)
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
  const chargerOf = (sessionId?: string) => sessions.data?.find((s) => s.id === sessionId)?.chargerId ?? '—'
  return (
    <div className="stack">
      <InvoiceSummary invoices={data} sessions={sessions.data ?? []} />
      <section className="card card--padded" aria-label="Liste des factures">
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Facture</th>
                <th scope="col">Date</th>
                <th scope="col">Borne</th>
                <th scope="col" className="num">Énergie</th>
                <th scope="col" className="num">HP / HC</th>
                <th scope="col">Statut</th>
                <th scope="col" className="num">Montant</th>
                <th scope="col"><span className="visually-hidden">Détail</span></th>
              </tr>
            </thead>
            <tbody>
              {sorted.map((invoice) => (
                <tr key={invoice.id} className="is-clickable" onClick={() => navigate(`/factures/${invoice.id}`)}>
                  <td>
                    <Link to={`/factures/${invoice.id}`} className="table__link" onClick={(e) => e.stopPropagation()}>
                      N° {invoice.id}
                    </Link>
                  </td>
                  <td className="tabular">{formatDateTime(invoice.issuedAt)}</td>
                  <td>{chargerOf(invoice.sessionId)}</td>
                  <td className="num">{formatKwh(invoice.totalEnergyKwh)} kWh</td>
                  <td className="num muted">
                    {formatKwh(invoice.peakKwh)} / {formatKwh(invoice.offPeakKwh)}
                  </td>
                  <td>
                    <Pill tone={invoiceTone(invoice.status)}>{INVOICE_LABELS[invoice.status ?? 'ISSUED']}</Pill>
                  </td>
                  <td className="num strong">{formatMoney(invoice.totalAmount, invoice.currency)}</td>
                  <td className="num">
                    <ChevronRight size={18} strokeWidth={ICON_STROKE} aria-hidden className="muted" />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
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

/** Session facturee : borne, station, horaires et energie. */
function InvoiceSession({ sessionId }: { sessionId?: string }) {
  const session = useLoad(sessionId ? () => voltpark.session(sessionId) : null, `session-${sessionId}`)
  const stations = useLoad((signal) => voltpark.stations(signal), 'stations')
  if (!session.data) return <CardSkeleton lines={5} />
  const s = session.data
  const station = stations.data?.find((st) => st.chargers?.some((c) => c.id === s.chargerId))
  const peakShare = s.totalEnergyKwh ? Math.round(((s.peakKwh ?? 0) / s.totalEnergyKwh) * 100) : 0
  return (
    <aside className="card card--padded invoice-session" aria-label="Session facturée">
      <h3 className="card__title">
        <Zap size={18} strokeWidth={ICON_STROKE} aria-hidden /> Session
      </h3>
      <dl className="facts">
        <div>
          <dt>Borne</dt>
          <dd>{s.chargerId}</dd>
        </div>
        <div>
          <dt>
            <MapPin size={14} strokeWidth={ICON_STROKE} aria-hidden /> Station
          </dt>
          <dd>{station ? shortName(station.name) : '—'}</dd>
        </div>
        <div>
          <dt>Début</dt>
          <dd className="tabular">{formatDateTime(s.startedAt)}</dd>
        </div>
        <div>
          <dt>Fin</dt>
          <dd className="tabular">{formatDateTime(s.stoppedAt)}</dd>
        </div>
        <div>
          <dt>
            <Clock3 size={14} strokeWidth={ICON_STROKE} aria-hidden /> Durée
          </dt>
          <dd className="tabular">{formatDuration(durationSeconds(s))}</dd>
        </div>
        <div>
          <dt>Énergie</dt>
          <dd className="tabular">{formatKwh(s.totalEnergyKwh)} kWh</dd>
        </div>
      </dl>
      <div className="split" aria-label={`${peakShare} % en heures pleines`}>
        <div className="split__bar">
          <span className="split__peak" style={{ width: `${peakShare}%` }} />
        </div>
        <p className="muted split__legend">
          <span>Heures pleines {peakShare} %</span>
          <span>Heures creuses {100 - peakShare} %</span>
        </p>
      </div>
      <Link to="/sessions" className="btn btn--ghost btn--block">
        Mes sessions
      </Link>
    </aside>
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
        <div className="invoice-layout">
          <InvoiceDetail invoice={invoice} onUpdated={setUpdated} />
          <InvoiceSession sessionId={invoice.sessionId} />
        </div>
      )}
    </>
  )
}
