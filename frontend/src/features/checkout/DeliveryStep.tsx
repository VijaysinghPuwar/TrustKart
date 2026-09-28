import { Archive, Plus, Server, Sparkles } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { AddressForm } from '@/components/address/AddressForm'
import { AddressLines } from '@/components/address/AddressLines'
import { Field } from '@/components/ui/Field'
import { useAddressMutations, useAddresses } from '@/data/shopping'
import { ApiError } from '@/lib/api'
import { cn } from '@/lib/cn'
import type { Destination } from './destination'

const PRESETS: {
  preset: 'DREAM_SETUP' | 'HOMELAB' | 'COLLECTION'
  label: string
  hint: string
  icon: ReactNode
}[] = [
  {
    preset: 'DREAM_SETUP',
    label: 'Dream setup',
    hint: 'Straight to your desk of dreams',
    icon: <Sparkles className="size-5" />,
  },
  {
    preset: 'HOMELAB',
    label: 'Homelab',
    hint: 'The rack in the basement',
    icon: <Server className="size-5" />,
  },
  {
    preset: 'COLLECTION',
    label: 'My collection',
    hint: 'No address, just collect it',
    icon: <Archive className="size-5" />,
  },
]

const card = (selected: boolean) =>
  cn(
    'flex cursor-pointer items-start gap-3 rounded-card border p-3.5 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-primary',
    selected ? 'border-primary bg-primary-subtle' : 'border-border hover:border-border-strong',
  )

interface DeliveryStepProps {
  value: Destination | null
  onChange: (d: Destination) => void
}

export function DeliveryStep({ value, onChange }: DeliveryStepProps) {
  const addresses = useAddresses()
  const { create } = useAddressMutations()
  const saved = addresses.data ?? []
  const [adding, setAdding] = useState(false)
  const [saveToBook, setSaveToBook] = useState(true)
  const [customLabel, setCustomLabel] = useState('')
  const showForm =
    adding ||
    (addresses.isSuccess && saved.length === 0 && value?.kind !== 'preset' && value?.kind !== 'inline')
  const serverErrors = create.error instanceof ApiError ? create.error.fieldErrors : []

  return (
    <div className="flex flex-col gap-5">
      <p className="rounded-control bg-surface-2 p-3 text-sm">
        <strong>No physical product will be shipped.</strong> Choose where this order lives in the simulation.
        Addresses can be fictional.
      </p>

      <fieldset className="flex flex-col gap-2.5">
        <legend className="mb-2 font-semibold">Ship to an address</legend>
        {saved.map((a) => (
          <label key={a.id} className={card(value?.kind === 'saved' && value.address.id === a.id)}>
            <input
              type="radio"
              name="destination"
              className="mt-1 accent-primary"
              checked={value?.kind === 'saved' && value.address.id === a.id}
              onChange={() => onChange({ kind: 'saved', address: a })}
            />
            <span className="flex-1">
              <span className="flex items-center gap-2 font-semibold">
                {a.label}
                {a.isDefault && (
                  <span className="rounded-badge bg-surface-2 px-1.5 text-[11px] font-semibold text-ink-muted">
                    Default
                  </span>
                )}
              </span>
              <AddressLines address={a} className="text-[13px] not-italic leading-5 text-ink-muted" />
            </span>
          </label>
        ))}
        {value?.kind === 'inline' && !showForm && (
          <div className={card(true)}>
            <span className="flex-1">
              <span className="block font-semibold">{value.address.label} (this order only)</span>
              <AddressLines
                address={value.address}
                className="text-[13px] not-italic leading-5 text-ink-muted"
              />
            </span>
            <button
              type="button"
              className="text-sm font-semibold text-primary"
              onClick={() => setAdding(true)}
            >
              Edit
            </button>
          </div>
        )}
        {showForm ? (
          <div className="rounded-card border border-border p-4">
            <p className="mb-3 font-semibold">{saved.length ? 'New address' : 'Add a delivery address'}</p>
            <AddressForm
              initial={value?.kind === 'inline' ? value.address : undefined}
              submitLabel="Use this address"
              pending={create.isPending}
              serverErrors={serverErrors}
              onCancel={saved.length || value ? () => setAdding(false) : undefined}
              extra={
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    className="size-[18px] accent-primary"
                    checked={saveToBook}
                    onChange={(e) => setSaveToBook(e.target.checked)}
                  />
                  Save to my address book
                </label>
              }
              onSubmit={(input) => {
                if (saveToBook) {
                  create.mutate(
                    { input },
                    {
                      onSuccess: (address) => {
                        setAdding(false)
                        onChange({ kind: 'saved', address })
                      },
                    },
                  )
                } else {
                  setAdding(false)
                  onChange({ kind: 'inline', address: input })
                }
              }}
            />
          </div>
        ) : (
          <button
            type="button"
            onClick={() => setAdding(true)}
            className="flex h-12 items-center gap-2 rounded-card border border-dashed border-border-strong px-4 text-sm font-semibold text-primary hover:bg-surface-2"
          >
            <Plus className="size-4" aria-hidden="true" />
            Add a new address
          </button>
        )}
      </fieldset>

      <fieldset className="flex flex-col gap-2.5">
        <legend className="mb-2 font-semibold">Or keep it virtual</legend>
        <div className="grid gap-2.5 sm:grid-cols-3">
          {PRESETS.map((p) => (
            <label key={p.preset} className={card(value?.kind === 'preset' && value.preset === p.preset)}>
              <input
                type="radio"
                name="destination"
                className="sr-only"
                checked={value?.kind === 'preset' && value.preset === p.preset}
                onChange={() => onChange({ kind: 'preset', preset: p.preset })}
              />
              <span className="mt-0.5 text-primary" aria-hidden="true">
                {p.icon}
              </span>
              <span className="flex flex-col">
                <span className="font-semibold">{p.label}</span>
                <span className="text-[13px] text-ink-muted">{p.hint}</span>
              </span>
            </label>
          ))}
        </div>
        <div className="flex flex-wrap items-end gap-2">
          <Field
            label="Or name a fictional place"
            className="min-w-0 flex-1"
            maxLength={60}
            value={customLabel}
            placeholder="Moon base"
            onChange={(e) => {
              setCustomLabel(e.target.value)
              if (e.target.value.trim())
                onChange({ kind: 'preset', preset: 'CUSTOM', label: e.target.value.trim() })
            }}
          />
        </div>
      </fieldset>
    </div>
  )
}
