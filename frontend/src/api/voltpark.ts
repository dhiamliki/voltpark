/**
 * Appels de l'API session-service (via la gateway), types generes depuis openapi.yaml
 * (npm run gen:api -> schema.d.ts).
 */
import { apiRequest } from './client'
import type { components } from './schema'

type Schemas = components['schemas']

export type Driver = Schemas['DriverResponse']
export type DriverRequest = Schemas['DriverRequest']
export type Station = Schemas['StationResponse']
export type Charger = Schemas['ChargerResponse']
export type Reservation = Schemas['ReservationResponse']
export type ReservationRequest = Schemas['ReservationRequest']
export type Session = Schemas['SessionResponse']
export type StartSessionRequest = Schemas['StartSessionRequest']
export type Invoice = Schemas['InvoiceResponse']
export type LiveProgress = Schemas['LiveProgress']

export const voltpark = {
  drivers: () => apiRequest<Driver[]>('/drivers'),
  createDriver: (body: DriverRequest) => apiRequest<Driver>('/drivers', { method: 'POST', body }),

  stations: (signal?: AbortSignal) => apiRequest<Station[]>('/stations', { signal }),

  reservations: (driverId: number) => apiRequest<Reservation[]>(`/reservations?driverId=${driverId}`),
  createReservation: (body: ReservationRequest) =>
    apiRequest<Reservation>('/reservations', { method: 'POST', body }),
  cancelReservation: (id: number) => apiRequest<void>(`/reservations/${id}`, { method: 'DELETE' }),

  sessions: (driverId: number) => apiRequest<Session[]>(`/sessions?driverId=${driverId}`),
  session: (id: string) => apiRequest<Session>(`/sessions/${encodeURIComponent(id)}`),
  startSession: (body: StartSessionRequest) => apiRequest<Session>('/sessions', { method: 'POST', body }),
  stopSession: (id: string) =>
    apiRequest<Session>(`/sessions/${encodeURIComponent(id)}`, { method: 'PATCH', body: { status: 'STOPPED' } }),
  retryStart: (id: string) =>
    apiRequest<Session>(`/sessions/${encodeURIComponent(id)}/start-attempts`, { method: 'POST' }),
  sessionInvoice: (id: string) => apiRequest<Invoice>(`/sessions/${encodeURIComponent(id)}/invoice`),

  invoices: (driverId: number) => apiRequest<Invoice[]>(`/invoices?driverId=${driverId}`),
  invoice: (id: number) => apiRequest<Invoice>(`/invoices/${id}`),
  recalculateInvoice: (id: number) =>
    apiRequest<Invoice>(`/invoices/${id}/recalculation`, { method: 'POST' }),
}
