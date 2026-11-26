import { Link, Navigate, Route, Routes } from 'react-router'
import { Compass } from 'lucide-react'
import { Layout } from './components/Layout'
import { EmptyState, ICON_STROKE } from './components/ui'
import { ChargePage } from './pages/ChargePage'
import { HistoryPage } from './pages/HistoryPage'
import { InvoiceDetailPage } from './pages/InvoicesPage'
import { MapPage } from './pages/MapPage'
import { ReceiptPage } from './pages/ReceiptPage'
import { ReservationsPage } from './pages/ReservationsPage'
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
        <Route path="charge" element={<ChargePage />} />
        <Route path="charge/terminee/:sessionId" element={<ReceiptPage />} />
        <Route path="reservations" element={<ReservationsPage />} />
        <Route path="historique" element={<HistoryPage />} />
        <Route path="factures/:id" element={<InvoiceDetailPage />} />
        {/* anciennes adresses */}
        <Route path="sessions" element={<Navigate to="/historique" replace />} />
        <Route path="factures" element={<Navigate to="/historique?onglet=factures" replace />} />
        <Route path="parametres" element={<SettingsPage />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}
