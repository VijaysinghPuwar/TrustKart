import { Link } from 'react-router'
import { Button } from '@/components/ui/Button'
import { Dialog } from '@/components/ui/Dialog'
import { ErrorState } from '@/components/ui/ErrorState'
import { PageSpinner } from '@/components/ui/PageSpinner'
import { useQuote } from '@/data/shopping'
import { ApiError } from '@/lib/api'
import { formatMoney } from '@/lib/money'
import { usePlaceVirtualOrder } from '@/features/checkout/usePlaceVirtualOrder'
import { WalletSummary } from '@/features/checkout/WalletSummary'
import type { ProductCard } from '@/lib/types'

/** Instant Virtual Buy always asks for confirmation, priced by the server, so one stray click never buys. */
export function InstantBuyDialog({
  product,
  quantity,
  open,
  onClose,
}: {
  product: ProductCard
  quantity: number
  open: boolean
  onClose: () => void
}) {
  const quote = useQuote(open ? { productId: product.id, quantity } : undefined)
  const place = usePlaceVirtualOrder()
  const q = quote.data
  const error = place.error instanceof ApiError ? place.error : null

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Confirm Instant Virtual Buy"
      description="Paid from your TrustKart Wallet. No real money is used and nothing ships."
      footer={
        q && (
          <div className="flex flex-col gap-2">
            <Button
              size="lg"
              disabled={!q.canPlace}
              loading={place.isPending}
              onClick={() =>
                place.submit({
                  deliveryPreset: 'COLLECTION',
                  expectedTotal: q.total,
                  instant: { productId: product.id, quantity },
                })
              }
            >
              Place Virtual Order · {formatMoney(q.total)}
            </Button>
            {q.shortfall && (
              <Link to="/wallet?add=1" className="text-center text-sm font-semibold" onClick={onClose}>
                Add virtual funds
              </Link>
            )}
          </div>
        )
      }
    >
      {quote.isPending ? (
        <PageSpinner label="Pricing your order" />
      ) : quote.isError ? (
        <ErrorState error={quote.error} title="We couldn’t price this order" />
      ) : q ? (
        <div className="flex flex-col gap-4">
          <p className="text-sm">
            {quantity} × <span className="font-semibold">{product.name}</span>
          </p>
          <WalletSummary quote={q} />
          {q.shortfall && (
            <p className="text-sm text-danger">
              You need {formatMoney(q.shortfall)} more virtual funds, or switch to Unlimited mode.
            </p>
          )}
          {error && (
            <p className="text-sm text-danger" role="alert">
              {error.message}
            </p>
          )}
        </div>
      ) : null}
    </Dialog>
  )
}
