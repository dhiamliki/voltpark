import { Link } from 'react-router'
import { CalendarClock, MapPin, Zap } from 'lucide-react'
import { LiveSessionPanel } from '../components/LiveSessionPanel'
import { PendingSessionPanel } from '../components/PendingSessionPanel'
import { RequireDriver } from '../components/sessionParts'
import { CardSkeleton, EmptyState, ICON_STROKE, PageHeader } from '../components/ui'
import { pageMeta } from '../lib/pages'
import { useCharge } from '../state/ChargeContext'

function ChargeContent() {
  const { current, loaded } = useCharge()
  if (!loaded) return <CardSkeleton lines={5} />
  if (current?.status === 'ACTIVE') return <LiveSessionPanel session={current} />
  if (current?.status === 'PENDING_START') return <PendingSessionPanel session={current} />
  return (
    <div className="card">
      <EmptyState
        icon={<Zap size={24} strokeWidth={ICON_STROKE} />}
        title="Aucune charge en cours"
        text="Sur la carte, touchez une station puis « Charger maintenant » à côté d’une borne disponible."
        action={
          <div className="empty__actions">
            <Link to="/" className="btn btn--primary">
              <MapPin size={16} strokeWidth={ICON_STROKE} aria-hidden /> Trouver une borne
            </Link>
            <Link to="/reservations" className="btn btn--ghost">
              <CalendarClock size={16} strokeWidth={ICON_STROKE} aria-hidden /> Mes réservations
            </Link>
          </div>
        }
      />
    </div>
  )
}

/** Ma charge : la charge en cours en direct, un demarrage en attente, ou comment en lancer une. */
export function ChargePage() {
  const meta = pageMeta('/charge')
  return (
    <>
      <PageHeader title={meta.title} subtitle={meta.subtitle} />
      <RequireDriver>{() => <ChargeContent />}</RequireDriver>
    </>
  )
}
