import { describe, expect, it } from 'vitest'
import type { Reservation } from '../api/voltpark'
import { pageMeta } from './pages'
import { canStartReservation, nextReservation } from './reservations'
import { formatCountdown, SIMULATION_FACTOR, simulatedSeconds } from './simulation'

const at = (iso: string) => new Date(iso).getTime()
const reservation = (start: string, end: string, status: Reservation['status'] = 'CONFIRMED'): Reservation => ({
  id: 1,
  chargerId: 'CP-460375-1',
  startTime: start,
  endTime: end,
  status,
})

describe('reservations', () => {
  const slot = reservation('2026-11-26T18:00:00Z', '2026-11-26T19:00:00Z')

  it('can start 15 min before the slot and until its end', () => {
    expect(canStartReservation(slot, at('2026-11-26T17:44:00Z'))).toBe(false)
    expect(canStartReservation(slot, at('2026-11-26T17:45:00Z'))).toBe(true)
    expect(canStartReservation(slot, at('2026-11-26T18:30:00Z'))).toBe(true)
    expect(canStartReservation(slot, at('2026-11-26T19:00:00Z'))).toBe(false)
  })

  it('next reservation is the earliest confirmed one not finished', () => {
    const later = reservation('2026-11-27T09:00:00Z', '2026-11-27T10:00:00Z')
    const cancelled = reservation('2026-11-26T17:00:00Z', '2026-11-26T20:00:00Z', 'CANCELLED')
    expect(nextReservation([later, cancelled, slot], at('2026-11-26T12:00:00Z'))).toBe(slot)
    expect(nextReservation([later, slot], at('2026-11-26T19:30:00Z'))).toBe(later)
  })
})

describe('simulation and countdown', () => {
  it('elapsed time is shown in simulated time', () => {
    expect(SIMULATION_FACTOR).toBe(60)
    expect(simulatedSeconds(30)).toBe(1800)
  })

  it('countdown is readable', () => {
    expect(formatCountdown(10_000)).toBe('maintenant')
    expect(formatCountdown(12 * 60_000)).toBe('dans 12 min')
    expect(formatCountdown(65 * 60_000)).toBe('dans 1 h 05')
    expect(formatCountdown(49 * 3_600_000)).toBe('dans 2 jours')
  })

  it('every page has a title and a one-line purpose', () => {
    for (const path of ['/', '/charge', '/charge/terminee/x', '/reservations', '/historique', '/factures/3', '/parametres']) {
      expect(pageMeta(path).subtitle).not.toBe('')
    }
    expect(pageMeta('/historique').title).toBe('Historique et factures')
  })
})
