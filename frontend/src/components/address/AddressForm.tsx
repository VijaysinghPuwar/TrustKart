import { useId, useState, type SyntheticEvent } from 'react'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import type { AddressInput } from '@/lib/types'
import { COUNTRIES } from './countries'

export const EMPTY_ADDRESS: AddressInput = {
  label: 'Home',
  fullName: '',
  line1: '',
  line2: '',
  city: '',
  region: '',
  postalCode: '',
  country: 'US',
}

type Errors = Partial<Record<keyof AddressInput, string>>

export function validateAddress(a: AddressInput): Errors {
  const errors: Errors = {}
  if (!a.label.trim()) errors.label = 'Give this address a short name.'
  if (!a.fullName.trim()) errors.fullName = 'Enter a name.'
  if (!a.line1.trim()) errors.line1 = 'Enter a street address.'
  if (!a.city.trim()) errors.city = 'Enter a city.'
  if (!/^[A-Za-z0-9 -]{2,16}$/.test(a.postalCode.trim()))
    errors.postalCode = 'Enter a postal code (letters, digits, spaces or hyphens).'
  return errors
}

interface AddressFormProps {
  initial?: AddressInput
  submitLabel: string
  onSubmit: (address: AddressInput) => void
  onCancel?: () => void
  pending?: boolean
  serverErrors?: { field: string; message: string }[]
  /** Extra controls rendered above the submit button (e.g. "save to address book"). */
  extra?: React.ReactNode
}

/**
 * Delivery address for the simulation. Every field can be fictional: TrustKart never ships anything, and the
 * form says so. Validation here is for usability; the server validates again.
 */
export function AddressForm({
  initial = EMPTY_ADDRESS,
  submitLabel,
  onSubmit,
  onCancel,
  pending,
  serverErrors = [],
  extra,
}: AddressFormProps) {
  const [a, setA] = useState<AddressInput>(initial)
  const [errors, setErrors] = useState<Errors>({})
  const countryId = useId()
  const set = (k: keyof AddressInput) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setA({ ...a, [k]: e.target.value })
  const err = (k: keyof AddressInput) => errors[k] ?? serverErrors.find((f) => f.field === k)?.message

  function submit(e: SyntheticEvent) {
    e.preventDefault()
    const found = validateAddress(a)
    setErrors(found)
    if (Object.keys(found).length === 0) onSubmit(a)
  }

  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4">
      <div className="grid gap-3 sm:grid-cols-2">
        <Field
          label="Address name"
          maxLength={40}
          value={a.label}
          onChange={set('label')}
          error={err('label')}
          hint="Home, Office, Dream studio…"
        />
        <Field
          label="Full name"
          autoComplete="off"
          maxLength={100}
          value={a.fullName}
          onChange={set('fullName')}
          error={err('fullName')}
        />
        <Field
          label="Street address"
          autoComplete="off"
          maxLength={120}
          value={a.line1}
          onChange={set('line1')}
          error={err('line1')}
          className="sm:col-span-2"
        />
        <Field
          label="Apartment, suite, etc. (optional)"
          autoComplete="off"
          maxLength={120}
          value={a.line2 ?? ''}
          onChange={set('line2')}
          className="sm:col-span-2"
        />
        <Field
          label="City"
          autoComplete="off"
          maxLength={80}
          value={a.city}
          onChange={set('city')}
          error={err('city')}
        />
        <Field
          label="State / region (optional)"
          autoComplete="off"
          maxLength={80}
          value={a.region ?? ''}
          onChange={set('region')}
        />
        <Field
          label="Postal code"
          autoComplete="off"
          maxLength={16}
          value={a.postalCode}
          onChange={set('postalCode')}
          error={err('postalCode')}
        />
        <div className="flex flex-col gap-1.5">
          <label htmlFor={countryId} className="text-sm font-medium">
            Country
          </label>
          <select
            id={countryId}
            value={a.country}
            onChange={set('country')}
            className="h-11 rounded-control border border-border-strong bg-surface px-3 text-[15px] text-ink"
          >
            {COUNTRIES.map(([code, name]) => (
              <option key={code} value={code}>
                {name}
              </option>
            ))}
          </select>
        </div>
      </div>
      {extra}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" loading={pending}>
          {submitLabel}
        </Button>
        {onCancel && (
          <Button variant="secondary" onClick={onCancel}>
            Cancel
          </Button>
        )}
      </div>
    </form>
  )
}
