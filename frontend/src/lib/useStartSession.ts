import { useState } from 'react'
import { useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { voltpark, type Session } from '../api/voltpark'
import { useCharge } from '../state/ChargeContext'
import { useDriver } from '../state/DriverContext'

/**
 * Demarre une charge (POST /sessions), tout de suite (sans reservation) ou depuis une reservation.
 * ACTIVE : ouvre Ma charge. PENDING_START (passerelle des bornes injoignable) : la session est
 * renvoyee pour proposer "Reessayer" la ou l'utilisateur se trouve. Refus : `error`.
 */
export function useStartSession() {
  const navigate = useNavigate()
  const { driver } = useDriver()
  const { refresh } = useCharge()
  const [busyKey, setBusyKey] = useState<string | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [pending, setPending] = useState<Session | null>(null)

  async function start(chargerId: string, reservationId?: number): Promise<Session | null> {
    if (driver?.id == null) return null
    setBusyKey(reservationId != null ? `reservation-${reservationId}` : chargerId)
    setError(null)
    setPending(null)
    try {
      const session = await voltpark.startSession({ driverId: driver.id, chargerId, reservationId })
      await refresh()
      if (session.status === 'PENDING_START') setPending(session)
      else navigate('/charge')
      return session
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError(0, String(e)))
      void refresh()
      return null
    } finally {
      setBusyKey(null)
    }
  }

  /** Nouvel essai d'une session PENDING_START (POST /sessions/{id}/start-attempts). */
  async function retry(session: Session) {
    if (!session.id) return
    setBusyKey(`retry-${session.id}`)
    setError(null)
    try {
      const started = await voltpark.retryStart(session.id)
      await refresh()
      if (started.status === 'ACTIVE') {
        setPending(null)
        navigate('/charge')
      }
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError(0, String(e)))
      void refresh()
    } finally {
      setBusyKey(null)
    }
  }

  function reset() {
    setError(null)
    setPending(null)
  }

  return { start, retry, reset, busyKey, busy: busyKey !== null, error, pending }
}
