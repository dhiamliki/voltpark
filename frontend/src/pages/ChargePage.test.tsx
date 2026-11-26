import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it } from 'vitest'
import { ChargeProvider } from '../state/ChargeContext'
import { DriverProvider } from '../state/DriverContext'
import { calls, json, mockApi } from '../test/mockApi'
import { ChargePage } from './ChargePage'

const pendingSession = {
  id: 'pending-1',
  driverId: 2,
  chargerId: 'CP-175672-2',
  status: 'PENDING_START',
  startedAt: '2026-11-25T19:00:00Z',
  totalEnergyKwh: 0,
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/charge']}>
      <DriverProvider>
        <ChargeProvider>
          <ChargePage />
        </ChargeProvider>
      </DriverProvider>
    </MemoryRouter>,
  )
}

describe('Ma charge', () => {
  beforeEach(() => {
    localStorage.setItem('voltpark.driverId', '2')
  })

  it('without a charge, explains how to start one', async () => {
    mockApi()
    renderPage()
    expect(await screen.findByText('Aucune charge en cours')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Trouver une borne/ })).toHaveAttribute('href', '/')
  })

  it('PENDING_START shows retry and cancel', async () => {
    mockApi((url) => (url.includes('/sessions?driverId=2') ? json([pendingSession]) : undefined))
    renderPage()

    expect(await screen.findByRole('button', { name: /Réessayer le démarrage/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Annuler' })).toBeInTheDocument()
    expect(screen.getByText(/La borne CP-175672-2 n’a pas pu être jointe/)).toBeInTheDocument()
  })

  it('retry calls start-attempts and shows a clear banner when the badge is refused', async () => {
    const fetchMock = mockApi((url, method) => {
      if (url.includes('/sessions?driverId=2')) return json([pendingSession])
      if (method === 'POST' && url.endsWith('/sessions/pending-1/start-attempts')) {
        return json(
          { type: 'https://voltpark.tn/errors/charger-fault', status: 403, detail: 'Badge refuse', errorCode: 'BADGE_REFUSED' },
          403,
          { 'X-Request-Id': 'abcdef12-3456' },
        )
      }
      return undefined
    })
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: /Réessayer le démarrage/ }))

    expect(await screen.findByText('Badge refusé')).toBeInTheDocument()
    expect(screen.getByText(/Badge refusé par la borne\./)).toBeInTheDocument()
    expect(screen.getByText('Réf. abcdef12')).toBeInTheDocument()
    expect(calls(fetchMock, 'POST', '/sessions/pending-1/start-attempts')).toHaveLength(1)
  })
})
