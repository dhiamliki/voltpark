import { Link } from 'react-router'
import { UserRound } from 'lucide-react'
import type { ReactNode } from 'react'
import type { ApiError } from '../api/client'
import { useDriver } from '../state/DriverContext'
import { Banner, CardSkeleton, EmptyState, ICON_STROKE } from './ui'

export type StartErrorKind = 'badge' | 'charger' | 'gateway' | 'other'

/** Titre, conseil et type de refus au demarrage (403 badge refuse, 409 borne occupee ou reservee...). */
export function startErrorTitle(error: ApiError): { title: string; hint?: string; replaceMessage?: boolean; kind: StartErrorKind } {
  if (error.status === 403 || error.errorCode === 'BADGE_REFUSED') {
    return { kind: 'badge', title: 'Badge refusé', hint: 'Ce badge n’est pas autorisé sur les bornes. Changez de compte pour charger.' }
  }
  if (error.errorCode === 'CHARGER_OCCUPIED') {
    return { kind: 'charger', title: 'Borne occupée', hint: 'Choisissez une autre borne de la station, ou une autre station.' }
  }
  if (error.errorCode === 'CHARGER_RESERVED') {
    return { kind: 'charger', title: 'Borne réservée', hint: 'Choisissez une autre borne, ou réservez un créneau plus tard.' }
  }
  if (error.errorCode === 'CHARGER_OUT_OF_SERVICE') {
    return { kind: 'charger', title: 'Borne hors service', hint: 'Choisissez une autre borne de la station.' }
  }
  if (error.status === 409) {
    return { kind: 'other', title: 'Démarrage impossible' }
  }
  if (error.status === 502 || error.status === 503) {
    return {
      kind: 'gateway',
      title: 'Passerelle des bornes injoignable',
      hint: 'La session reste en attente. Réessayez dans un instant.',
      replaceMessage: true,
    }
  }
  return { kind: 'other', title: 'Le démarrage a échoué' }
}

export function StartErrorBanner({ error, actions }: { error: ApiError | null; actions?: ReactNode }) {
  if (!error) return null
  const { title, hint, replaceMessage } = startErrorTitle(error)
  return (
    <Banner tone="danger" title={title} requestId={error.requestId} actions={actions}>
      {replaceMessage ? hint : error.message}
      {hint && !replaceMessage && <> {hint}</>}
    </Banner>
  )
}

/** Les pages personnelles demandent un conducteur choisi (il n'y a pas encore d'authentification). */
export function RequireDriver({ children }: { children: (driverId: number) => ReactNode }) {
  const { driver, loading, error } = useDriver()
  if (loading) return <CardSkeleton />
  if (error) {
    return (
      <Banner tone="danger" title="Impossible de charger les conducteurs" requestId={error.requestId}>
        {error.message}
      </Banner>
    )
  }
  if (!driver?.id) {
    return (
      <div className="card">
        <EmptyState
          icon={<UserRound size={24} strokeWidth={ICON_STROKE} />}
          title="Choisissez un compte"
          text="Choisissez un compte de démonstration pour charger, réserver et voir vos factures."
          action={
            <Link to="/" className="btn btn--primary">
              Choisir un compte
            </Link>
          }
        />
      </div>
    )
  }
  return <>{children(driver.id)}</>
}
