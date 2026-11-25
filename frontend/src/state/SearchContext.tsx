import { createContext, useContext, useState, type ReactNode } from 'react'

/** Texte du champ "Rechercher une station" de la barre du haut, utilise par la carte. */
const SearchContext = createContext<{ query: string; setQuery: (value: string) => void }>({
  query: '',
  setQuery: () => {},
})

export function SearchProvider({ children }: { children: ReactNode }) {
  const [query, setQuery] = useState('')
  return <SearchContext.Provider value={{ query, setQuery }}>{children}</SearchContext.Provider>
}

export const useSearch = () => useContext(SearchContext)
