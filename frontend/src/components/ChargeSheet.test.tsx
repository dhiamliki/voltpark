import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Charger, Station } from '../api/voltpark'
import { ChargeProvider } from '../state/ChargeContext'
import { DriverProvider } from '../state/DriverContext'
import { calls, json, mockApi, station } from '../test/mockApi'
import { ChargeSheet } from './ChargeSheet'

function renderSheet(onChooseAnother = vi.fn()) {
  const s = station as Station
  return render(
    <MemoryRouter initialEntries={['/']}>
      <DriverProvider>
        <ChargeProvider>
          <Routes>
            <Route
              path="/"
              element={<ChargeSheet station={s} charger={s.chargers![0] as Charger} onClose={vi.fn()} onChooseAnother={onChooseAnother} />}
            />
            <Route path="/charge" element={<p>Page Ma charge</p>} />
          </Routes>
        </ChargeProvider>
      </DriverProvider>
    </MemoryRouter>,
  )
}

describe('ChargeSheet (Charger maintenant)', () => {
  beforeEach(() => {
    localStorage.setItem('voltpark.driverId', '2')
  })

  it('shows the charger, prices and badge, then starts without reservation and opens Ma charge', async () => {
    const fetchMock = mockApi((url, method) =>
      method === 'POST' && url.endsWith('/sessions')
        ? json({ id: 's-1', driverId: 2, chargerId: 'CP-460375-1', status: 'ACTIVE' }, 201)
        : undefined,
    )
    renderSheet()

    expect(screen.getByRole('dialog', { name: 'Shell Sidi Khelifa - Direction Tunis' })).toBeInTheDocument()
    expect(screen.getByText('CP-460375-1')).toBeInTheDocument()
    expect(screen.getByText('CCS')).toBeInTheDocument()
    expect(screen.getByText('0,600 DT/kWh')).toBeInTheDocument()
    expect(await screen.findByText('TAG-002')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /Démarrer la charge/ }))

    expect(await screen.findByText('Page Ma charge')).toBeInTheDocument()
    const [, init] = calls(fetchMock, 'POST', '/sessions')[0]
    expect(JSON.parse(String(init?.body))).toEqual({ driverId: 2, chargerId: 'CP-460375-1' })
  })

  it('a charger reserved by someone else is explained inside the sheet with the next action', async () => {
    mockApi((url, method) =>
      method === 'POST' && url.endsWith('/sessions')
        ? json(
            {
              type: 'https://voltpark.tn/errors/charger-unavailable',
              status: 409,
              detail: 'La borne CP-460375-1 est reservee par un autre conducteur a partir de 18:30. Choisissez une autre borne.',
              errorCode: 'CHARGER_RESERVED',
            },
            409,
          )
        : undefined,
    )
    const onChooseAnother = vi.fn()
    renderSheet(onChooseAnother)
    await screen.findByText('TAG-002')

    await userEvent.click(screen.getByRole('button', { name: /Démarrer la charge/ }))

    expect(await screen.findByText('Borne réservée')).toBeInTheDocument()
    expect(screen.getByText(/reservee par un autre conducteur a partir de 18:30/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Démarrer la charge/ })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Choisir une autre borne' }))
    expect(onChooseAnother).toHaveBeenCalled()
  })

  it('a refused badge proposes to change account', async () => {
    mockApi((url, method) =>
      method === 'POST' && url.endsWith('/sessions')
        ? json({ type: 'https://voltpark.tn/errors/charger-fault', status: 403, detail: 'Badge refuse', errorCode: 'BADGE_REFUSED' }, 403)
        : undefined,
    )
    renderSheet()
    await screen.findByText('TAG-002')

    await userEvent.click(screen.getByRole('button', { name: /Démarrer la charge/ }))

    expect(await screen.findByText('Badge refusé')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Changer de compte' })).toBeInTheDocument()
  })

  it('PENDING_START stays in the sheet with Réessayer', async () => {
    const fetchMock = mockApi((url, method) => {
      if (method === 'POST' && url.endsWith('/sessions')) {
        return json({ id: 'p-1', driverId: 2, chargerId: 'CP-460375-1', status: 'PENDING_START' }, 201)
      }
      if (method === 'POST' && url.endsWith('/sessions/p-1/start-attempts')) {
        return json({ id: 'p-1', driverId: 2, chargerId: 'CP-460375-1', status: 'ACTIVE' })
      }
      return undefined
    })
    renderSheet()
    await screen.findByText('TAG-002')

    await userEvent.click(screen.getByRole('button', { name: /Démarrer la charge/ }))
    expect(await screen.findByText('Démarrage en attente')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /Réessayer/ }))
    expect(await screen.findByText('Page Ma charge')).toBeInTheDocument()
    expect(calls(fetchMock, 'POST', '/sessions/p-1/start-attempts')).toHaveLength(1)
  })
})
