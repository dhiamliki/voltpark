import { useState } from 'react'
import { useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { voltpark, type Reservation } from '../api/voltpark'

/** Demarre la charge a partir d'une reservation (POST /sessions), puis ouvre Mes sessions. */
export function useStartSession() {
  const navigate = useNavigate()
  const [startingId, setStartingId] = useState<number | null>(null)
  const [error, setError] = useState<ApiError | null>(null)

  async function start(reservation: Reservation) {
    if (reservation.id == null || reservation.driverId == null || !reservation.chargerId) return
    setStartingId(reservation.id)
    setError(null)
    try {
      await voltpark.startSession({
        driverId: reservation.driverId,
        chargerId: reservation.chargerId,
        reservationId: reservation.id,
      })
      navigate('/sessions')
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError(0, String(e)))
    } finally {
      setStartingId(null)
    }
  }

  return { start, startingId, error }
}
