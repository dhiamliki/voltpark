import type { ReactNode } from 'react'
import { AlertCircle, Info } from 'lucide-react'
import type { ApiError } from '../api/client'

export const ICON_STROKE = 1.75

type Tone = 'info' | 'danger'

/** Bandeau d'information ou d'erreur, au meme style calme partout. */
export function Banner({ tone = 'info', title, children, actions, requestId }: {
  tone?: Tone
  title?: string
  children?: ReactNode
  actions?: ReactNode
  requestId?: string
}) {
  const Icon = tone === 'danger' ? AlertCircle : Info
  return (
    <div className={`banner banner--${tone}`} role={tone === 'danger' ? 'alert' : 'status'}>
      <Icon size={18} strokeWidth={ICON_STROKE} className="banner__icon" aria-hidden />
      <div className="banner__body">
        {title && <p className="banner__title">{title}</p>}
        {children && <div className="banner__text">{children}</div>}
        {requestId && <p className="banner__ref">Réf. {requestId.slice(0, 8)}</p>}
        {actions && <div className="banner__actions">{actions}</div>}
      </div>
    </div>
  )
}

/** Erreur d'API : message lisible + reference X-Request-Id pour retrouver la requete dans les logs. */
export function ErrorBanner({ error, title, actions }: { error: ApiError | null; title?: string; actions?: ReactNode }) {
  if (!error) return null
  return (
    <Banner tone="danger" title={title ?? error.message} requestId={error.requestId} actions={actions}>
      {title ? error.message : null}
    </Banner>
  )
}

export function Skeleton({ width = '100%', height = 16, radius }: { width?: number | string; height?: number | string; radius?: number }) {
  return <span className="skeleton" style={{ width, height, borderRadius: radius }} aria-hidden />
}

export function CardSkeleton({ lines = 3 }: { lines?: number }) {
  return (
    <div className="card card--padded skeleton-card" aria-busy="true" aria-label="Chargement">
      <Skeleton width="45%" height={20} />
      {Array.from({ length: lines }, (_, i) => (
        <Skeleton key={i} width={`${90 - i * 15}%`} />
      ))}
    </div>
  )
}

/** Etat vide : une invitation et une action. */
export function EmptyState({ icon, title, text, action }: { icon: ReactNode; title: string; text?: string; action?: ReactNode }) {
  return (
    <div className="empty">
      <div className="empty__icon">{icon}</div>
      <p className="empty__title">{title}</p>
      {text && <p className="empty__text">{text}</p>}
      {action}
    </div>
  )
}

export type PillTone = 'accent' | 'neutral' | 'success' | 'danger'

export function Pill({ tone = 'neutral', children, dot }: { tone?: PillTone; children: ReactNode; dot?: boolean }) {
  return (
    <span className={`pill pill--${tone}`}>
      {dot && <span className="pill__dot" aria-hidden />}
      {children}
    </span>
  )
}

export type DotTone = 'available' | 'occupied' | 'out'

export function StatusDot({ tone }: { tone: DotTone }) {
  return <span className={`dot dot--${tone}`} aria-hidden />
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <header className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="muted page-header__subtitle">{subtitle}</p>}
      </div>
      {actions && <div className="page-header__actions">{actions}</div>}
    </header>
  )
}
