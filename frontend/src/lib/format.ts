const numberFormat = new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })
const energyFormat = new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 1, maximumFractionDigits: 2 })
const moneyFormat = new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 3, maximumFractionDigits: 3 })
const dateTimeFormat = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium', timeStyle: 'short' })
const dayFormat = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' })
export const formatLongDay = (date: Date) => dayFormat.format(date)
const timeFormat = new Intl.DateTimeFormat('fr-FR', { timeStyle: 'short' })

export const formatKw = (value?: number | null) => (value == null ? '—' : numberFormat.format(value))
export const formatKwh = (value?: number | null) => (value == null ? '—' : energyFormat.format(value))
/** Montant en dinars : TND est affiche "DT", l'abreviation courante en Tunisie. */
export const formatMoney = (value?: number | null, currency = 'TND') =>
  value == null ? '—' : `${moneyFormat.format(value)} ${currency === 'TND' ? 'DT' : currency}`
const wholeFormat = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 })
export const formatWhole = (value?: number | null) => (value == null ? '—' : wholeFormat.format(value))

/** Type de connecteur deduit de la puissance : AC Type 2 jusqu'a 22 kW, DC CCS au-dela. */
export const connectorOf = (powerKw?: number | null) => (powerKw != null && powerKw > 22 ? 'CCS' : 'Type 2')
export const formatDateTime = (iso?: string | null) => (iso ? dateTimeFormat.format(new Date(iso)) : '—')
export const formatTime = (iso?: string | null) => (iso ? timeFormat.format(new Date(iso)) : '—')

/** Duree en hh:mm:ss. */
export function formatDuration(totalSeconds: number): string {
  const seconds = Math.max(0, Math.floor(totalSeconds))
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  return [h, m, s].map((part) => String(part).padStart(2, '0')).join(':')
}

export function initials(name?: string): string {
  if (!name) return '?'
  const parts = name.trim().split(/\s+/)
  return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase()
}

/** Distance a vol d'oiseau en km (formule de haversine). */
export function distanceKm(a: { lat: number; lng: number }, b: { lat: number; lng: number }): number {
  const rad = (deg: number) => (deg * Math.PI) / 180
  const dLat = rad(b.lat - a.lat)
  const dLng = rad(b.lng - a.lng)
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2
  return 6371 * 2 * Math.asin(Math.sqrt(h))
}

/** Derniere partie de l'adresse (ville), ex. "Avenue Habib Bourguiba, Tunis" -> "Tunis". */
export function cityOf(address?: string): string {
  if (!address) return ''
  const parts = address.split(',').map((part) => part.trim()).filter(Boolean)
  return parts[parts.length - 1] ?? ''
}

/** Nom de station sans le prefixe de la marque, ex. "VoltPark Sfax Centre" -> "Sfax Centre". */
export const shortName = (name?: string) => (name ?? '').replace(/^VoltPark /, '')
