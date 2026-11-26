import { useState } from 'react'
import { Link } from 'react-router'
import { Check, Compass, UserPlus } from 'lucide-react'
import { API_BASE_URL } from '../api/client'
import { DriverForm } from '../components/DriverForm'
import { CardSkeleton, EmptyState, ErrorBanner, ICON_STROKE, PageHeader } from '../components/ui'
import { initials } from '../lib/format'
import { useDriver } from '../state/DriverContext'

/** Parametres / Profil : choix du conducteur (pas encore d'authentification) et creation. */
export function SettingsPage() {
  const { drivers, driver, selectDriver, loading, error, reload } = useDriver()
  const [creating, setCreating] = useState(false)

  return (
    <>
      <PageHeader title="Paramètres" subtitle="Choisissez le conducteur qui utilise l’application." />
      <div className="two-columns">
        <section className="card card--padded" aria-label="Conducteur">
          <h2 className="card__title">Conducteur</h2>
          {error && (
            <ErrorBanner
              error={error}
              title="Impossible de charger les conducteurs"
              actions={<button type="button" className="btn btn--ghost btn--small" onClick={reload}>Réessayer</button>}
            />
          )}
          {loading ? (
            <CardSkeleton />
          ) : (
            <ul className="driver-cards">
              {drivers.map((d) => (
                <li key={d.id}>
                  <button
                    type="button"
                    className={`driver-card ${d.id === driver?.id ? 'is-selected' : ''}`}
                    onClick={() => selectDriver(d.id ?? null)}
                    aria-pressed={d.id === driver?.id}
                  >
                    <span className="avatar">{initials(d.name)}</span>
                    <span className="driver-card__text">
                      <strong>{d.name}</strong>
                      <span className="muted">
                        {d.email} · badge {d.badgeId}
                      </span>
                    </span>
                    {d.id === driver?.id && <Check size={18} strokeWidth={ICON_STROKE} aria-label="Sélectionné" />}
                  </button>
                </li>
              ))}
            </ul>
          )}
          {driver && (
            <Link to="/" className="btn btn--primary btn--block settings__continue">
              <Compass size={16} strokeWidth={ICON_STROKE} aria-hidden /> Continuer vers la carte
            </Link>
          )}
        </section>

        <section className="card card--padded" aria-label="Nouveau conducteur">
          <h2 className="card__title">
            <UserPlus size={20} strokeWidth={ICON_STROKE} aria-hidden /> Nouveau conducteur
          </h2>
          {creating ? (
            <DriverForm onCreated={() => setCreating(false)} onCancel={() => setCreating(false)} />
          ) : (
            <EmptyState
              icon={<UserPlus size={24} strokeWidth={ICON_STROKE} />}
              title="Ajouter un profil"
              text="Le badge doit être autorisé par les bornes (TAG-001, TAG-002 ou TAG-003 dans les données de démonstration) pour pouvoir charger."
              action={
                <button type="button" className="btn btn--ghost" onClick={() => setCreating(true)}>
                  Créer un conducteur
                </button>
              }
            />
          )}
          <p className="settings__api muted">API : {API_BASE_URL}</p>
        </section>
      </div>
    </>
  )
}
