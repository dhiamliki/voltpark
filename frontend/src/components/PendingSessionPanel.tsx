import { useState } from 'react'
import { RotateCw } from 'lucide-react'
import { ApiError } from '../api/client'
import { voltpark, type Session } from '../api/voltpark'
import { formatTime } from '../lib/format'
import { useStartSession } from '../lib/useStartSession'
import { useCharge } from '../state/ChargeContext'
import { StartErrorBanner } from './sessionParts'
import { Banner, ErrorBanner, ICON_STROKE } from './ui'

/**
 * Session PENDING_START : la passerelle des bornes etait injoignable au demarrage.
 * On peut relancer le demarrage (POST /sessions/{id}/start-attempts) ou annuler (borne liberee).
 */
export function PendingSessionPanel({ session }: { session: Session }) {
  const { refresh } = useCharge()
  const { retry, busyKey, error } = useStartSession()
  const [cancelling, setCancelling] = useState(false)
  const [cancelError, setCancelError] = useState<ApiError | null>(null)

  async function cancel() {
    if (!session.id) return
    setCancelling(true)
    setCancelError(null)
    try {
      await voltpark.stopSession(session.id)
      await refresh()
    } catch (e) {
      setCancelError(e instanceof ApiError ? e : new ApiError(0, String(e)))
    } finally {
      setCancelling(false)
    }
  }

  const busy = busyKey !== null || cancelling
  return (
    <section aria-label="Démarrage en attente" className="stack">
      <Banner
        title="Démarrage en attente"
        actions={
          <>
            <button type="button" className="btn btn--primary btn--small" onClick={() => retry(session)} disabled={busy}>
              <RotateCw size={16} strokeWidth={ICON_STROKE} aria-hidden />
              {busyKey ? 'Nouvel essai…' : 'Réessayer le démarrage'}
            </button>
            <button type="button" className="btn btn--ghost btn--small" onClick={cancel} disabled={busy}>
              {cancelling ? 'Annulation…' : 'Annuler'}
            </button>
          </>
        }
      >
        La borne {session.chargerId} n’a pas pu être jointe à {formatTime(session.startedAt)}. Elle reste gardée
        pour vous : relancez le démarrage dans un instant, ou annulez pour la libérer.
      </Banner>
      <StartErrorBanner error={error} />
      <ErrorBanner error={cancelError} title="Annulation impossible" />
    </section>
  )
}
