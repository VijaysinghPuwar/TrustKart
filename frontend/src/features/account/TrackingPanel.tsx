import { Check, Package, Truck } from 'lucide-react'
import { cn } from '@/lib/cn'
import type { Tracking } from '@/lib/types'

const dayFmt: Intl.DateTimeFormatOptions = { weekday: 'long', month: 'long', day: 'numeric' }
const stampFmt: Intl.DateTimeFormatOptions = {
  month: 'short',
  day: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
}

export function formatDay(iso: string) {
  return new Date(iso).toLocaleDateString('en-US', dayFmt)
}

/** One-line delivery status, shared by the order list and the tracking panel. */
export function deliveryHeadline(t: Pick<Tracking, 'stage' | 'estimatedDelivery' | 'deliveredAt'>) {
  switch (t.stage) {
    case 'DELIVERED':
      return `Delivered ${formatDay(t.deliveredAt ?? t.estimatedDelivery)}`
    case 'CANCELLED':
      return 'Cancelled'
    case 'REFUNDED':
      return 'Returned and refunded'
    case 'OUT_FOR_DELIVERY':
      return 'Arriving today'
    default:
      return `Arriving ${formatDay(t.estimatedDelivery)}`
  }
}

/**
 * Carrier-style tracking: headline, progress bar across the five customer-facing checkpoints and the full
 * event history, newest first. Future events aren't listed; the bar shows what's still to come.
 */
export function TrackingPanel({ tracking: t }: { tracking: Tracking }) {
  const closed = t.stage === 'CANCELLED' || t.stage === 'REFUNDED'
  const history = t.events.filter((e) => e.done).reverse()
  const checkpoints = [
    { label: 'Ordered', at: 0 },
    { label: 'Shipped', at: 43 },
    { label: 'Out for delivery', at: 86 },
    { label: 'Delivered', at: 100 },
  ]
  return (
    <section
      aria-labelledby="tracking-title"
      className="flex flex-col gap-5 rounded-card border border-border bg-surface p-6 print:hidden"
    >
      <div className="flex flex-wrap items-start gap-4">
        <span
          className={cn(
            'flex size-11 items-center justify-center rounded-full',
            closed ? 'bg-surface-2 text-ink-muted' : 'bg-trust-subtle text-trust',
          )}
        >
          {t.stage === 'DELIVERED' ? (
            <Package className="size-5" aria-hidden="true" />
          ) : (
            <Truck className="size-5" aria-hidden="true" />
          )}
        </span>
        <div className="min-w-0 flex-1">
          <h2 id="tracking-title" className="text-xl font-bold">
            {deliveryHeadline(t)}
          </h2>
          <p className="text-sm text-ink-muted">
            {t.stageLabel}
            {!closed && (
              <>
                {' · '}
                {t.carrier} <span className="font-mono">{t.trackingNumber}</span>
              </>
            )}
          </p>
        </div>
      </div>

      {!closed && (
        <div>
          <div
            role="progressbar"
            aria-label="Delivery progress"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={t.progress}
            aria-valuetext={t.stageLabel}
            className="relative h-2 rounded-full bg-surface-2"
          >
            <div
              className="absolute inset-y-0 left-0 rounded-full bg-trust transition-[width] duration-700"
              style={{ width: `${String(Math.max(t.progress, 3))}%` }}
            />
          </div>
          <ol className="mt-2 grid grid-cols-4 text-xs text-ink-muted">
            {checkpoints.map((c, i) => (
              <li
                key={c.label}
                className={cn(
                  i === 0 ? 'text-left' : i === checkpoints.length - 1 ? 'text-right' : 'text-center',
                  t.progress >= c.at && 'font-semibold text-ink',
                )}
              >
                {c.label}
              </li>
            ))}
          </ol>
        </div>
      )}

      <div>
        <h3 className="mb-2 text-sm font-semibold">Shipment history</h3>
        <ol className="flex flex-col">
          {history.map((e, i) => (
            <li key={e.stage} className="relative flex gap-3 pb-4 last:pb-0">
              {i < history.length - 1 && (
                <span aria-hidden="true" className="absolute left-[9px] top-5 h-full w-px bg-border" />
              )}
              <span
                className={cn(
                  'relative mt-0.5 flex size-[19px] shrink-0 items-center justify-center rounded-full',
                  i === 0 ? 'bg-trust text-white' : 'bg-surface-2 text-ink-muted',
                )}
              >
                <Check className="size-3" aria-hidden="true" />
              </span>
              <div className="min-w-0 text-sm">
                <p className={cn('font-semibold', i !== 0 && 'text-ink-muted')}>{e.label}</p>
                <p className="text-ink-muted">{e.description}</p>
                <p className="text-xs text-ink-subtle">
                  <time dateTime={e.at}>{new Date(e.at).toLocaleString('en-US', stampFmt)}</time>
                  {e.location && ` · ${e.location}`}
                </p>
              </div>
            </li>
          ))}
        </ol>
      </div>
    </section>
  )
}
