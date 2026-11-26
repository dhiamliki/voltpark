/**
 * Les bornes sont simulees en temps accelere : une seconde reelle correspond a SIMULATION_FACTOR
 * secondes de charge (SIMULATOR_TIME_FACTOR du simulateur, 60 par defaut). Les kWh, le % de
 * batterie et la puissance sont donc ceux d'une charge "simulee" : la duree affichee l'est aussi.
 */
const configured = Number(import.meta.env.VITE_SIMULATION_FACTOR)
export const SIMULATION_FACTOR = Number.isFinite(configured) && configured >= 1 ? configured : 60

export const SIMULATION_HINT =
  `Les bornes de démonstration sont simulées en accéléré : 1 seconde réelle = ${SIMULATION_FACTOR} secondes de charge. ` +
  'Énergie, batterie et durée sont affichées en temps simulé.'

/** Duree simulee (secondes) a partir d'une duree reelle (secondes). */
export const simulatedSeconds = (realSeconds: number) => Math.max(0, realSeconds) * SIMULATION_FACTOR

/** Compte a rebours lisible : "dans 1 h 05", "dans 12 min", "maintenant". */
export function formatCountdown(ms: number): string {
  if (ms <= 30_000) return 'maintenant'
  const minutes = Math.round(ms / 60_000)
  if (minutes < 60) return `dans ${minutes} min`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  if (hours >= 24) {
    const days = Math.round(hours / 24)
    return `dans ${days} jour${days > 1 ? 's' : ''}`
  }
  return `dans ${hours} h ${String(rest).padStart(2, '0')}`
}
