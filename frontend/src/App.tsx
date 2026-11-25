import { Route, Routes } from 'react-router'
import { Construction } from 'lucide-react'
import { Layout } from './components/Layout'
import { EmptyState, ICON_STROKE, PageHeader } from './components/ui'

function Placeholder({ title }: { title: string }) {
  return (
    <>
      <PageHeader title={title} />
      <div className="card">
        <EmptyState icon={<Construction size={24} strokeWidth={ICON_STROKE} />} title="Page en préparation" />
      </div>
    </>
  )
}

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Placeholder title="Carte" />} />
        <Route path="sessions" element={<Placeholder title="Mes sessions" />} />
        <Route path="reservations" element={<Placeholder title="Réservations" />} />
        <Route path="factures" element={<Placeholder title="Factures" />} />
        <Route path="parametres" element={<Placeholder title="Paramètres" />} />
        <Route path="*" element={<Placeholder title="Page introuvable" />} />
      </Route>
    </Routes>
  )
}
