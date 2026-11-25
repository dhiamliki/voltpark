import { describe, expect, it, vi } from 'vitest'
import { ApiError, apiRequest, problemToMessage, toApiError } from './client'

function problemResponse(status: number, body: object, requestId = 'req-1234-5678') {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/problem+json', 'X-Request-Id': requestId },
  })
}

describe('ProblemDetail -> ApiError', () => {
  it('400 with field errors: readable French message and errors per field', async () => {
    const error = await toApiError(
      problemResponse(400, {
        type: 'https://voltpark.tn/errors/validation-error',
        title: 'Erreur de validation',
        status: 400,
        detail: 'Donnees de requete invalides',
        invalidParams: { name: 'Le nom est obligatoire', email: "Format d'email invalide" },
      }),
    )

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(400)
    expect(error.fieldErrors).toEqual({ name: 'Le nom est obligatoire', email: "Format d'email invalide" })
    expect(error.message).toContain('Certains champs sont invalides.')
    expect(error.message).toContain('Nom : Le nom est obligatoire')
    expect(error.message).toContain("Email : Format d'email invalide")
    expect(error.requestId).toBe('req-1234-5678')
  })

  it('403 badge refused: message from the SOAP fault code', async () => {
    const error = await toApiError(
      problemResponse(403, {
        type: 'https://voltpark.tn/errors/charger-fault',
        title: 'Demarrage refuse par la borne (SOAP Fault)',
        status: 403,
        detail: 'Badge refuse: TAG-BLOCKED (Badge desactive ou bloque)',
        errorCode: 'BADGE_REFUSED',
      }),
    )

    expect(error.status).toBe(403)
    expect(error.errorCode).toBe('BADGE_REFUSED')
    expect(error.message).toBe('Badge refusé par la borne.')
  })

  it('409 from our services keeps the French detail', async () => {
    const error = await toApiError(
      problemResponse(409, {
        type: 'https://voltpark.tn/errors/reservation-conflict',
        title: 'Conflit de reservation',
        status: 409,
        detail: 'La borne CP-TUN-002 est deja reservee sur ce creneau horaire',
      }),
    )

    expect(error.status).toBe(409)
    expect(error.message).toBe('La borne CP-TUN-002 est deja reservee sur ce creneau horaire')
    expect(error.fieldErrors).toEqual({})
  })

  it('English Spring errors are replaced by a French message', () => {
    expect(problemToMessage(400, { type: 'about:blank', title: 'Bad Request', detail: 'Failed to read request' })).toBe(
      'La requête est invalide.',
    )
    expect(problemToMessage(503, undefined)).toBe('Le service est momentanément indisponible.')
  })

  it('a body that is not JSON still gives an error', async () => {
    const error = await toApiError(new Response('<html>Bad gateway</html>', { status: 502 }))
    expect(error.status).toBe(502)
    expect(error.message).toBe('Le service des bornes ne répond pas.')
  })
})

describe('apiRequest', () => {
  it('returns undefined for 204 No Content', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 204 })))
    await expect(apiRequest('/reservations/1', { method: 'DELETE' })).resolves.toBeUndefined()
  })

  it('sends JSON and parses the response', async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ id: 'abc', status: 'COMPLETED' }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    const body = await apiRequest<{ status: string }>('/sessions/abc', { method: 'PATCH', body: { status: 'STOPPED' } })

    expect(body.status).toBe('COMPLETED')
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).toMatch(/\/sessions\/abc$/)
    expect(init.method).toBe('PATCH')
    expect(init.body).toBe('{"status":"STOPPED"}')
  })

  it('a network failure becomes an ApiError with status 0', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => Promise.reject(new TypeError('Failed to fetch'))))
    await expect(apiRequest('/stations')).rejects.toMatchObject({ status: 0 })
  })
})
