import { useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { CalendarDays, FileText, Map as MapIcon, Menu, Search, Settings, User, UserRound, Users, X, Zap } from 'lucide-react'
import { useDriver } from '../state/DriverContext'
import { useSearch } from '../state/SearchContext'
import { formatLongDay } from '../lib/format'
import { DriverMenu } from './DriverMenu'
import { NotificationBell } from './NotificationBell'
import { ICON_STROKE } from './ui'

const NAV = [
  { to: '/', label: 'Carte', icon: MapIcon, end: true },
  { to: '/sessions', label: 'Mes sessions', icon: Zap },
  { to: '/reservations', label: 'Réservations', icon: CalendarDays },
  { to: '/factures', label: 'Factures', icon: FileText },
]

const TABS = [
  { to: '/', label: 'Carte', icon: MapIcon, end: true },
  { to: '/sessions', label: 'Sessions', icon: Zap },
  { to: '/factures', label: 'Factures', icon: FileText },
  { to: '/parametres', label: 'Profil', icon: User },
]

export function Logo({ dark = false }: { dark?: boolean }) {
  return (
    <span className={`logo ${dark ? 'logo--dark' : ''}`}>
      <Zap size={24} strokeWidth={2} className="logo__bolt" fill="currentColor" aria-hidden />
      <span>VoltPark</span>
    </span>
  )
}

function Sidebar() {
  const { selectDriver } = useDriver()
  const navigate = useNavigate()
  return (
    <aside className="sidebar">
      <Logo dark />
      <nav className="sidebar__nav" aria-label="Navigation principale">
        {NAV.map(({ to, label, icon: Icon, end }) => (
          <NavLink key={to} to={to} end={end} className="nav-item">
            <Icon size={18} strokeWidth={ICON_STROKE} aria-hidden />
            <span>{label}</span>
          </NavLink>
        ))}
      </nav>
      <div className="sidebar__bottom">
        <NavLink to="/parametres" className="nav-item">
          <Settings size={18} strokeWidth={ICON_STROKE} aria-hidden />
          <span>Paramètres</span>
        </NavLink>
        <button
          type="button"
          className="nav-item"
          onClick={() => {
            selectDriver(null)
            navigate('/parametres')
          }}
        >
          <Users size={18} strokeWidth={ICON_STROKE} aria-hidden />
          <span>Changer de conducteur</span>
        </button>
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

const TITLES: Array<[RegExp, string]> = [
  [/^\/$/, 'Carte'],
  [/^\/sessions/, 'Mes sessions'],
  [/^\/reservations/, 'Réservations'],
  [/^\/factures\/.+/, 'Facture'],
  [/^\/factures/, 'Factures'],
  [/^\/parametres/, 'Paramètres'],
]

/** Titre de la page et salutation (prenom du conducteur et date du jour). */
function PageTitle() {
  const { pathname } = useLocation()
  const { driver } = useDriver()
  const title = TITLES.find(([pattern]) => pattern.test(pathname))?.[1] ?? 'VoltPark'
  const firstName = driver?.name?.split(' ')[0]
  const [today] = useState(() => formatLongDay(new Date()))
  return (
    <div className="topbar__title">
      <h1>{title}</h1>
      <p className="muted">
        {firstName ? `Bonjour, ${firstName}` : 'Bienvenue sur VoltPark'} · <span className="topbar__date">{today}</span>
      </p>
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
              <UserRound size={18} strokeWidth={ICON_STROKE} aria-hidden />
              Conducteur et paramètres
            </NavLink>
          </nav>
        </div>
      )}
    </header>
  )
}

function TabBar() {
  return (
    <nav className="tabbar" aria-label="Onglets">
      {TABS.map(({ to, label, icon: Icon, end }) => (
        <NavLink key={to} to={to} end={end} className="tabbar__item">
          <Icon size={22} strokeWidth={ICON_STROKE} aria-hidden />
          <span>{label}</span>
        </NavLink>
      ))}
    </nav>
  )
}

export function Layout() {
  return (
    <div className="app">
      <Sidebar />
      <div className="app__main">
        <TopBar />
        <MobileHeader />
        <main className="content">
          <Outlet />
        </main>
      </div>
      <TabBar />
    </div>
  )
}
