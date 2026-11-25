import { Link, Route, Routes } from 'react-router'
import { Compass } from 'lucide-react'
import { Layout } from './components/Layout'
import { EmptyState, ICON_STROKE } from './components/ui'
import { InvoiceDetailPage, InvoicesPage } from './pages/InvoicesPage'
import { MapPage } from './pages/MapPage'
import { ReservationsPage } from './pages/ReservationsPage'
import { SessionsPage } from './pages/SessionsPage'
import { SettingsPage } from './pages/SettingsPage'

function NotFound() {
  return (
    <div className="card">
      <EmptyState
        icon={<Compass size={24} strokeWidth={ICON_STROKE} />}
        title="Page introuvable"
        text="Cette adresse ne correspond à aucune page."
        action={
          <Link to="/" className="btn btn--primary">
            Retour à la carte
          </Link>
        }
      />
    </div>
  )
}

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<MapPage />} />
        <Route path="sessions" element={<SessionsPage />} />
        <Route path="reservations" element={<ReservationsPage />} />
        <Route path="factures" element={<InvoicesPage />} />
        <Route path="factures/:id" element={<InvoiceDetailPage />} />
        <Route path="parametres" element={<SettingsPage />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}
