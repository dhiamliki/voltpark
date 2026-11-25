/** localStorage peut etre indisponible (navigation privee, stockage bloque) : jamais d'exception. */
export function readStorage(key: string): string | null {
  try {
    return window.localStorage.getItem(key)
  } catch {
    return null
  }
}

export function writeStorage(key: string, value: string | null): void {
  try {
    if (value === null) {
      window.localStorage.removeItem(key)
    } else {
      window.localStorage.setItem(key, value)
    }
  } catch {
    // stockage indisponible : le choix ne sera simplement pas retenu
  }
}
