import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { Clock3, Gauge, Square, Timer, Zap } from 'lucide-react'
import { ApiError } from '../api/client'
import { voltpark, type Session } from '../api/voltpark'
import { formatDuration, formatKw, formatKwh, formatMoney, shortName } from '../lib/format'
import { SIMULATION_FACTOR, SIMULATION_HINT, simulatedSeconds } from '../lib/simulation'
import { useCharge } from '../state/ChargeContext'
import { ConfirmDialog } from './ConfirmDialog'
import { PowerChart } from './PowerChart'
import { ProgressRing } from './ProgressRing'
import { ErrorBanner, ICON_STROKE, Pill } from './ui'

function useRealElapsed(startedAt?: string) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])
  return startedAt ? Math.max(0, (now - new Date(startedAt).getTime()) / 1000) : 0
}

/** Pastille "Simulation x60" avec une info-bulle (survol et focus clavier). */
export function SimulationChip() {
  return (
    <span className="sim-chip" tabIndex={0} aria-describedby="sim-tooltip">
      <Timer size={14} strokeWidth={ICON_STROKE} aria-hidden /> Simulation ×{SIMULATION_FACTOR}
      <span id="sim-tooltip" role="tooltip" className="sim-chip__tooltip">
        {SIMULATION_HINT}
      </span>
    </span>
  )
}

/**
 * Ma charge : anneau de batterie, puissance, energie et duree (en temps simule, pour qu'elles
 * concordent), une seule action principale "Arreter la charge" (avec confirmation), puis
 * ouverture automatique du recapitulatif "Charge terminee".
 */
export function LiveSessionPanel({ session }: { session: Session }) {
  const navigate = useNavigate()
  const { live, stationOf, refresh } = useCharge()
  const { state, latest, points } = live
  const realElapsed = useRealElapsed(session.startedAt)
  const [confirming, setConfirming] = useState(false)
  const [stopping, setStopping] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const station = stationOf(session.chargerId)
  const charger = station?.chargers?.find((c) => c.id === session.chargerId)

  const minutes = latest?.estimatedMinutesRemaining
  const ringLabel = minutes == null || minutes < 0 ? 'estimation…' : minutes === 0 ? 'chargée' : `~ ${minutes} min`

  async function stop() {
    if (!session.id) return
    setStopping(true)
    setError(null)
    try {
      await voltpark.stopSession(session.id)
      await refresh()
      navigate(`/charge/terminee/${session.id}`)
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError(0, String(e)))
      setStopping(false)
      setConfirming(false)
    }
  }

  return (
    <section className="card live live--page" aria-label="Charge en cours">
      <header className="live__header">
        <h2 className="live__title">
          <Zap size={20} strokeWidth={ICON_STROKE} aria-hidden />
          <span>{station ? shortName(station.name) : 'Charge en cours'}</span>
        </h2>
        <span className="live__meta muted">
          Borne {session.chargerId}
          {charger?.connectorType && ` · ${charger.connectorType} ${formatKw(charger.maxPowerKw)} kW`}
        </span>
        <span className="live__chips">
          <SimulationChip />
          {state === 'live' && (
            <Pill tone="accent" dot>
              En direct
            </Pill>
          )}
          {state === 'waiting' && <Pill tone="neutral">En attente des mesures</Pill>}
          {state === 'ended' && <Pill tone="neutral">Terminée</Pill>}
        </span>
      </header>

      <ErrorBanner error={error} title="L’arrêt a échoué" />

      <div className="live__body">
        <div className="live__main">
          <ProgressRing percent={latest?.socPercent ?? null} label={latest ? ringLabel : 'connexion…'} />
          <div className="live__stats">
            <div className="live__stat">
              <span className="live__stat-label">
                <Gauge size={16} strokeWidth={ICON_STROKE} aria-hidden /> Puissance
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
            <div className="live__stat" title={`Temps réel écoulé : ${formatDuration(realElapsed)}`}>
              <span className="live__stat-label">
                <Clock3 size={16} strokeWidth={ICON_STROKE} aria-hidden /> Durée de charge
              </span>
              <span className="number">{formatDuration(simulatedSeconds(realElapsed))}</span>
            </div>
          </div>
        </div>
        <button type="button" className="btn btn--primary btn--pill live__stop" onClick={() => setConfirming(true)} disabled={stopping}>
          <Square size={14} strokeWidth={ICON_STROKE} fill="currentColor" aria-hidden />
          {stopping ? 'Arrêt…' : 'Arrêter la charge'}
        </button>
      </div>

      <PowerChart points={points} />
      {station && (
        <p className="muted live__prices tabular">
          Tarif : {formatMoney(station.peakPricePerKwh)}/kWh en heures pleines, {formatMoney(station.offPeakPricePerKwh)}/kWh en
          heures creuses. La facture est créée à l’arrêt.
        </p>
      )}

      {confirming && (
        <ConfirmDialog
          title="Arrêter la charge ?"
          confirmLabel={stopping ? 'Arrêt…' : 'Arrêter la charge'}
          cancelLabel="Continuer à charger"
          busy={stopping}
          onConfirm={stop}
          onCancel={() => setConfirming(false)}
        >
          {latest ? `${formatKwh(latest.energyKwh)} kWh chargés, batterie à ${latest.socPercent ?? '—'} %. ` : ''}
          La borne est libérée et la facture est calculée avec l’énergie mesurée.
        </ConfirmDialog>
      )}
    </section>
  )
}
