import { useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { CalendarDays, History, Map as MapIcon, Menu, Search, Settings, Users, X, Zap } from 'lucide-react'
import { pageMeta } from '../lib/pages'
import { useCharge } from '../state/ChargeContext'
import { useDriver } from '../state/DriverContext'
import { useSearch } from '../state/SearchContext'
import { WelcomePage } from '../pages/WelcomePage'
import { ChargeBar } from './ChargeBar'
import { DriverMenu } from './DriverMenu'
import { NotificationBell } from './NotificationBell'
import { ICON_STROKE } from './ui'

/** Le parcours principal : trouver une borne, charger, puis retrouver ses reservations et factures. */
const NAV = [
  { to: '/', label: 'Carte', short: 'Carte', icon: MapIcon, end: true },
  { to: '/charge', label: 'Ma charge', short: 'Ma charge', icon: Zap },
  { to: '/reservations', label: 'Réservations', short: 'Réserver', icon: CalendarDays },
  { to: '/historique', label: 'Historique et factures', short: 'Historique', icon: History },
]

export function Logo({ dark = false }: { dark?: boolean }) {
  return (
    <span className={`logo ${dark ? 'logo--dark' : ''}`}>
      <Zap size={24} strokeWidth={2} className="logo__bolt" fill="currentColor" aria-hidden />
      <span>VoltPark</span>
    </span>
  )
}

/** Point vert sur "Ma charge" pendant une charge. */
function ChargingDot() {
  const { current } = useCharge()
  if (!current) return null
  return (
    <span
      className={`nav-dot ${current.status === 'PENDING_START' ? 'nav-dot--pending' : ''}`}
      aria-label={current.status === 'ACTIVE' ? 'charge en cours' : 'démarrage en attente'}
    />
  )
}

function Sidebar() {
  return (
    <aside className="sidebar">
      <Logo dark />
      <nav className="sidebar__nav" aria-label="Navigation principale">
        {NAV.map(({ to, label, icon: Icon, end }) => (
          <NavLink key={to} to={to} end={end} className="nav-item">
            <Icon size={18} strokeWidth={ICON_STROKE} aria-hidden />
            <span>{label}</span>
            {to === '/charge' && <ChargingDot />}
          </NavLink>
        ))}
      </nav>
      <div className="sidebar__bottom">
        <NavLink to="/parametres" className="nav-item">
          <Settings size={18} strokeWidth={ICON_STROKE} aria-hidden />
          <span>Paramètres</span>
        </NavLink>
      </div>
    </aside>
  )
}

function SearchField() {
  const { query, setQuery } = useSearch()
  const navigate = useNavigate()
  const location = useLocation()
  return (
    <label className="search">
      <Search size={18} strokeWidth={ICON_STROKE} aria-hidden />
      <span className="visually-hidden">Rechercher une station</span>
      <input
        type="search"
        placeholder="Rechercher une station…"
        value={query}
        onChange={(event) => {
          setQuery(event.target.value)
          if (location.pathname !== '/') navigate('/')
        }}
      />
    </label>
  )
}

/** Titre de la page et, en une ligne, a quoi elle sert. */
function PageTitle() {
  const { pathname } = useLocation()
  const meta = pageMeta(pathname)
  return (
    <div className="topbar__title">
      <h1>{meta.title}</h1>
      <p className="muted">{meta.subtitle}</p>
    </div>
  )
}

function TopBar() {
  return (
    <header className="topbar">
      <PageTitle />
      <SearchField />
      <div className="topbar__actions">
        <NotificationBell />
        <DriverMenu />
      </div>
    </header>
  )
}

/** Mobile : logo + menu en haut, onglets en bas. */
function MobileHeader() {
  const [open, setOpen] = useState(false)
  const { selectDriver } = useDriver()
  const close = () => setOpen(false)
  return (
    <header className="mobile-header">
      <Logo />
      <button type="button" className="icon-button" aria-label="Menu" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        {open ? <X size={22} strokeWidth={ICON_STROKE} /> : <Menu size={22} strokeWidth={ICON_STROKE} />}
      </button>
      {open && (
        <div className="mobile-menu">
          <SearchField />
          <nav aria-label="Menu">
            {NAV.map(({ to, label, icon: Icon, end }) => (
              <NavLink key={to} to={to} end={end} className="mobile-menu__item" onClick={close}>
                <Icon size={18} strokeWidth={ICON_STROKE} aria-hidden />
                {label}
              </NavLink>
            ))}
            <NavLink to="/parametres" className="mobile-menu__item" onClick={close}>
              <Settings size={18} strokeWidth={ICON_STROKE} aria-hidden />
              Paramètres
            </NavLink>
            <button
              type="button"
              className="mobile-menu__item"
              onClick={() => {
                close()
                selectDriver(null)
              }}
            >
              <Users size={18} strokeWidth={ICON_STROKE} aria-hidden />
              Changer de compte
            </button>
          </nav>
        </div>
      )}
    </header>
  )
}

function TabBar() {
  return (
    <nav className="tabbar" aria-label="Onglets">
      {NAV.map(({ to, short, icon: Icon, end }) => (
        <NavLink key={to} to={to} end={end} className="tabbar__item">
          <span className="tabbar__icon">
            <Icon size={22} strokeWidth={ICON_STROKE} aria-hidden />
            {to === '/charge' && <ChargingDot />}
          </span>
          <span>{short}</span>
        </NavLink>
      ))}
    </nav>
  )
}

export function Layout() {
  const { driver, selectedId, loading } = useDriver()
  const { current } = useCharge()
  // pas de compte choisi (ou compte disparu) : ecran d'accueil
  if (selectedId == null || (!loading && !driver)) return <WelcomePage />

  return (
    <div className={`app ${current ? 'app--charging' : ''}`}>
      <Sidebar />
      <div className="app__main">
        <TopBar />
        <MobileHeader />
        <ChargeBar />
        <main className="content">
          <Outlet />
        </main>
      </div>
      <TabBar />
    </div>
  )
}
