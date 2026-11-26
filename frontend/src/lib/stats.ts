/**
 * Indicateurs du tableau de bord, calcules dans le navigateur a partir des sessions et
 * des factures du conducteur (l'API n'a pas d'endpoint de statistiques).
 */
import type { Invoice, Session } from '../api/voltpark'

/** Emissions evitees : estimation de 0,4 kg de CO2 par kWh (voiture thermique equivalente). */
export const CO2_KG_PER_KWH = 0.4

const DAY_MS = 86_400_000

function sameMonth(iso: string | undefined, now: Date): boolean {
  if (!iso) return false
  const date = new Date(iso)
  return date.getFullYear() === now.getFullYear() && date.getMonth() === now.getMonth()
}

export function isFinished(session: Session): boolean {
  return session.status === 'COMPLETED' || session.status === 'STOPPED'
}

export interface MonthSummary {
  energyKwh: number
  spent: number
  sessions: number
  co2Kg: number
  pendingInvoices: number
}

export function monthSummary(sessions: Session[], invoices: Invoice[], now = new Date()): MonthSummary {
  const monthSessions = sessions.filter((s) => isFinished(s) && sameMonth(s.stoppedAt ?? s.startedAt, now))
  const energyKwh = monthSessions.reduce((sum, s) => sum + (s.totalEnergyKwh ?? 0), 0)
  const spent = invoices
    .filter((i) => sameMonth(i.issuedAt, now) && i.status !== 'CANCELLED')
    .reduce((sum, i) => sum + (i.totalAmount ?? 0), 0)
  return {
    energyKwh,
    spent,
    sessions: monthSessions.length,
    co2Kg: energyKwh * CO2_KG_PER_KWH,
    pendingInvoices: invoices.filter((i) => i.status === 'PENDING_ENERGY').length,
  }
}

export interface DayEnergy {
  date: Date
  label: string
  energyKwh: number
}

const weekday = new Intl.DateTimeFormat('fr-FR', { weekday: 'short' })

/** Energie chargee par jour sur les 7 derniers jours (aujourd'hui compris). */
export function lastSevenDays(sessions: Session[], now = new Date()): DayEnergy[] {
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  return Array.from({ length: 7 }, (_, i) => {
    const date = new Date(today.getTime() - (6 - i) * DAY_MS)
    const next = date.getTime() + DAY_MS
    const energyKwh = sessions
      .filter((s) => isFinished(s) && s.startedAt)
      .filter((s) => {
        const t = new Date(s.startedAt as string).getTime()
        return t >= date.getTime() && t < next
      })
      .reduce((sum, s) => sum + (s.totalEnergyKwh ?? 0), 0)
    return { date, label: weekday.format(date).replace('.', ''), energyKwh }
  })
}

/** Duree d'une session en secondes (0 si elle n'est pas terminee). */
export function durationSeconds(session: Session): number {
  if (!session.startedAt || !session.stoppedAt) return 0
  return Math.max(0, (new Date(session.stoppedAt).getTime() - new Date(session.startedAt).getTime()) / 1000)
}

export function latestFinished(sessions: Session[]): Session | undefined {
  return [...sessions]
    .filter(isFinished)
    .sort((a, b) => (b.startedAt ?? '').localeCompare(a.startedAt ?? ''))[0]
}
