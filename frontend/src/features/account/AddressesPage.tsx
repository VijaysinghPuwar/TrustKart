import { Plus } from 'lucide-react'
import { useState } from 'react'
import { AddressForm } from '@/components/address/AddressForm'
import { AddressLines } from '@/components/address/AddressLines'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ErrorState } from '@/components/ui/ErrorState'
import { PageSpinner } from '@/components/ui/PageSpinner'
import { useToast } from '@/components/ui/Toast'
import { useAddressMutations, useAddresses } from '@/data/shopping'
import { ApiError } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'

export default function AddressesPage() {
  usePageTitle('Addresses')
  const addresses = useAddresses()
  const { create, update, remove, makeDefault } = useAddressMutations()
  const { notify } = useToast()
  const [editing, setEditing] = useState<string | null>(null) // an address id, or 'new'

  if (addresses.isPending) return <PageSpinner />
  if (addresses.isError) return <ErrorState error={addresses.error} />
  const list = addresses.data
  const errorsOf = (e: unknown) => (e instanceof ApiError ? e.fieldErrors : [])

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-end gap-3">
        <div className="flex-1">
          <h1 className="text-[28px] font-bold leading-9">Addresses</h1>
          <p className="text-sm text-ink-muted">
            Where your virtual orders “ship”. Nothing is ever delivered, so any address is fine.
          </p>
        </div>
        {editing === null && list.length < 10 && (
          <Button variant="secondary" onClick={() => setEditing('new')}>
            <Plus className="size-4" aria-hidden="true" />
            Add address
          </Button>
        )}
      </div>

      {editing === 'new' && (
        <section className="rounded-card border border-border bg-surface p-5" aria-label="New address">
          <AddressForm
            submitLabel="Save address"
            pending={create.isPending}
            serverErrors={errorsOf(create.error)}
            onCancel={() => setEditing(null)}
            onSubmit={(input) =>
              create.mutate(
                { input },
                {
                  onSuccess: () => {
                    setEditing(null)
                    notify('Address saved')
                  },
                },
              )
            }
          />
        </section>
      )}

      {list.length === 0 && editing !== 'new' && (
        <p className="rounded-card border border-border bg-surface p-6 text-sm text-ink-muted">
          No saved addresses yet. Add one here or during checkout.
        </p>
      )}

      <ul className="grid gap-3 md:grid-cols-2">
        {list.map((a) =>
          editing === a.id ? (
            <li key={a.id} className="rounded-card border border-primary bg-surface p-5 md:col-span-2">
              <AddressForm
                initial={{ ...a, line2: a.line2 ?? '', region: a.region ?? '' }}
                submitLabel="Save changes"
                pending={update.isPending}
                serverErrors={errorsOf(update.error)}
                onCancel={() => setEditing(null)}
                onSubmit={(input) =>
                  update.mutate(
                    { id: a.id, input },
                    {
                      onSuccess: () => {
                        setEditing(null)
                        notify('Address updated')
                      },
                    },
                  )
                }
              />
            </li>
          ) : (
            <li key={a.id} className="flex flex-col gap-3 rounded-card border border-border bg-surface p-5">
              <p className="flex items-center gap-2 font-semibold">
                {a.label}
                {a.isDefault && <Badge tone="info">Default</Badge>}
              </p>
              <AddressLines address={a} />
              <div className="mt-auto flex flex-wrap gap-1">
                <Button variant="ghost" size="sm" onClick={() => setEditing(a.id)}>
                  Edit<span className="sr-only"> {a.label}</span>
                </Button>
                {!a.isDefault && (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() =>
                      makeDefault.mutate(a.id, { onSuccess: () => notify(`${a.label} is now your default`) })
                    }
                  >
                    Make default
                  </Button>
                )}
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => remove.mutate(a.id, { onSuccess: () => notify('Address removed') })}
                >
                  Remove<span className="sr-only"> {a.label}</span>
                </Button>
              </div>
            </li>
          ),
        )}
      </ul>
    </div>
  )
}
