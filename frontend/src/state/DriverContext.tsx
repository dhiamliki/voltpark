import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { ApiError } from '../api/client'
import { voltpark, type Driver, type DriverRequest } from '../api/voltpark'
import { readStorage, writeStorage } from '../lib/storage'

const STORAGE_KEY = 'voltpark.driverId'

/**
 * Conducteur courant. Il n'y a pas encore d'authentification : le compte est choisi sur l'ecran
 * d'accueil (ou "Changer de compte" dans le menu de l'avatar) et retenu dans localStorage.
 */
interface DriverState {
  drivers: Driver[]
  driver: Driver | null
  /** compte choisi (retenu dans localStorage), meme si la liste des conducteurs charge encore */
  selectedId: number | null
  loading: boolean
  error: ApiError | null
  selectDriver: (id: number | null) => void
  createDriver: (request: DriverRequest) => Promise<Driver>
  reload: () => void
}

const DriverContext = createContext<DriverState | null>(null)

function storedDriverId(): number | null {
  const value = Number(readStorage(STORAGE_KEY))
  return Number.isInteger(value) && value > 0 ? value : null
}

export function DriverProvider({ children }: { children: ReactNode }) {
  const [drivers, setDrivers] = useState<Driver[]>([])
  const [driverId, setDriverId] = useState<number | null>(storedDriverId)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<ApiError | null>(null)
  const [version, setVersion] = useState(0)

  useEffect(() => {
    let active = true
    voltpark
      .drivers()
      .then((list) => {
        if (!active) return
        setDrivers(list)
        setError(null)
      })
      .catch((e: unknown) => active && setError(e instanceof ApiError ? e : new ApiError(0, String(e))))
      .finally(() => active && setLoading(false))
    return () => {
      active = false
    }
  }, [version])

  // un autre onglet change de compte : celui-ci suit
  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key === STORAGE_KEY) setDriverId(storedDriverId())
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [])

  const selectDriver = useCallback((id: number | null) => {
    setDriverId(id)
    writeStorage(STORAGE_KEY, id === null ? null : String(id))
  }, [])

  const createDriver = useCallback(
    async (request: DriverRequest) => {
      const created = await voltpark.createDriver(request)
      setDrivers((list) => [...list, created])
      selectDriver(created.id ?? null)
      return created
    },
    [selectDriver],
  )

  const value = useMemo<DriverState>(
    () => ({
      drivers,
      driver: drivers.find((d) => d.id === driverId) ?? null,
      selectedId: driverId,
      loading,
      error,
      selectDriver,
      createDriver,
      reload: () => {
        setLoading(true)
        setVersion((v) => v + 1)
      },
    }),
    [drivers, driverId, loading, error, selectDriver, createDriver],
  )

  return <DriverContext.Provider value={value}>{children}</DriverContext.Provider>
}

export function useDriver(): DriverState {
  const state = useContext(DriverContext)
  if (!state) throw new Error('useDriver must be used inside DriverProvider')
  return state
}
