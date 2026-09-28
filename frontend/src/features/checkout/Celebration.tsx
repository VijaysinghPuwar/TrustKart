import { Check } from 'lucide-react'
import { useMemo } from 'react'
import { ButtonLink } from '@/components/ui/Button'
import { Dialog } from '@/components/ui/Dialog'
import { formatMoney } from '@/lib/money'
import type { Purchase } from '@/lib/types'

const BIG_PURCHASE = 50_000

/** The satisfying moment after Place Virtual Order, honest about being a simulation. */
export function Celebration({
  purchase,
  open,
  onClose,
}: {
  purchase: Purchase
  open: boolean
  onClose: () => void
}) {
  const confetti =
    Number(purchase.total) >= BIG_PURCHASE && !window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const pieces = useMemo(
    () =>
      Array.from({ length: 36 }, (_, i) => ({
        left: `${String((i * 37) % 100)}%`,
        delay: `${String((i % 9) * 60)}ms`,
        color: ['var(--tk-primary)', 'var(--tk-trust)', 'var(--tk-accent)'][i % 3],
        rotate: `${String((i * 47) % 360)}deg`,
      })),
    [],
  )
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Virtual purchase complete!"
      hideClose
      className="overflow-hidden text-center"
    >
      {confetti && (
        <div aria-hidden="true" className="pointer-events-none absolute inset-x-0 top-0 h-40 overflow-hidden">
          {pieces.map((p, i) => (
            <span
              key={i}
              className="tk-confetti absolute top-0 h-2.5 w-1.5 rounded-sm"
              style={{ left: p.left, animationDelay: p.delay, background: p.color, rotate: p.rotate }}
            />
          ))}
        </div>
      )}
      <div className="flex flex-col items-center gap-4">
        <span className="tk-pop flex size-16 items-center justify-center rounded-full bg-trust text-white">
          <Check className="size-9" strokeWidth={3} aria-hidden="true" />
        </span>
        <p className="text-3xl font-bold tabular">{formatMoney(purchase.total)}</p>
        <p className="text-ink-muted">
          worth of technology was added to your TrustKart collection. It’s yours, virtually.
        </p>
        <dl className="grid w-full grid-cols-3 gap-2 rounded-card bg-surface-2 p-3 text-sm tabular">
          <div>
            <dt className="text-ink-muted">You spent</dt>
            <dd className="font-semibold">
              {purchase.walletMode === 'UNLIMITED' ? 'Unlimited' : formatMoney(purchase.total)}
            </dd>
          </div>
          <div>
            <dt className="text-ink-muted">Remaining</dt>
            <dd className="font-semibold">
              {purchase.balanceAfter ? formatMoney(purchase.balanceAfter) : '∞'}
            </dd>
          </div>
          <div>
            <dt className="text-ink-muted">Items</dt>
            <dd className="font-semibold">{purchase.itemCount}</dd>
          </div>
        </dl>
        <div className="flex w-full flex-wrap justify-center gap-2">
          <ButtonLink to="/collection" size="lg" onClick={onClose}>
            View my collection
          </ButtonLink>
          <ButtonLink to="/" variant="secondary" size="lg" onClick={onClose}>
            Continue shopping
          </ButtonLink>
        </div>
        <p className="text-xs text-ink-muted">
          This was a simulated purchase. No real payment was processed and no products will be shipped.
        </p>
      </div>
    </Dialog>
  )
}
