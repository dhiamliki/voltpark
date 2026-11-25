/**
 * Client HTTP unique de l'application : toutes les requetes passent par l'API Gateway.
 * Les erreurs au format ProblemDetail (RFC 7807) deviennent des ApiError avec un message en francais.
 */

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1').replace(/\/+$/, '')

/** Corps d'erreur renvoye par session-service et la gateway (application/problem+json). */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  timestamp?: string
  /** erreurs de validation : champ -> message */
  invalidParams?: Record<string, string>
  /** code de la SOAP Fault de la borne */
  errorCode?: string
}

export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>
  readonly errorCode?: string
  readonly requestId?: string
  readonly problem?: ProblemDetail

  constructor(status: number, message: string, options: {
    fieldErrors?: Record<string, string>
    errorCode?: string
    requestId?: string
    problem?: ProblemDetail
  } = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = options.fieldErrors ?? {}
    this.errorCode = options.errorCode
    this.requestId = options.requestId
    this.problem = options.problem
  }
}

const STATUS_MESSAGES: Record<number, string> = {
  400: 'La requête est invalide.',
  403: 'Action refusée.',
  404: 'Élément introuvable.',
  405: 'Cette action n’est pas possible ici.',
  409: 'Action impossible dans l’état actuel.',
  502: 'Le service des bornes ne répond pas.',
  503: 'Le service est momentanément indisponible.',
  504: 'Le service a mis trop de temps à répondre.',
}

/** Refus de la borne (SOAP Fault), tels que renvoyes dans errorCode. */
const ERROR_CODE_MESSAGES: Record<string, string> = {
  BADGE_REFUSED: 'Badge refusé par la borne.',
  CHARGER_OCCUPIED: 'La borne est déjà occupée.',
  CHARGER_NOT_FOUND: 'Cette borne est inconnue de la passerelle.',
  TRANSACTION_MISMATCH: 'La borne n’a pas reconnu la transaction.',
}

const FIELD_LABELS: Record<string, string> = {
  name: 'Nom',
  email: 'Email',
  badgeId: 'Badge',
  phoneNumber: 'Téléphone',
  driverId: 'Conducteur',
  chargerId: 'Borne',
  reservationId: 'Réservation',
  connectorId: 'Connecteur',
  startTime: 'Début',
  endTime: 'Fin',
  status: 'Statut',
}

export function fieldLabel(field: string): string {
  return FIELD_LABELS[field] ?? field
}

/**
 * Message lisible a partir d'un ProblemDetail.
 * Les messages de nos services (type https://voltpark.tn/...) sont deja en francais ;
 * ceux produits par Spring (JSON illisible, URL inconnue...) sont en anglais : on les remplace.
 */
export function problemToMessage(status: number, problem?: ProblemDetail): string {
  const fields = problem?.invalidParams ?? {}
  const fieldNames = Object.keys(fields)
  if (fieldNames.length > 0) {
    const details = fieldNames.map((field) => `${fieldLabel(field)} : ${fields[field]}`).join(' · ')
    return `Certains champs sont invalides. ${details}`
  }
  if (problem?.errorCode && ERROR_CODE_MESSAGES[problem.errorCode]) {
    return ERROR_CODE_MESSAGES[problem.errorCode]
  }
  if (problem?.detail && problem.type?.startsWith('https://voltpark.tn/')) {
    return problem.detail
  }
  return STATUS_MESSAGES[status] ?? (status >= 500 ? 'Le service a rencontré une erreur.' : 'Une erreur inattendue est survenue.')
}

/** Transforme une reponse en erreur (corps ProblemDetail si present). */
export async function toApiError(response: Response): Promise<ApiError> {
  let problem: ProblemDetail | undefined
  try {
    const text = await response.text()
    problem = text ? (JSON.parse(text) as ProblemDetail) : undefined
  } catch {
    problem = undefined
  }
  return new ApiError(response.status, problemToMessage(response.status, problem), {
    fieldErrors: problem?.invalidParams,
    errorCode: problem?.errorCode,
    requestId: response.headers.get('X-Request-Id') ?? undefined,
    problem,
  })
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE'
  body?: unknown
  signal?: AbortSignal
}

/** Requete JSON vers l'API. Renvoie undefined pour 204 No Content. */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method: options.method ?? 'GET',
      headers: {
        Accept: 'application/json',
        ...(options.body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      },
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
      signal: options.signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error
    }
    throw new ApiError(0, 'Impossible de joindre le serveur. Vérifiez que la plateforme est démarrée.')
  }
  if (!response.ok) {
    throw await toApiError(response)
  }
  if (response.status === 204) {
    return undefined as T
  }
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}
