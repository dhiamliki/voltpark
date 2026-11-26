import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { App } from './App'
import { ChargeProvider } from './state/ChargeContext'
import { DriverProvider } from './state/DriverContext'
import { SearchProvider } from './state/SearchContext'
import { json, mockApi } from './test/mockApi'

function renderApp(path = '/historique') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <DriverProvider>
        <ChargeProvider>
          <SearchProvider>
            <App />
          </SearchProvider>
        </ChargeProvider>
      </DriverProvider>
    </MemoryRouter>,
  )
}

describe('parcours', () => {
  it('first visit shows the welcome screen; choosing a demo account opens the app', async () => {
    mockApi()
    renderApp()

    expect(screen.getByRole('heading', { name: 'Bienvenue sur VoltPark' })).toBeInTheDocument()
    await userEvent.click(await screen.findByRole('button', { name: /Dhia Mliki/ }))

    expect(localStorage.getItem('voltpark.driverId')).toBe('2')
    expect(screen.getAllByRole('link', { name: /Historique et factures/ }).length).toBeGreaterThan(0)
    expect(screen.queryByText(/Changer de conducteur/)).not.toBeInTheDocument()
  })

  it('an active charge shows the sticky bar on other pages', async () => {
    localStorage.setItem('voltpark.driverId', '2')
    mockApi((url) =>
      url.includes('/sessions?driverId=2')
        ? json([{ id: 'a-1', driverId: 2, chargerId: 'CP-460375-1', status: 'ACTIVE', startedAt: new Date().toISOString() }])
        : undefined,
    )
    renderApp('/historique')

    const bar = await screen.findByRole('link', { name: /Charge en cours à Shell Sidi Khelifa/ })
    expect(bar).toHaveAttribute('href', '/charge')
  })

  it('the history page merges charges and invoices in two tabs', async () => {
    localStorage.setItem('voltpark.driverId', '2')
    mockApi()
    renderApp('/historique')

    expect(await screen.findByRole('tab', { name: /Charges/ })).toHaveAttribute('aria-selected', 'true')
    await userEvent.click(screen.getByRole('tab', { name: /Factures/ }))
    expect(screen.getByRole('tab', { name: /Factures/ })).toHaveAttribute('aria-selected', 'true')
    expect(await screen.findByText('Aucune facture pour l’instant')).toBeInTheDocument()
  })
})
