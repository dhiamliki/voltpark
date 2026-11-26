import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DriverProvider } from '../state/DriverContext'
import { SessionsPage } from './SessionsPage'

const driver = { id: 2, name: 'Dhia Mliki', email: 'dhia@voltpark.tn', badgeId: 'TAG-002' }
const pendingSession = {
  id: 'pending-1',
  driverId: 2,
  chargerId: 'CP-175672-2',
  status: 'PENDING_START',
  startedAt: '2026-11-25T19:00:00Z',
  totalEnergyKwh: 0,
}
const pastSession = {
  id: 'done-1',
  driverId: 2,
  chargerId: 'CP-460375-1',
  status: 'COMPLETED',
  startedAt: '2026-11-24T10:00:00Z',
  totalEnergyKwh: 12.5,
}

const json = (body: unknown, status = 200, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } })

/** Faux backend : repond selon la methode et l'URL appelees. */
function mockApi(retryResponse: () => Response) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    const method = init?.method ?? 'GET'
    if (url.endsWith('/drivers')) return json([driver])
    if (url.includes('/sessions?driverId=2')) return json([pendingSession, pastSession])
    if (url.includes('/invoices?driverId=2')) return json([{ id: 7, sessionId: 'done-1', status: 'ISSUED' }])
    if (url.includes('/reservations?driverId=2')) return json([])
    if (method === 'POST' && url.endsWith('/sessions/pending-1/start-attempts')) return retryResponse()
    return json({ title: 'Not Found' }, 404)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/sessions']}>
      <DriverProvider>
        <SessionsPage />
      </DriverProvider>
    </MemoryRouter>,
  )
}

describe('SessionsPage', () => {
  beforeEach(() => {
    localStorage.setItem('voltpark.driverId', '2')
  })

  it('PENDING_START shows the retry and cancel buttons, and the history with its invoice', async () => {
    mockApi(() => json(pendingSession))
    renderPage()

    expect(await screen.findByRole('button', { name: /Réessayer le démarrage/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Annuler' })).toBeInTheDocument()
    expect(screen.getByText(/La borne CP-175672-2 n’a pas pu être jointe/)).toBeInTheDocument()

    const history = screen.getByRole('region', { name: 'Historique des sessions' })
    expect(within(history).getByText(/12,5 kWh/)).toBeInTheDocument()
    expect(within(history).getByRole('link', { name: /Facture/ })).toHaveAttribute('href', '/factures/7')
  })

  it('retry calls start-attempts and shows a clear banner when the badge is refused', async () => {
    const fetchMock = mockApi(() =>
      json(
        { type: 'https://voltpark.tn/errors/charger-fault', status: 403, detail: 'Badge refuse', errorCode: 'BADGE_REFUSED' },
        403,
        { 'X-Request-Id': 'abcdef12-3456' },
      ),
    )
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: /Réessayer le démarrage/ }))

    expect(await screen.findByText('Badge refusé')).toBeInTheDocument()
    expect(screen.getByText(/Badge refusé par la borne\./)).toBeInTheDocument()
    expect(screen.getByText('Réf. abcdef12')).toBeInTheDocument()
    expect(
      fetchMock.mock.calls.some(
        ([url, init]) => String(url).endsWith('/sessions/pending-1/start-attempts') && init?.method === 'POST',
      ),
    ).toBe(true)
  })
})
