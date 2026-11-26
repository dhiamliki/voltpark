import { useState } from 'react'
import { useNavigate } from 'react-router'
import { ArrowRight, BadgeCheck, MapPin, Play, ReceiptText, ShieldAlert, UserPlus } from 'lucide-react'
import { DriverForm } from '../components/DriverForm'
import { Logo } from '../components/Layout'
import { CardSkeleton, ErrorBanner, ICON_STROKE } from '../components/ui'
import { initials } from '../lib/format'
import { useDriver } from '../state/DriverContext'

const STEPS = [
  { icon: MapPin, text: 'Trouvez une borne libre sur la carte' },
  { icon: Play, text: 'Chargez maintenant, sans réservation' },
  { icon: ReceiptText, text: 'Suivez la charge, arrêtez, recevez le reçu' },
]

/**
 * Premier ecran quand aucun compte n'est choisi (il n'y a pas d'authentification) :
 * les comptes de demonstration en cartes, et la creation d'un compte.
 */
export function WelcomePage() {
  const { drivers, loading, error, reload, selectDriver } = useDriver()
  const [creating, setCreating] = useState(false)
  const navigate = useNavigate()
  // un compte choisi ouvre toujours la carte (pas la page d'un autre compte)
  const choose = (id: number | null) => {
    selectDriver(id)
    navigate('/', { replace: true })
  }

  return (
    <div className="welcome">
      <div className="welcome__panel">
        <Logo />
        <h1>Bienvenue sur VoltPark</h1>
        <p className="muted welcome__lead">La recharge de votre véhicule électrique sur les bornes de Tunisie, en trois gestes.</p>
        <ol className="welcome__steps">
          {STEPS.map(({ icon: Icon, text }) => (
            <li key={text}>
              <Icon size={18} strokeWidth={ICON_STROKE} aria-hidden /> {text}
            </li>
          ))}
        </ol>

        <section aria-labelledby="demo-accounts">
          <h2 id="demo-accounts" className="welcome__title">
            Choisir un compte de démo
          </h2>
          {error && (
            <ErrorBanner
              error={error}
              title="Impossible de charger les comptes"
              actions={<button type="button" className="btn btn--ghost btn--small" onClick={reload}>Réessayer</button>}
            />
          )}
          {loading ? (
            <CardSkeleton lines={3} />
          ) : (
            <ul className="welcome__accounts">
              {drivers.map((d) => {
                const blocked = d.badgeId === 'TAG-BLOCKED'
                return (
                  <li key={d.id}>
                    <button type="button" className="account-card" onClick={() => choose(d.id ?? null)}>
                      <span className="avatar">{initials(d.name)}</span>
                      <span className="account-card__text">
                        <strong>{d.name}</strong>
                        <span className="muted">
                          {blocked ? (
                            <>
                              <ShieldAlert size={14} strokeWidth={ICON_STROKE} aria-hidden /> Badge {d.badgeId} · refusé par les bornes
                            </>
                          ) : (
                            <>
                              <BadgeCheck size={14} strokeWidth={ICON_STROKE} aria-hidden /> Badge {d.badgeId}
                            </>
                          )}
                        </span>
                      </span>
                      <ArrowRight size={18} strokeWidth={ICON_STROKE} aria-hidden className="account-card__go" />
                    </button>
                  </li>
                )
              })}
            </ul>
          )}
        </section>

        {creating ? (
          <section className="card card--padded" aria-label="Nouveau compte">
            <DriverForm
              onCreated={() => {
                setCreating(false)
                navigate('/', { replace: true })
              }}
              onCancel={() => setCreating(false)}
            />
          </section>
        ) : (
          <button type="button" className="link-button welcome__create" onClick={() => setCreating(true)}>
            <UserPlus size={16} strokeWidth={ICON_STROKE} aria-hidden /> Créer un compte
          </button>
        )}
        <p className="muted welcome__note">Démonstration : pas de mot de passe. Les bornes sont réelles, leur disponibilité est simulée.</p>
      </div>
    </div>
  )
}
