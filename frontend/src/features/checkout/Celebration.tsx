import { Check, Truck } from 'lucide-react'
import { useMemo } from 'react'
import { ButtonLink } from '@/components/ui/Button'
import { Dialog } from '@/components/ui/Dialog'
import { formatMoney } from '@/lib/money'
import type { Purchase } from '@/lib/types'
import { estimatedDelivery } from './delivery'

const BIG_ORDER = 50_000

/** Order confirmation moment: check animation, key facts, and a little confetti for big orders. */
export function Celebration({
  purchase,
  open,
  onClose,
  name,
}: {
  purchase: Purchase
  open: boolean
  onClose: () => void
  name?: string
}) {
  const confetti =
    Number(purchase.total) >= BIG_ORDER && !window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const pieces = useMemo(
    () =>
      Array.from({ length: 40 }, (_, i) => ({
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
      title="Order placed!"
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
        <p className="text-lg font-semibold">
          {name ? `Thank you, ${name.split(' ')[0] ?? name}!` : 'Thank you for your order!'}
        </p>
        <p className="text-3xl font-bold tabular">{formatMoney(purchase.total)}</p>
        <p className="font-mono text-sm text-ink-muted">Order {purchase.orderNumber}</p>
        <dl className="grid w-full grid-cols-3 gap-2 rounded-card bg-surface-2 p-3 text-sm tabular">
          <div>
            <dt className="text-ink-muted">Paid</dt>
            <dd className="font-semibold">{formatMoney(purchase.total)}</dd>
          </div>
          <div>
            <dt className="text-ink-muted">Wallet balance</dt>
            <dd className="font-semibold">
              {purchase.balanceAfter ? formatMoney(purchase.balanceAfter) : '∞'}
            </dd>
          </div>
          <div>
            <dt className="text-ink-muted">Items</dt>
            <dd className="font-semibold">{purchase.itemCount}</dd>
          </div>
        </dl>
        <p className="flex items-center gap-1.5 text-sm text-trust">
          <Truck className="size-4" aria-hidden="true" />
          Estimated delivery {estimatedDelivery(new Date(purchase.createdAt))}
        </p>
        <div className="flex w-full flex-wrap justify-center gap-2">
          <ButtonLink to="/collection" size="lg" onClick={onClose}>
            View my collection
          </ButtonLink>
          <ButtonLink to="/" variant="secondary" size="lg" onClick={onClose}>
            Continue shopping
          </ButtonLink>
        </div>
      </div>
    </Dialog>
  )
}
