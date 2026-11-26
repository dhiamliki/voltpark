import type { Reservation } from '../api/voltpark'

/** Une reservation peut demarrer 15 min avant son creneau, et jusqu'a sa fin. */
export const START_WINDOW_MS = 15 * 60_000

export function canStartReservation(reservation: Reservation, now: number): boolean {
  const start = new Date(reservation.startTime ?? 0).getTime()
  const end = new Date(reservation.endTime ?? 0).getTime()
  return now >= start - START_WINDOW_MS && now < end
}

/** Prochaine reservation confirmee qui n'est pas terminee. */
export function nextReservation(reservations: Reservation[], now: number): Reservation | undefined {
  return reservations
    .filter((r) => r.status === 'CONFIRMED' && new Date(r.endTime ?? 0).getTime() > now)
    .sort((a, b) => (a.startTime ?? '').localeCompare(b.startTime ?? ''))[0]
}
