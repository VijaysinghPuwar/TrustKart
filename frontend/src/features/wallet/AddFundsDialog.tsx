import { Check } from 'lucide-react'
import { useRef, useState, type SyntheticEvent } from 'react'
import { Button } from '@/components/ui/Button'
import { Dialog } from '@/components/ui/Dialog'
import { Field } from '@/components/ui/Field'
import { newIdempotencyKey, useAddFunds } from '@/data/shopping'
import { ApiError } from '@/lib/api'
import { cn } from '@/lib/cn'
import { formatMoney, formatMoneyWhole } from '@/lib/money'

const PRESETS = ['1000', '5000', '10000', '25000', '50000', '100000', '1000000']
const AMOUNT = /^\d{1,8}(\.\d{1,2})?$/

/** Adds virtual funds. No card, bank or payment provider: the server just credits the virtual ledger. */
export function AddFundsDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [choice, setChoice] = useState<string>('100000')
  const [custom, setCustom] = useState('')
  const [error, setError] = useState<string>()
  const [added, setAdded] = useState<{ amount: string; balance: string } | null>(null)
  const addFunds = useAddFunds()
  // One key per submission attempt: a double click can't credit twice.
  const key = useRef(newIdempotencyKey())

  const amount = choice === 'custom' ? custom.replace(/[,$\s]/g, '') : choice

  function close() {
    setAdded(null)
    setError(undefined)
    onClose()
  }

  function submit(e: SyntheticEvent) {
    e.preventDefault()
    if (!AMOUNT.test(amount) || Number(amount) < 1 || Number(amount) > 10_000_000) {
      setError('Enter an amount from $1 to $10,000,000.')
      return
    }
    setError(undefined)
    addFunds.mutate(
      { amount, key: key.current },
      {
        onSuccess: (wallet) => {
          key.current = newIdempotencyKey()
          setAdded({ amount, balance: wallet.balance })
        },
        onError: (err) => {
          key.current = newIdempotencyKey()
          setError(
            err instanceof ApiError ? (err.fieldErrors[0]?.message ?? err.message) : 'Something went wrong.',
          )
        },
      },
    )
  }

  return (
    <Dialog
      open={open}
      onClose={close}
      title={added ? `${formatMoneyWhole(added.amount)} added` : 'Add funds'}
    >
      {added ? (
        <div className="flex flex-col items-center gap-3 py-2 text-center" role="status">
          <span className="tk-pop flex size-14 items-center justify-center rounded-full bg-trust text-white">
            <Check className="size-8" strokeWidth={3} aria-hidden="true" />
          </span>
          <p className="text-ink-muted">Your wallet balance is now</p>
          <p className="text-3xl font-bold tabular">{formatMoney(added.balance)}</p>
          <Button className="mt-2" onClick={close}>
            Done
          </Button>
        </div>
      ) : (
        <form onSubmit={submit} className="flex flex-col gap-4">
          <fieldset>
            <legend className="mb-2 text-sm font-medium">Choose an amount</legend>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
              {[...PRESETS, 'custom'].map((p) => (
                <label
                  key={p}
                  className={cn(
                    'flex h-11 cursor-pointer items-center justify-center rounded-control border text-sm font-semibold has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-primary',
                    choice === p
                      ? 'border-primary bg-primary-subtle text-primary-hover'
                      : 'border-border-strong hover:bg-surface-2',
                  )}
                >
                  <input
                    type="radio"
                    name="amount"
                    value={p}
                    checked={choice === p}
                    onChange={() => setChoice(p)}
                    className="sr-only"
                  />
                  {p === 'custom' ? 'Custom' : `+${formatMoneyWhole(p)}`}
                </label>
              ))}
            </div>
          </fieldset>
          {choice === 'custom' && (
            <Field
              label="Custom amount (USD)"
              inputMode="decimal"
              value={custom}
              onChange={(e) => setCustom(e.target.value)}
              placeholder="500000"
              hint={AMOUNT.test(amount) ? `+${formatMoney(amount)} to your wallet` : 'From $1 to $10,000,000'}
            />
          )}
          {error && (
            <p role="alert" className="text-sm text-danger">
              {error}
            </p>
          )}
          <Button type="submit" size="lg" loading={addFunds.isPending}>
            Add to Wallet
          </Button>
          <p className="text-center text-xs text-ink-muted">
            Funds are added instantly. See our <a href="/about">store policy</a>.
          </p>
        </form>
      )}
    </Dialog>
  )
}
