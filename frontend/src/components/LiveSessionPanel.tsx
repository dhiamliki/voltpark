import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { Clock3, Square, Zap } from 'lucide-react'
import { ApiError } from '../api/client'
import { voltpark, type Session } from '../api/voltpark'
import { formatDuration, formatKw, formatKwh } from '../lib/format'
import { useLiveSession } from '../lib/useLiveSession'
import { PowerChart } from './PowerChart'
import { ProgressRing } from './ProgressRing'
import { ErrorBanner, ICON_STROKE, Pill } from './ui'

function useElapsed(startedAt?: string) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])
  return startedAt ? (now - new Date(startedAt).getTime()) / 1000 : 0
}

/**
 * Session en direct : anneau de batterie, puissance, energie, duree, bouton d'arret
 * et graphique de puissance, alimentes par le flux SSE /sessions/{id}/live.
 */
export function LiveSessionPanel({ session, compact = false }: { session: Session; compact?: boolean }) {
  const navigate = useNavigate()
  const { state, latest, points } = useLiveSession(session.id ?? null)
  const elapsed = useElapsed(session.startedAt)
  const [stopping, setStopping] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)

  const minutes = latest?.estimatedMinutesRemaining
  const ringLabel = minutes == null || minutes < 0 ? 'estimation…' : minutes === 0 ? 'chargée' : `~ ${minutes} min`

  async function stop() {
    if (!session.id) return
    setStopping(true)
    setError(null)
    try {
      await voltpark.stopSession(session.id)
      const invoice = await voltpark.sessionInvoice(session.id).catch(() => null)
      navigate(invoice?.id ? `/factures/${invoice.id}` : '/sessions')
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError(0, String(e)))
      setStopping(false)
    }
  }

  return (
    <section className={`card live ${compact ? 'live--compact' : ''}`} aria-label="Session en direct">
      <header className="live__header">
        <h2 className="live__title">
          <Zap size={20} strokeWidth={ICON_STROKE} aria-hidden />
          <span className="live__title-desktop">Session en direct</span>
          <span className="live__title-mobile">Votre session</span>
        </h2>
        <span className="live__meta muted">Borne {session.chargerId}</span>
        {state === 'live' && (
          <Pill tone="accent" dot>
            Live
          </Pill>
        )}
        {state === 'waiting' && <Pill tone="neutral">En attente des mesures</Pill>}
        {state === 'ended' && <Pill tone="neutral">Terminée</Pill>}
      </header>

      <ErrorBanner error={error} title="L’arrêt a échoué" />

      <div className="live__body">
        <div className="live__main">
          <ProgressRing percent={latest?.socPercent ?? null} label={latest ? ringLabel : 'connexion…'} />
          <div className="live__stats">
            <div className="live__stat">
              <span className="live__stat-label">
                <Zap size={16} strokeWidth={ICON_STROKE} aria-hidden /> Puissance
              </span>
              <span className="number">
                {formatKw(latest?.powerKw)} <span className="unit">kW</span>
              </span>
            </div>
            <div className="live__stat">
              <span className="live__stat-label">
                <Zap size={16} strokeWidth={ICON_STROKE} aria-hidden /> Énergie
              </span>
              <span className="number">
                {formatKwh(latest?.energyKwh)} <span className="unit">kWh</span>
              </span>
            </div>
            <div className="live__stat">
              <span className="live__stat-label">
                <Clock3 size={16} strokeWidth={ICON_STROKE} aria-hidden /> Durée
              </span>
              <span className="number">{formatDuration(elapsed)}</span>
            </div>
          </div>
        </div>
        <button type="button" className="btn btn--outline btn--pill live__stop" onClick={stop} disabled={stopping}>
          <Square size={14} strokeWidth={ICON_STROKE} fill="currentColor" aria-hidden />
          {stopping ? 'Arrêt…' : 'Arrêter la charge'}
        </button>
      </div>

      {!compact && <PowerChart points={points} />}
    </section>
  )
}
