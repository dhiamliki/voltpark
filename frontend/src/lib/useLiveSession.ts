import { useEffect, useState } from 'react'
import { API_BASE_URL } from '../api/client'
import type { LiveProgress } from '../api/voltpark'

/** Nombre de points gardes pour le graphique de puissance. */
export const MAX_POINTS = 120
const RETRY_DELAY_MS = 3000

export type LiveState = 'connecting' | 'waiting' | 'live' | 'ended' | 'unsupported'

interface LiveData {
  sessionId: string | null
  state: LiveState
  latest: LiveProgress | null
  points: number[]
}

/**
 * Suivi en direct d'une session : EventSource sur GET /api/v1/sessions/{id}/live.
 * session-service relaie le flux gRPC WatchSession d'energy-service en Server-Sent Events :
 *   progress -> une mesure (puissance, energie, batterie, temps restant)
 *   end      -> session cloturee
 *   error    -> pas encore de mesure : on se reconnecte quelques secondes plus tard
 * Fermer l'EventSource (page quittee) coupe la connexion HTTP : le serveur annule l'appel gRPC.
 */
export function useLiveSession(sessionId: string | null) {
  const [data, setData] = useState<LiveData>({ sessionId: null, state: 'connecting', latest: null, points: [] })

  useEffect(() => {
    if (!sessionId || typeof EventSource === 'undefined') return
    let source: EventSource | null = null
    let retryTimer: ReturnType<typeof setTimeout> | undefined
    let closed = false

    const connect = () => {
      source = new EventSource(`${API_BASE_URL}/sessions/${encodeURIComponent(sessionId)}/live`)

      source.addEventListener('progress', (event) => {
        const progress = JSON.parse((event as MessageEvent<string>).data) as LiveProgress
        setData((previous) => ({
          sessionId,
          state: 'live',
          latest: progress,
          points: [...(previous.sessionId === sessionId ? previous.points : []), progress.powerKw ?? 0].slice(-MAX_POINTS),
        }))
      })

      source.addEventListener('end', () => {
        source?.close()
        setData((previous) => ({ ...previous, sessionId, state: 'ended' }))
      })

      // evenement "error" envoye par le serveur (avec data) ou erreur de connexion (sans data)
      source.addEventListener('error', (event) => {
        const fromServer = event instanceof MessageEvent && Boolean(event.data)
        if (fromServer || source?.readyState === EventSource.CLOSED) {
          source?.close()
          setData((previous) => ({ ...previous, sessionId, state: previous.state === 'live' ? 'live' : 'waiting' }))
          if (!closed) retryTimer = setTimeout(connect, RETRY_DELAY_MS)
        }
        // sinon EventSource se reconnecte tout seul
      })
    }

    connect()
    return () => {
      closed = true
      clearTimeout(retryTimer)
      source?.close()
    }
  }, [sessionId])

  if (typeof EventSource === 'undefined') {
    return { state: 'unsupported' as LiveState, latest: null, points: [] as number[] }
  }
  if (data.sessionId !== sessionId) {
    return { state: 'connecting' as LiveState, latest: null, points: [] as number[] }
  }
  return { state: data.state, latest: data.latest, points: data.points }
}
