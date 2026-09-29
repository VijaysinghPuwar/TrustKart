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
import { ProcessingOverlay } from '@/features/checkout/ProcessingOverlay'
import type { OptionSelection, ProductCard } from '@/lib/types'

/** Instant Virtual Buy always asks for confirmation, priced by the server, so one stray click never buys. */
export function InstantBuyDialog({
  product,
  quantity,
  options,
  open,
  onClose,
}: {
  product: ProductCard
  quantity: number
  options?: OptionSelection
  open: boolean
  onClose: () => void
}) {
  // Only while the dialog is open: a closed dialog used to fetch the cart quote on every product page.
  const quote = useQuote({ productId: product.id, quantity, options }, { enabled: open })
  const place = usePlaceVirtualOrder()
  const q = quote.data
  const error = place.error instanceof ApiError ? place.error : null

  if (place.processing) return <ProcessingOverlay done={place.confirmed} />
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Confirm your order"
      description="Paid with your TrustKart Wallet. Free delivery."
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
                  instant: {
                    productId: product.id,
                    quantity,
                    ...(options && Object.keys(options).length ? { options } : {}),
                  },
                })
              }
            >
              Place order · {formatMoney(q.total)}
            </Button>
            {q.shortfall && (
              <Link to="/wallet?add=1" className="text-center text-sm font-semibold" onClick={onClose}>
                Add funds to wallet
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
            {q.lines[0]?.optionsLabel && (
              <span className="block text-[13px] text-ink-muted">{q.lines[0].optionsLabel}</span>
            )}
          </p>
          <WalletSummary quote={q} />
          {q.shortfall && (
            <p className="text-sm text-danger">
              Your wallet is {formatMoney(q.shortfall)} short for this order. Add funds or switch to Unlimited
              mode.
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
