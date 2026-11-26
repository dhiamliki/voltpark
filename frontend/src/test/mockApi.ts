import { vi } from 'vitest'

export const json = (body: unknown, status = 200, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } })

export const driver = { id: 2, name: 'Dhia Mliki', email: 'dhia@voltpark.tn', badgeId: 'TAG-002' }

export const station = {
  id: 12,
  code: 'ST-OCM-460375',
  name: 'Shell Sidi Khelifa - Direction Tunis',
  address: 'Autoroute A1 direction Tunis, Ain Rahma',
  corridor: 'A1',
  latitude: 36.247,
  longitude: 10.426,
  peakPricePerKwh: 0.6,
  offPeakPricePerKwh: 0.4,
  chargers: [
    { id: 'CP-460375-1', stationId: 12, status: 'AVAILABLE', maxPowerKw: 50, connectorType: 'CCS' },
    { id: 'CP-460375-2', stationId: 12, status: 'OCCUPIED', maxPowerKw: 50, connectorType: 'CHAdeMO' },
  ],
}

type Handler = (url: string, method: string, body: unknown) => Response | undefined

/**
 * Faux backend : `handler` repond aux appels du test, sinon des valeurs par defaut
 * (un conducteur, une station, aucune session, reservation ni facture).
 */
export function mockApi(handler: Handler = () => undefined) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    const method = init?.method ?? 'GET'
    const body = init?.body ? JSON.parse(String(init.body)) : undefined
    const custom = handler(url, method, body)
    if (custom) return custom
    if (url.endsWith('/drivers')) return json([driver, { id: 4, name: 'Youssef Trabelsi', badgeId: 'TAG-BLOCKED' }])
    if (url.endsWith('/stations')) return json([station])
    if (url.includes('/sessions?driverId=')) return json([])
    if (url.includes('/reservations?driverId=')) return json([])
    if (url.includes('/invoices?driverId=')) return json([])
    return json({ title: 'Not Found' }, 404)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

export const calls = (fetchMock: ReturnType<typeof mockApi>, method: string, suffix: string) =>
  fetchMock.mock.calls.filter(([url, init]) => String(url).endsWith(suffix) && (init?.method ?? 'GET') === method)
