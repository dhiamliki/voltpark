import type { DotTone, PillTone } from '../components/ui'
import type { Charger, Invoice, Reservation, Session, Station } from '../api/voltpark'

export function chargerTone(status?: Charger['status']): DotTone {
  if (status === 'AVAILABLE') return 'available'
  if (status === 'OCCUPIED' || status === 'CHARGING') return 'occupied'
  return 'out'
}

export const CHARGER_LABELS: Record<string, string> = {
  AVAILABLE: 'Disponible',
  OCCUPIED: 'Réservée',
  CHARGING: 'En charge',
  FAULTED: 'En panne',
  UNAVAILABLE: 'Hors service',
}

export function stationCounts(station?: Station) {
  const chargers = station?.chargers ?? []
  return {
    total: chargers.length,
    available: chargers.filter((c) => c.status === 'AVAILABLE').length,
    occupied: chargers.filter((c) => c.status === 'OCCUPIED' || c.status === 'CHARGING').length,
  }
}

export const SESSION_LABELS: Record<NonNullable<Session['status']>, string> = {
  PENDING_START: 'Démarrage en attente',
  ACTIVE: 'En charge',
  STOPPED: 'Arrêtée',
  COMPLETED: 'Terminée',
  FAILED: 'Échouée',
  CANCELLED: 'Annulée',
}

export function sessionTone(status?: Session['status']): PillTone {
  if (status === 'ACTIVE') return 'accent'
  if (status === 'FAILED') return 'danger'
  return 'neutral'
}

export const INVOICE_LABELS: Record<NonNullable<Invoice['status']>, string> = {
  PENDING_ENERGY: 'En attente des mesures',
  ISSUED: 'Émise',
  PAID: 'Payée',
  CANCELLED: 'Annulée',
}

export function invoiceTone(status?: Invoice['status']): PillTone {
  if (status === 'PAID') return 'success'
  if (status === 'ISSUED') return 'accent'
  return 'neutral'
}

export const RESERVATION_LABELS: Record<NonNullable<Reservation['status']>, string> = {
  PENDING: 'En attente',
  CONFIRMED: 'Confirmée',
  ACTIVE: 'En cours',
  CANCELLED: 'Annulée',
  COMPLETED: 'Terminée',
  EXPIRED: 'Expirée',
}

/** Recherche sans accents ni majuscules. */
export function normalize(text: string): string {
  return text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
}
