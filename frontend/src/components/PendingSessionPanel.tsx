import { useState } from 'react'
import { RotateCw } from 'lucide-react'
import { ApiError } from '../api/client'
import { voltpark, type Session } from '../api/voltpark'
import { formatTime } from '../lib/format'
import { StartErrorBanner } from './sessionParts'
import { Banner, ICON_STROKE } from './ui'

/**
 * Session PENDING_START : la passerelle des bornes etait injoignable au demarrage.
 * On peut relancer le demarrage (POST /sessions/{id}/start-attempts) ou annuler.
 */
export function PendingSessionPanel({ session, onChange }: { session: Session; onChange: () => void }) {
  const [busy, setBusy] = useState<'retry' | 'cancel' | null>(null)
  const [error, setError] = useState<ApiError | null>(null)

  async function run(action: 'retry' | 'cancel') {
    if (!session.id) return
    setBusy(action)
    setError(null)
    try {
      if (action === 'retry') await voltpark.retryStart(session.id)
      else await voltpark.stopSession(session.id)
      onChange()
    } catch (e) {
      const apiError = e instanceof ApiError ? e : new ApiError(0, String(e))
      setError(apiError)
      // un refus definitif (badge, borne) passe la session en FAILED : on recharge la liste
      if (apiError.status === 403 || apiError.status === 404 || apiError.status === 409) onChange()
    } finally {
      setBusy(null)
    }
  }

  return (
    <section aria-label="Démarrage en attente">
      <Banner
        title="Démarrage en attente"
        actions={
          <>
            <button type="button" className="btn btn--primary btn--small" onClick={() => run('retry')} disabled={busy !== null}>
              <RotateCw size={16} strokeWidth={ICON_STROKE} aria-hidden />
              {busy === 'retry' ? 'Nouvel essai…' : 'Réessayer le démarrage'}
            </button>
            <button type="button" className="btn btn--ghost btn--small" onClick={() => run('cancel')} disabled={busy !== null}>
              {busy === 'cancel' ? 'Annulation…' : 'Annuler'}
            </button>
          </>
        }
      >
        La borne {session.chargerId} n’a pas pu être jointe à {formatTime(session.startedAt)}. Elle reste réservée
        pour vous : relancez le démarrage dans un instant, ou annulez pour la libérer.
      </Banner>
      <StartErrorBanner error={error} />
    </section>
  )
}
