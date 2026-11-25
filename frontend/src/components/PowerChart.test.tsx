import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { PowerChart, smoothPath } from './PowerChart'

describe('PowerChart', () => {
  it('draws a smooth line and an area from sample points', () => {
    const { container } = render(<PowerChart points={[7.1, 7.4, 7.3, 7.4, 6.9, 7.2]} />)

    const line = container.querySelector('path.chart__line')
    const area = container.querySelector('path.chart__area')
    expect(line).not.toBeNull()
    expect(line?.getAttribute('d')).toMatch(/^M0\.0,[\d.]+ C/)
    // une courbe de Bezier par intervalle entre deux points
    expect(line?.getAttribute('d')?.match(/C/g)).toHaveLength(5)
    // l'aire referme la courbe jusqu'en bas du graphique
    expect(area?.getAttribute('d')).toMatch(/ Z$/)
    // axe vertical de 0 au maximum (avec une marge)
    expect(screen.getByText('0 kW')).toBeInTheDocument()
    expect(screen.getByText(/8,6 kW/)).toBeInTheDocument()
  })

  it('shows a waiting message before two points are received', () => {
    const { container } = render(<PowerChart points={[7]} />)
    expect(container.querySelector('path')).toBeNull()
    expect(screen.getByText(/mesures apparaîtront/)).toBeInTheDocument()
  })

  it('smoothPath goes through the first and last points', () => {
    const d = smoothPath([
      [0, 10],
      [50, 20],
      [100, 5],
    ])
    expect(d.startsWith('M0.0,10.0')).toBe(true)
    expect(d.endsWith('100.0,5.0')).toBe(true)
  })
})
