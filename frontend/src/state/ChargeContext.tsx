import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { voltpark, type Invoice, type Reservation, type Session, type Station } from '../api/voltpark'
import { useLiveSession, type LiveState } from '../lib/useLiveSession'
import type { LiveProgress } from '../api/voltpark'
import { useDriver } from './DriverContext'

/** Frequence de rafraichissement : rapide pendant une charge, plus lente sinon. */
const FAST_POLL_MS = 5_000
const SLOW_POLL_MS = 20_000
const CHANNEL = 'voltpark'

/**
 * Etat partage par toutes les pages : la charge en cours (ACTIVE ou PENDING_START) du conducteur,
 * ses reservations et factures, et les stations. Recharge au demarrage, apres chaque action,
 * a intervalle regulier, au retour sur l'onglet, et quand un autre onglet signale un changement
 * (BroadcastChannel) : un rafraichissement, le bouton retour ou un second onglet restent coherents.
 */
interface ChargeState {
  /** charge en cours ou en attente de demarrage, sinon null */
  current: Session | null
  sessions: Session[]
  reservations: Reservation[]
  invoices: Invoice[]
  stations: Station[]
  loaded: boolean
  live: { state: LiveState; latest: LiveProgress | null; points: number[] }
  /** recharge tout ; a appeler apres une action (demarrage, arret, reservation...) */
  refresh: () => Promise<void>
  stationOf: (chargerId?: string) => Station | undefined
}

const ChargeContext = createContext<ChargeState | null>(null)

function byStartDesc(a: Session, b: Session) {
  return (b.startedAt ?? '').localeCompare(a.startedAt ?? '')
}

export function ChargeProvider({ children }: { children: ReactNode }) {
  const { driver } = useDriver()
  const driverId = driver?.id
  const [data, setData] = useState<{ driverId?: number; sessions: Session[]; reservations: Reservation[]; invoices: Invoice[] }>({
    sessions: [],
    reservations: [],
    invoices: [],
  })
  const [stations, setStations] = useState<Station[]>([])
  const [loaded, setLoaded] = useState(false)
  const channel = useRef<BroadcastChannel | null>(null)

  const load = useCallback(async () => {
    const [stationList, personal] = await Promise.all([
      voltpark.stations().catch(() => null),
      driverId
        ? Promise.all([voltpark.sessions(driverId), voltpark.reservations(driverId), voltpark.invoices(driverId)]).catch(() => null)
        : Promise.resolve(null),
    ])
    if (stationList) setStations(stationList)
    if (personal) {
      const [sessions, reservations, invoices] = personal
      setData({ driverId, sessions: [...sessions].sort(byStartDesc), reservations, invoices })
    }
    setLoaded(true)
  }, [driverId])

  const refresh = useCallback(async () => {
    await load()
    channel.current?.postMessage('changed')
  }, [load])

  // premier chargement et changement de conducteur
  useEffect(() => {
    void load()
  }, [load])

  // autres onglets : rechargement quand l'un d'eux signale une action
  useEffect(() => {
    if (typeof BroadcastChannel === 'undefined') return
    const bc = new BroadcastChannel(CHANNEL)
    bc.onmessage = () => void load()
    channel.current = bc
    return () => {
      bc.close()
      channel.current = null
    }
  }, [load])

  const mine = data.driverId === driverId ? data : { sessions: [], reservations: [], invoices: [] }
  const current = mine.sessions.find((s) => s.status === 'ACTIVE') ?? mine.sessions.find((s) => s.status === 'PENDING_START') ?? null

  // rafraichissement regulier et au retour sur l'onglet
  useEffect(() => {
    const timer = setInterval(() => {
      if (document.visibilityState === 'visible') void load()
    }, current ? FAST_POLL_MS : SLOW_POLL_MS)
    const onVisible = () => document.visibilityState === 'visible' && void load()
    document.addEventListener('visibilitychange', onVisible)
    window.addEventListener('focus', onVisible)
    return () => {
      clearInterval(timer)
      document.removeEventListener('visibilitychange', onVisible)
      window.removeEventListener('focus', onVisible)
    }
  }, [load, current])

  // un seul flux SSE pour toute l'application (barre de charge, carte, Ma charge)
  const live = useLiveSession(current?.status === 'ACTIVE' ? (current.id ?? null) : null)

  const stationOf = useCallback(
    (chargerId?: string) => stations.find((s) => s.chargers?.some((c) => c.id === chargerId)),
    [stations],
  )

  const value = useMemo<ChargeState>(
    () => ({
      current,
      sessions: mine.sessions,
      reservations: mine.reservations,
      invoices: mine.invoices,
      stations,
      loaded,
      live,
      refresh,
      stationOf,
    }),
    [current, mine.sessions, mine.reservations, mine.invoices, stations, loaded, live, refresh, stationOf],
  )

  return <ChargeContext.Provider value={value}>{children}</ChargeContext.Provider>
}

export function useCharge(): ChargeState {
  const state = useContext(ChargeContext)
  if (!state) throw new Error('useCharge must be used inside ChargeProvider')
  return state
}
