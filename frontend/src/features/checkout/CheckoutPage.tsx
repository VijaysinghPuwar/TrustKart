import { Check } from 'lucide-react'
import { useState } from 'react'
import { Link, Navigate } from 'react-router'
import { AddressLines } from '@/components/address/AddressLines'
import { Button, ButtonLink } from '@/components/ui/Button'
import { ErrorState } from '@/components/ui/ErrorState'
import { PageSpinner } from '@/components/ui/PageSpinner'
import { useAddresses, useQuote } from '@/data/shopping'
import { ApiError } from '@/lib/api'
import { cn } from '@/lib/cn'
import { formatMoney } from '@/lib/money'
import { usePageTitle } from '@/lib/usePageTitle'
import { DeliveryStep } from './DeliveryStep'
import { destinationLabel, destinationPayload, type Destination } from './destination'
import { usePlaceVirtualOrder } from './usePlaceVirtualOrder'
import { WalletSummary } from './WalletSummary'

const STEPS = ['Review cart', 'Delivery', 'Virtual payment', 'Review purchase'] as const

/** Four-step virtual checkout. Every number shown comes from the server's quote; nothing is computed here. */
export default function CheckoutPage() {
  usePageTitle('Virtual checkout')
  const quote = useQuote()
  const addresses = useAddresses()
  const [step, setStep] = useState(0)
  const [chosen, setDestination] = useState<Destination | null>(null)
  const [deliveryError, setDeliveryError] = useState<string>()
  const place = usePlaceVirtualOrder()
  const [placeError, setPlaceError] = useState<ApiError | null>(null)

  // Until the shopper picks something, their default saved address is preselected.
  const defaultAddress = addresses.data?.find((a) => a.isDefault)
  const destination: Destination | null =
    chosen ?? (defaultAddress ? { kind: 'saved', address: defaultAddress } : null)

  if (quote.isPending) return <PageSpinner label="Pricing your cart" />
  if (quote.isError)
    return (
      <ErrorState
        error={quote.error}
        title="We couldn’t price your cart"
        onRetry={() => void quote.refetch()}
      />
    )
  // After a successful order the emptied cart refetches while the receipt page is still loading; without this
  // guard the "empty cart" redirect below would win the race and hide the confirmation.
  if (place.isSuccess) return <PageSpinner label="Opening your receipt" />
  const q = quote.data
  if (q.lines.length === 0) return <Navigate to="/cart" replace />
  const blocked = q.lines.some((l) => l.issue)

  function next() {
    if (step === 1 && !destination) {
      setDeliveryError('Choose an address or one of the virtual destinations.')
      return
    }
    setDeliveryError(undefined)
    setStep((s) => Math.min(s + 1, STEPS.length - 1))
  }

  function placeOrder() {
    if (!destination) return
    setPlaceError(null)
    place.submit({ ...destinationPayload(destination), expectedTotal: q.total }, (e) => {
      setPlaceError(e instanceof ApiError ? e : null)
      void quote.refetch()
    })
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-baseline gap-3">
        <h1 className="text-[32px] font-bold leading-10">Virtual checkout</h1>
        <span className="rounded-badge bg-primary-subtle px-2 py-0.5 text-xs font-semibold text-primary-hover">
          Simulation
        </span>
      </div>
      <ol className="flex flex-wrap gap-x-6 gap-y-2" aria-label="Checkout progress">
        {STEPS.map((label, i) => (
          <li
            key={label}
            aria-current={i === step ? 'step' : undefined}
            className="flex items-center gap-2 text-sm"
          >
            <span
              className={cn(
                'flex size-7 items-center justify-center rounded-full text-xs font-bold',
                i < step
                  ? 'bg-trust text-white'
                  : i === step
                    ? 'bg-primary text-on-primary'
                    : 'bg-surface-2 text-ink-muted',
              )}
            >
              {i < step ? <Check className="size-4" aria-label="done" /> : i + 1}
            </span>
            <span className={i === step ? 'font-semibold' : 'text-ink-muted'}>{label}</span>
          </li>
        ))}
      </ol>

      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
        <section
          className="rounded-card border border-border bg-surface p-5 sm:p-6"
          aria-labelledby="step-heading"
        >
          <h2 id="step-heading" className="mb-4 text-xl font-semibold">
            {STEPS[step]}
          </h2>

          {step === 0 && (
            <div className="flex flex-col gap-4">
              <ul className="divide-y divide-border">
                {q.lines.map((l) => (
                  <li key={l.productId} className="flex items-center gap-3 py-3">
                    {l.imageUrl && (
                      <img
                        src={l.imageUrl}
                        alt=""
                        width={56}
                        height={56}
                        className="tk-product-img size-14 rounded-control bg-surface-2 object-contain p-1"
                      />
                    )}
                    <div className="min-w-0 flex-1">
                      <Link to={`/p/${l.slug}`} className="line-clamp-2 text-sm text-ink">
                        {l.name}
                      </Link>
                      <p className="text-[13px] text-ink-muted tabular">
                        {l.quantity} × {formatMoney(l.unitPrice)}
                      </p>
                      {l.issue && (
                        <p className="text-[13px] text-danger">
                          This item is no longer available in that quantity.
                        </p>
                      )}
                    </div>
                    <span className="font-semibold tabular">{formatMoney(l.lineTotal)}</span>
                  </li>
                ))}
              </ul>
              <Link to="/cart" className="text-sm font-semibold">
                Edit cart
              </Link>
            </div>
          )}

          {step === 1 && <DeliveryStep value={destination} onChange={setDestination} />}

          {step === 2 && (
            <div className="flex flex-col gap-4">
              <div className="rounded-card border border-border p-4">
                <p className="mb-3 text-lg font-semibold">TrustKart Wallet</p>
                <WalletSummary quote={q} />
              </div>
              <p className="flex items-center gap-2 text-sm font-semibold text-trust">
                <Check className="size-4" aria-hidden="true" />
                No real payment information is required.
              </p>
              {q.shortfall && (
                <div className="flex flex-wrap items-center gap-3 rounded-control bg-danger-subtle p-3 text-sm">
                  <span className="flex-1">You need {formatMoney(q.shortfall)} more virtual funds.</span>
                  <ButtonLink to="/wallet?add=1" size="sm">
                    Add virtual funds
                  </ButtonLink>
                </div>
              )}
            </div>
          )}

          {step === 3 && destination && (
            <div className="flex flex-col gap-4">
              <p className="text-2xl font-bold">Review your virtual purchase</p>
              <dl className="grid grid-cols-[1fr_auto] gap-y-2 text-sm tabular">
                <dt className="text-ink-muted">Products</dt>
                <dd className="text-right">{q.itemCount}</dd>
                <dt className="text-ink-muted">Delivery</dt>
                <dd className="text-right">
                  {destinationLabel(destination)}
                  {destination.kind !== 'preset' && (
                    <AddressLines
                      address={destination.address}
                      className="text-[13px] not-italic leading-5 text-ink-muted"
                    />
                  )}
                </dd>
                <dt className="text-ink-muted">Subtotal</dt>
                <dd className="text-right">{formatMoney(q.subtotal)}</dd>
                <dt className="text-ink-muted">Shipping</dt>
                <dd className="text-right">{formatMoney(q.shipping)}</dd>
                <dt className="font-bold">Virtual total</dt>
                <dd className="text-right font-bold">{formatMoney(q.total)}</dd>
                {q.walletMode === 'BUDGET' && q.balanceAfter && (
                  <>
                    <dt className="text-ink-muted">Virtual balance after purchase</dt>
                    <dd className="text-right">{formatMoney(q.balanceAfter)}</dd>
                  </>
                )}
              </dl>
              <ul className="flex flex-col gap-1.5 text-sm text-trust">
                {[
                  'No real money will be charged',
                  'No payment information required',
                  'Nothing will be physically shipped',
                ].map((t) => (
                  <li key={t} className="flex items-center gap-2 font-semibold">
                    <Check className="size-4" aria-hidden="true" />
                    {t}
                  </li>
                ))}
              </ul>
              {placeError && (
                <div role="alert" className="rounded-control bg-danger-subtle p-3 text-sm">
                  {placeError.message}
                  {placeError.code === 'PRICE_CHANGED' && ' The totals above are now up to date.'}
                  {placeError.supportReference && (
                    <span className="mt-1 block font-mono text-xs">
                      Reference {placeError.supportReference}
                    </span>
                  )}
                </div>
              )}
            </div>
          )}

          <div className="mt-6 flex flex-wrap items-center gap-3 border-t border-border pt-5">
            {step > 0 && (
              <Button
                variant="secondary"
                size="lg"
                onClick={() => setStep((s) => s - 1)}
                disabled={place.isPending}
              >
                Back
              </Button>
            )}
            {deliveryError && step === 1 && (
              <p role="alert" className="text-sm text-danger">
                {deliveryError}
              </p>
            )}
            {step < 3 ? (
              <Button
                size="lg"
                className="ml-auto"
                onClick={next}
                disabled={(step === 0 && blocked) || (step === 2 && !q.canPlace)}
              >
                Continue
              </Button>
            ) : (
              <Button
                size="lg"
                className="ml-auto min-w-56"
                onClick={placeOrder}
                loading={place.isPending}
                disabled={!q.canPlace}
              >
                Place Virtual Order
              </Button>
            )}
          </div>
        </section>

        <aside
          aria-label="Order summary"
          className="flex flex-col gap-3 rounded-card border border-border bg-surface p-5 lg:sticky lg:top-40"
        >
          <p className="font-semibold">Order summary</p>
          <dl className="flex flex-col gap-1.5 text-sm tabular">
            <div className="flex">
              <dt>{q.itemCount} items</dt>
              <dd className="ml-auto">{formatMoney(q.subtotal)}</dd>
            </div>
            <div className="flex text-ink-muted">
              <dt>Shipping</dt>
              <dd className="ml-auto">{formatMoney(q.shipping)}</dd>
            </div>
            <div className="flex border-t border-border pt-2 text-base font-bold">
              <dt>Virtual total</dt>
              <dd className="ml-auto">{formatMoney(q.total)}</dd>
            </div>
          </dl>
          <p className="text-[13px] text-ink-muted">
            Simulation only. No real payment is processed and nothing ships.
          </p>
        </aside>
      </div>
    </div>
  )
}
