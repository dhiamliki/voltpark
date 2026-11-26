import { describe, expect, it } from 'vitest'
import type { Station } from '../api/voltpark'
import { A1_PATH, matchesFilter } from './regions'

const station = (latitude: number, longitude: number, corridor?: string): Station => ({ latitude, longitude, corridor })

// positions reelles (Open Charge Map)
const sidiKhelifa = station(36.247161607668616, 10.425836973245396, 'A1')
const aouina = station(36.84922376026216, 10.265912540199224)
const sfaxCentre = station(34.73209689537957, 10.764369402374655)
const agareb = station(34.77263492260646, 10.611341261111761, 'A1')

describe('filtres de la carte', () => {
  it('"Toutes" garde toutes les stations', () => {
    expect([sidiKhelifa, aouina, sfaxCentre].every((s) => matchesFilter(s, 'all'))).toBe(true)
  })

  it('"Autoroute A1" garde les stations rattachees au corridor', () => {
    expect(matchesFilter(sidiKhelifa, 'a1')).toBe(true)
    expect(matchesFilter(agareb, 'a1')).toBe(true)
    expect(matchesFilter(aouina, 'a1')).toBe(false)
  })

  it('"Grand Tunis" et "Sfax" filtrent par distance au centre-ville', () => {
    expect(matchesFilter(aouina, 'tunis')).toBe(true)
    expect(matchesFilter(sidiKhelifa, 'tunis')).toBe(false)
    expect(matchesFilter(sfaxCentre, 'sfax')).toBe(true)
    expect(matchesFilter(agareb, 'sfax')).toBe(true)
    expect(matchesFilter(aouina, 'sfax')).toBe(false)
  })

  it('le trace de l\'A1 va de Tunis a Sfax', () => {
    expect(A1_PATH.length).toBeGreaterThanOrEqual(10)
    expect(A1_PATH[0][0]).toBeGreaterThan(36.7)
    expect(A1_PATH[A1_PATH.length - 1][0]).toBeLessThan(34.8)
  })
})
