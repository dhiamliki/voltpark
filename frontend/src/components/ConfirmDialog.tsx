import { useEffect, useRef, type ReactNode } from 'react'

/** Petite fenetre de confirmation (action importante) : Echap ou "Annuler" ferme, focus sur l'action. */
export function ConfirmDialog({ title, children, confirmLabel, cancelLabel = 'Annuler', busy, onConfirm, onCancel }: {
  title: string
  children?: ReactNode
  confirmLabel: string
  cancelLabel?: string
  busy?: boolean
  onConfirm: () => void
  onCancel: () => void
}) {
  const confirm = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    confirm.current?.focus()
    const onKey = (event: KeyboardEvent) => event.key === 'Escape' && onCancel()
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onCancel])

  return (
    <div className="sheet-layer sheet-layer--center">
      <button type="button" className="sheet-backdrop" aria-label={cancelLabel} tabIndex={-1} onClick={onCancel} />
      <section className="dialog" role="alertdialog" aria-modal="true" aria-labelledby="confirm-title">
        <h2 id="confirm-title">{title}</h2>
        {children && <div className="muted dialog__text">{children}</div>}
        <div className="dialog__actions">
          <button type="button" className="btn btn--ghost" onClick={onCancel} disabled={busy}>
            {cancelLabel}
          </button>
          <button ref={confirm} type="button" className="btn btn--primary" onClick={onConfirm} disabled={busy}>
            {confirmLabel}
          </button>
        </div>
      </section>
    </div>
  )
}
