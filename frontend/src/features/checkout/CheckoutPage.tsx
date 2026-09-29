import { useIsMutating } from '@tanstack/react-query'
import { Check, Lock, Truck } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Link, Navigate } from 'react-router'
import { AddressLines } from '@/components/address/AddressLines'
import { Button, ButtonLink } from '@/components/ui/Button'
import { ErrorState } from '@/components/ui/ErrorState'
import { PageSpinner } from '@/components/ui/PageSpinner'
import { useMe } from '@/data/account'
import { ADDRESS_CREATE, useAddresses, useQuote } from '@/data/shopping'
import { ApiError } from '@/lib/api'
import { cn } from '@/lib/cn'
import { formatMoney } from '@/lib/money'
import { usePageTitle } from '@/lib/usePageTitle'
import { DeliveryStep } from './DeliveryStep'
import { estimatedDelivery } from './delivery'
import { destinationLabel, destinationPayload, type Destination } from './destination'
import { ProcessingOverlay } from './ProcessingOverlay'
import { usePlaceVirtualOrder } from './usePlaceVirtualOrder'
import { WalletCard } from './WalletCard'
import { WalletSummary } from './WalletSummary'

const STEPS = ['Cart', 'Delivery', 'Payment', 'Review'] as const

/** Four-step checkout. Every number shown comes from the server's quote; nothing is computed in the browser. */
export default function CheckoutPage() {
  usePageTitle('Checkout')
  const quote = useQuote()
  const addresses = useAddresses()
  const { data: me } = useMe()
  const [step, setStep] = useState(0)
  const [chosen, setDestination] = useState<Destination | null>(null)
  const [deliveryError, setDeliveryError] = useState<string>()
  const savingAddress = useIsMutating({ mutationKey: ADDRESS_CREATE }) > 0
  const place = usePlaceVirtualOrder()
  const [placeError, setPlaceError] = useState<ApiError | null>(null)

  const defaultAddress = addresses.data?.find((a) => a.isDefault)
  const destination: Destination | null =
    chosen ?? (defaultAddress ? { kind: 'saved', address: defaultAddress } : null)

  if (quote.isPending) return <PageSpinner label="Preparing checkout" />
  if (quote.isError)
    return (
      <ErrorState
        error={quote.error}
        title="We couldn’t load checkout"
        onRetry={() => void quote.refetch()}
      />
    )
  // After the order succeeds the emptied cart refetches while the confirmation loads; don't bounce to /cart.
  if (place.isSuccess || place.processing) {
    return (
      <>
        <PageSpinner label="Opening your order" />
        {place.processing && <ProcessingOverlay done={place.confirmed} />}
      </>
    )
  }
  const q = quote.data
  if (q.lines.length === 0) return <Navigate to="/cart" replace />
  const blocked = q.lines.some((l) => l.issue)

  function next() {
    if (step === 1 && !destination) {
      setDeliveryError('Choose a delivery address to continue.')
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
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-[32px] font-bold leading-10">Checkout</h1>
        <span className="flex items-center gap-1.5 text-sm font-semibold text-trust">
          <Lock className="size-4" aria-hidden="true" />
          Secure checkout
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
            {['Your cart', 'Delivery address', 'Payment method', 'Review your order'][step]}
          </h2>

          {step === 0 && (
            <div className="flex flex-col gap-4">
              <ItemList lines={q.lines} />
              <Link to="/cart" className="text-sm font-semibold">
                Edit cart
              </Link>
            </div>
          )}

          {step === 1 && <DeliveryStep value={destination} onChange={setDestination} />}

          {step === 2 && (
            <div className="flex flex-col gap-5">
              <label className="flex cursor-pointer flex-col gap-4 rounded-card border-2 border-primary p-4 sm:flex-row sm:items-center">
                <input type="radio" name="payment" defaultChecked className="sr-only" />
                <WalletCard balance={q.balance} mode={q.walletMode} holder={me?.profile?.displayName} />
                <div className="flex flex-1 flex-col gap-3">
                  <p className="flex items-center gap-2 font-semibold">
                    <span className="flex size-5 items-center justify-center rounded-full bg-primary text-on-primary">
                      <Check className="size-3.5" strokeWidth={3} aria-hidden="true" />
                    </span>
                    TrustKart Wallet
                  </p>
                  <WalletSummary quote={q} />
                </div>
              </label>
              {q.shortfall && (
                <div className="flex flex-wrap items-center gap-3 rounded-control bg-danger-subtle p-3 text-sm">
                  <span className="flex-1">
                    Your wallet is {formatMoney(q.shortfall)} short for this order.
                  </span>
                  <ButtonLink to="/wallet?add=1" size="sm">
                    Add funds
                  </ButtonLink>
                </div>
              )}
            </div>
          )}

          {step === 3 && destination && (
            <div className="flex flex-col gap-4">
              <div className="grid gap-3 sm:grid-cols-2">
                <ReviewCard title="Delivery address" onChange={() => setStep(1)}>
                  <p className="font-medium">{destinationLabel(destination)}</p>
                  {destination.kind !== 'preset' && (
                    <AddressLines
                      address={destination.address}
                      className="text-sm not-italic leading-5 text-ink-muted"
                    />
                  )}
                  <p className="mt-2 flex items-center gap-1.5 text-sm text-trust">
                    <Truck className="size-4" aria-hidden="true" />
                    Free delivery · arrives {estimatedDelivery()}
                  </p>
                </ReviewCard>
                <ReviewCard title="Payment method" onChange={() => setStep(2)}>
                  <p className="font-medium">TrustKart Wallet</p>
                  <p className="text-sm text-ink-muted tabular">
                    Balance {q.walletMode === 'UNLIMITED' ? '∞ Unlimited' : formatMoney(q.balance)}
                  </p>
                </ReviewCard>
              </div>
              <ReviewCard title={`Items (${String(q.itemCount)})`} onChange={() => setStep(0)}>
                <ItemList lines={q.lines} />
              </ReviewCard>
              {placeError && (
                <div role="alert" className="rounded-control bg-danger-subtle p-3 text-sm">
                  {placeError.message}
                  {placeError.code === 'PRICE_CHANGED' && ' The totals are now up to date.'}
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
                loading={step === 1 && savingAddress}
                disabled={
                  (step === 0 && blocked) || (step === 1 && savingAddress) || (step === 2 && !q.canPlace)
                }
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
                Place order · {formatMoney(q.total)}
              </Button>
            )}
          </div>
          {step === 3 && (
            <p className="mt-3 text-right text-xs text-ink-muted">
              By placing your order, you agree to TrustKart’s <Link to="/about">Store Policy</Link>.
            </p>
          )}
        </section>

        <aside
          aria-label="Order summary"
          className="flex flex-col gap-3 rounded-card border border-border bg-surface p-5 lg:sticky lg:top-32"
        >
          <p className="font-semibold">Order summary</p>
          <dl className="flex flex-col gap-1.5 text-sm tabular">
            <div className="flex">
              <dt>Items ({q.itemCount})</dt>
              <dd className="ml-auto">{formatMoney(q.subtotal)}</dd>
            </div>
            <div className="flex text-ink-muted">
              <dt>Delivery</dt>
              <dd className="ml-auto text-trust">Free</dd>
            </div>
            <div className="flex border-t border-border pt-2 text-base font-bold">
              <dt>Order total</dt>
              <dd className="ml-auto">{formatMoney(q.total)}</dd>
            </div>
          </dl>
          <p className="flex items-center gap-1.5 text-[13px] text-ink-muted">
            <Lock className="size-3.5" aria-hidden="true" />
            Your payment is protected by TrustKart Secure Checkout.
          </p>
        </aside>
      </div>
    </div>
  )
}

function ReviewCard({
  title,
  onChange,
  children,
}: {
  title: string
  onChange: () => void
  children: ReactNode
}) {
  return (
    <section className="rounded-card border border-border p-4">
      <div className="mb-2 flex items-center">
        <h3 className="text-sm font-semibold text-ink-muted">{title}</h3>
        <button
          type="button"
          onClick={onChange}
          className="ml-auto text-sm font-semibold text-primary hover:underline"
        >
          Change<span className="sr-only"> {title.toLowerCase()}</span>
        </button>
      </div>
      {children}
    </section>
  )
}

function ItemList({
  lines,
}: {
  lines: {
    productId: number
    slug: string
    name: string
    optionsLabel?: string
    imageUrl?: string
    unitPrice: string
    quantity: number
    lineTotal: string
    issue?: string
  }[]
}) {
  return (
    <ul className="divide-y divide-border">
      {lines.map((l) => (
        <li key={`${String(l.productId)}-${l.optionsLabel ?? ''}`} className="flex items-center gap-3 py-3">
          {l.imageUrl && (
            <span className="tk-img-well flex size-14 shrink-0 items-center justify-center rounded-control bg-surface-2 p-1">
              <img
                src={l.imageUrl}
                alt=""
                width={56}
                height={56}
                loading="lazy"
                decoding="async"
                className="tk-product-img max-h-full max-w-full object-contain"
              />
            </span>
          )}
          <div className="min-w-0 flex-1">
            <Link to={`/p/${l.slug}`} className="line-clamp-2 text-sm text-ink">
              {l.name}
            </Link>
            {l.optionsLabel && <p className="text-[13px] text-ink-muted">{l.optionsLabel}</p>}
            <p className="text-[13px] text-ink-muted tabular">
              Qty {l.quantity} · {formatMoney(l.unitPrice)} each
            </p>
            {l.issue && <p className="text-[13px] text-danger">No longer available in that quantity.</p>}
            {/* On narrow phones the total moves under the name instead of squeezing it. */}
            <p className="font-semibold tabular sm:hidden">{formatMoney(l.lineTotal)}</p>
          </div>
          <span className="shrink-0 font-semibold tabular max-sm:hidden">{formatMoney(l.lineTotal)}</span>
        </li>
      ))}
    </ul>
  )
}
