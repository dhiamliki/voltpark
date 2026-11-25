import { Link } from 'react-router'
import { UserRound } from 'lucide-react'
import type { ReactNode } from 'react'
import type { ApiError } from '../api/client'
import { useDriver } from '../state/DriverContext'
import { Banner, CardSkeleton, EmptyState, ICON_STROKE } from './ui'

/** Titre et conseil pour un refus au demarrage (403 badge refuse, 409 borne occupee...). */
export function startErrorTitle(error: ApiError): { title: string; hint?: string; replaceMessage?: boolean } {
  if (error.status === 403 || error.errorCode === 'BADGE_REFUSED') {
    return { title: 'Badge refusé', hint: 'Ce badge n’est pas autorisé sur les bornes. Choisissez un autre conducteur dans Paramètres.' }
  }
  if (error.errorCode === 'CHARGER_OCCUPIED') {
    return { title: 'Borne occupée', hint: 'Choisissez une autre borne ou réessayez dans quelques minutes.' }
  }
  if (error.status === 409) {
    return { title: 'Démarrage impossible' }
  }
  if (error.status === 502 || error.status === 503) {
    return {
      title: 'Passerelle des bornes injoignable',
      hint: 'La session reste en attente. Réessayez dans un instant.',
      replaceMessage: true,
    }
  }
  return { title: 'Le démarrage a échoué' }
}

export function StartErrorBanner({ error }: { error: ApiError | null }) {
  if (!error) return null
  const { title, hint, replaceMessage } = startErrorTitle(error)
  return (
    <Banner tone="danger" title={title} requestId={error.requestId}>
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
          title="Choisissez un conducteur"
          text="Sélectionnez votre profil ou créez-en un pour voir vos réservations, sessions et factures."
          action={
            <Link to="/parametres" className="btn btn--primary">
              Choisir un conducteur
            </Link>
          }
        />
      </div>
    )
  }
  return <>{children(driver.id)}</>
}
