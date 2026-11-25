import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../api/client'

interface Result<T> {
  key: string
  data?: T
  error?: ApiError
}

/**
 * Charge des donnees de l'API. `loading` est vrai tant que la reponse correspondant aux
 * parametres actuels (key) n'est pas arrivee ; reload() relance la requete.
 * Une requete obsolete est annulee (AbortController).
 */
export function useLoad<T>(load: ((signal: AbortSignal) => Promise<T>) | null, key: string) {
  const [version, setVersion] = useState(0)
  const [result, setResult] = useState<Result<T>>({ key: '' })
  const fullKey = `${key}#${version}`

  useEffect(() => {
    if (!load) return
    const controller = new AbortController()
    load(controller.signal)
      .then((data) => setResult({ key: fullKey, data }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) return
        setResult({ key: fullKey, error: error instanceof ApiError ? error : new ApiError(0, String(error)) })
      })
    return () => controller.abort()
    // eslint-disable-next-line react-hooks/exhaustive-deps -- la cle decrit les parametres
  }, [fullKey])

  const reload = useCallback(() => setVersion((v) => v + 1), [])
  const current = result.key === fullKey
  return {
    // on garde les dernieres donnees pendant un rechargement (pas de clignotement)
    data: result.data,
    error: current ? result.error : undefined,
    loading: Boolean(load) && !current,
    reload,
  }
}
