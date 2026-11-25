import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { useDriver } from '../state/DriverContext'
import { ErrorBanner } from './ui'

/** Creation d'un conducteur ; les erreurs de validation de l'API s'affichent sous chaque champ. */
export function DriverForm({ onCreated, onCancel }: { onCreated?: () => void; onCancel?: () => void }) {
  const { createDriver } = useDriver()
  const [values, setValues] = useState({ name: '', email: '', badgeId: '', phoneNumber: '' })
  const [error, setError] = useState<ApiError | null>(null)
  const [saving, setSaving] = useState(false)

  const update = (field: keyof typeof values) => (event: { target: { value: string } }) =>
    setValues((v) => ({ ...v, [field]: event.target.value }))

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      await createDriver({ ...values, phoneNumber: values.phoneNumber || undefined })
      onCreated?.()
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError(0, String(e)))
    } finally {
      setSaving(false)
    }
  }

  const fieldError = (field: string) =>
    error?.fieldErrors[field] ? <p className="field__error">{error.fieldErrors[field]}</p> : null

  return (
    <form className="form" onSubmit={submit} noValidate>
      {error && Object.keys(error.fieldErrors).length === 0 && <ErrorBanner error={error} />}
      <label className="field">
        <span className="field__label">Nom</span>
        <input className="input" value={values.name} onChange={update('name')} autoComplete="name" />
        {fieldError('name')}
      </label>
      <label className="field">
        <span className="field__label">Email</span>
        <input className="input" type="email" value={values.email} onChange={update('email')} autoComplete="email" />
        {fieldError('email')}
      </label>
      <label className="field">
        <span className="field__label">Badge RFID</span>
        <input className="input" value={values.badgeId} onChange={update('badgeId')} placeholder="TAG-..." />
        {fieldError('badgeId')}
      </label>
      <label className="field">
        <span className="field__label">Téléphone (optionnel)</span>
        <input className="input" value={values.phoneNumber} onChange={update('phoneNumber')} placeholder="+216 20 123 456" />
        {fieldError('phoneNumber')}
      </label>
      <div className="form__actions">
        {onCancel && (
          <button type="button" className="btn btn--ghost" onClick={onCancel}>
            Annuler
          </button>
        )}
        <button type="submit" className="btn btn--primary" disabled={saving}>
          {saving ? 'Création…' : 'Créer le conducteur'}
        </button>
      </div>
    </form>
  )
}
