import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import '@fontsource/inter/400.css'
import '@fontsource/inter/500.css'
import '@fontsource/inter/600.css'
import 'leaflet/dist/leaflet.css'
import './styles/theme.css'
import './styles/app.css'
import './styles/pages.css'
import './styles/journey.css'
import { App } from './App'
import { ChargeProvider } from './state/ChargeContext'
import { DriverProvider } from './state/DriverContext'
import { SearchProvider } from './state/SearchContext'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <DriverProvider>
        <ChargeProvider>
          <SearchProvider>
            <App />
          </SearchProvider>
        </ChargeProvider>
      </DriverProvider>
    </BrowserRouter>
  </StrictMode>,
)
