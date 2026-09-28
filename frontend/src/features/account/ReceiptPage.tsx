import { Printer } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { AddressLines } from '@/components/address/AddressLines'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Dialog } from '@/components/ui/Dialog'
import { ErrorState } from '@/components/ui/ErrorState'
import { PageSpinner } from '@/components/ui/PageSpinner'
import { useToast } from '@/components/ui/Toast'
import { useMe } from '@/data/account'
import { usePurchase, useRefund } from '@/data/shopping'
import { formatMoney } from '@/lib/money'
import { usePageTitle } from '@/lib/usePageTitle'
import { Celebration } from '@/features/checkout/Celebration'
import { presetLabel } from './presets'
import { TrackingPanel, formatDay } from './TrackingPanel'

const STATUS_BADGE = {
  COMPLETED: '✓ Order confirmed',
  CANCELLED: 'Cancelled · Refunded',
  REFUNDED: 'Returned · Refunded',
} as const

/** Virtual receipt, doubling as the confirmation page right after an order (?placed=1). */
export default function ReceiptPage() {
  const { id = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const purchase = usePurchase(id)
  const { data: me } = useMe()
  const refund = useRefund()
  const { notify } = useToast()
  const [confirmRefund, setConfirmRefund] = useState(false)
  const placed = params.get('placed') === '1'
  usePageTitle(purchase.data ? `Receipt ${purchase.data.orderNumber}` : 'Receipt')

  if (purchase.isPending) return <PageSpinner />
  if (purchase.isError) return <ErrorState error={purchase.error} title="Receipt not found" />
  const p = purchase.data
  const t = p.tracking
  const date = new Date(p.createdAt)

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-center gap-3">
        <Link to="/account/purchases" className="text-sm">
          ← All orders
        </Link>
        <Button variant="secondary" size="sm" className="ml-auto print:hidden" onClick={() => window.print()}>
          <Printer className="size-4" aria-hidden="true" />
          Print receipt
        </Button>
      </div>
      <TrackingPanel tracking={p.tracking} />
      <article className="flex flex-col gap-5 rounded-card border border-border bg-surface p-6">
        <header className="flex flex-wrap items-start gap-4">
          <div>
            <p className="text-sm font-semibold text-ink-muted">TrustKart · Order receipt</p>
            <h1 className="font-mono text-2xl font-bold">{p.orderNumber}</h1>
            <p className="text-sm text-ink-muted">
              {date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })} at{' '}
              {date.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })}
            </p>
          </div>
          <Badge tone={p.status === 'COMPLETED' ? 'trust' : 'neutral'} className="ml-auto h-7 px-3 text-xs">
            {STATUS_BADGE[p.status]}
          </Badge>
        </header>
        <table className="w-full text-sm">
          <caption className="sr-only">Items</caption>
          <thead>
            <tr className="border-b border-border text-left text-ink-muted">
              <th scope="col" className="py-2 font-medium">
                Item
              </th>
              <th scope="col" className="py-2 text-right font-medium">
                Qty
              </th>
              <th scope="col" className="py-2 text-right font-medium">
                Price
              </th>
              <th scope="col" className="py-2 text-right font-medium">
                Total
              </th>
            </tr>
          </thead>
          <tbody>
            {p.items.map((i) => (
              <tr key={i.productId} className="border-b border-border">
                <td className="py-2.5 pr-3">
                  <Link to={`/p/${i.slug}`} className="text-ink">
                    {i.name}
                  </Link>
                  <span className="block text-xs text-ink-muted">{i.categoryName}</span>
                </td>
                <td className="py-2.5 text-right tabular">{i.quantity}</td>
                <td className="py-2.5 text-right tabular">{formatMoney(i.unitPrice)}</td>
                <td className="py-2.5 text-right font-semibold tabular">{formatMoney(i.lineTotal)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <dl className="ml-auto grid w-full max-w-sm grid-cols-[1fr_auto] gap-y-1.5 text-sm tabular">
          <dt className="text-ink-muted">Subtotal</dt>
          <dd className="text-right">{formatMoney(p.subtotal)}</dd>
          <dt className="text-ink-muted">Shipping</dt>
          <dd className="text-right">{formatMoney(p.shipping)}</dd>
          <dt className="font-bold">Total</dt>
          <dd className="text-right font-bold">{formatMoney(p.total)}</dd>
          <dt className="text-ink-muted">Paid with</dt>
          <dd className="text-right">TrustKart Wallet</dd>
          {p.balanceBefore && (
            <>
              <dt className="text-ink-muted">Wallet balance before</dt>
              <dd className="text-right">{formatMoney(p.balanceBefore)}</dd>
            </>
          )}
          {p.balanceAfter && (
            <>
              <dt className="text-ink-muted">Wallet balance after</dt>
              <dd className="text-right">{formatMoney(p.balanceAfter)}</dd>
            </>
          )}
          <dt className="text-ink-muted">Deliver to</dt>
          <dd className="text-right">
            {p.simulationAddress?.label ?? presetLabel(p.deliveryPreset)}
            {p.simulationAddress?.line1 && (
              <AddressLines
                address={p.simulationAddress}
                className="text-[13px] not-italic leading-5 text-ink-muted"
              />
            )}
          </dd>
        </dl>
        <footer className="border-t border-border pt-4 text-center text-xs text-ink-muted">
          Thank you for shopping with TrustKart. Questions about this order? See our{' '}
          <a href="/about">store policy</a>.
        </footer>
      </article>
      {p.status === 'COMPLETED' && (
        <div className="flex flex-wrap items-center gap-3 print:hidden">
          {t.canCancel || t.canReturn ? (
            <Button variant="secondary" onClick={() => setConfirmRefund(true)}>
              {t.canCancel ? 'Cancel this order' : 'Return items'}
            </Button>
          ) : null}
          <p className="text-sm text-ink-muted">
            {t.canCancel
              ? 'You can cancel until your order ships.'
              : t.canReturn && t.returnBy
                ? `Eligible for return until ${formatDay(t.returnBy)}.`
                : t.deliveredAt
                  ? 'The return window for this order has closed.'
                  : 'Your order has shipped. You can return it once it’s delivered.'}
          </p>
        </div>
      )}
      <Dialog
        open={confirmRefund}
        onClose={() => setConfirmRefund(false)}
        title={t.canCancel ? 'Cancel this order?' : 'Return these items?'}
        description={
          p.walletMode === 'BUDGET'
            ? `${formatMoney(p.total)} goes back to your wallet and the items leave your collection.`
            : 'The items leave your collection.'
        }
        footer={
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setConfirmRefund(false)}>
              Keep it
            </Button>
            <Button
              variant="danger"
              loading={refund.isPending}
              onClick={() =>
                refund.mutate(p.id, {
                  onSuccess: () => {
                    setConfirmRefund(false)
                    notify(
                      t.canCancel
                        ? 'Order cancelled and refunded to your wallet'
                        : 'Return complete. Your refund is in your wallet',
                    )
                  },
                })
              }
            >
              {t.canCancel ? 'Cancel and refund' : 'Return and refund'}
            </Button>
          </div>
        }
      >
        <p className="text-sm text-ink-muted">Refunds go straight back to your TrustKart Wallet.</p>
      </Dialog>
      <Celebration
        name={me?.profile?.displayName}
        purchase={p}
        open={placed}
        onClose={() => {
          params.delete('placed')
          setParams(params, { replace: true })
        }}
      />
    </div>
  )
}
