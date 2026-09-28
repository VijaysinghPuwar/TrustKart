import { ShoppingCart } from 'lucide-react'
import { Link } from 'react-router'
import { ProductImage } from '@/components/commerce/ProductImage'
import { cartIssueText } from '@/components/commerce/cartIssue'
import { Button, ButtonLink } from '@/components/ui/Button'
import { ErrorState } from '@/components/ui/ErrorState'
import { PageSpinner } from '@/components/ui/PageSpinner'
import { QuantityStepper } from '@/components/ui/QuantityStepper'
import { useCart, useMoveToWishlist, useRemoveCartLine, useUpdateCartLine, useWallet } from '@/data/shopping'
import { formatMoney, subtractMoney } from '@/lib/money'
import { usePageTitle } from '@/lib/usePageTitle'
import type { CartLine } from '@/lib/types'

export default function CartPage() {
  usePageTitle('Cart')
  const cart = useCart()
  const { data: wallet } = useWallet()
  if (cart.isPending) return <PageSpinner />
  if (cart.isError) return <ErrorState error={cart.error} onRetry={() => void cart.refetch()} />
  const { items, savedForLater, subtotal, itemCount } = cart.data
  const unlimited = wallet?.mode === 'UNLIMITED'
  const remaining = wallet ? subtractMoney(wallet.balance, subtotal) : null
  const short = remaining?.startsWith('-') ?? false
  const blocked = items.some((i) => i.issue)

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-[32px] font-bold leading-10">Cart</h1>
      {items.length === 0 ? (
        <div className="flex flex-col items-center gap-3 rounded-card border border-border bg-surface px-6 py-14 text-center">
          <ShoppingCart className="size-10 text-ink-subtle" aria-hidden="true" />
          <p className="font-semibold">Your cart is empty.</p>
          <p className="text-sm text-ink-muted">
            Search for anything you’d love to own. Describe it however you like.
          </p>
          <ButtonLink to="/search" variant="secondary">
            Start browsing
          </ButtonLink>
        </div>
      ) : (
        <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
          <ul className="divide-y divide-border rounded-card border border-border bg-surface px-5">
            {items.map((line) => (
              <CartRow key={line.id} line={line} />
            ))}
          </ul>
          <aside
            aria-label="Order summary"
            className="flex flex-col gap-4 rounded-card border border-border bg-surface p-5 lg:sticky lg:top-40"
          >
            <dl className="flex flex-col gap-2 text-sm tabular">
              <div className="flex">
                <dt>Subtotal ({itemCount} items)</dt>
                <dd className="ml-auto font-semibold">{formatMoney(subtotal)}</dd>
              </div>
              <div className="flex text-ink-muted">
                <dt>Shipping</dt>
                <dd className="ml-auto">Free</dd>
              </div>
              <div className="flex border-t border-border pt-2 text-base font-bold">
                <dt>Total</dt>
                <dd className="ml-auto">{formatMoney(subtotal)}</dd>
              </div>
              {wallet && (
                <>
                  <div className="flex text-ink-muted">
                    <dt>Wallet balance</dt>
                    <dd className="ml-auto">{unlimited ? '∞ Unlimited' : formatMoney(wallet.balance)}</dd>
                  </div>
                  {!unlimited && remaining && (
                    <div className="flex">
                      <dt className={short ? 'text-danger' : 'text-ink-muted'}>
                        {short ? 'Wallet short by' : 'Remaining after purchase'}
                      </dt>
                      <dd className={short ? 'ml-auto font-semibold text-danger' : 'ml-auto font-semibold'}>
                        {formatMoney(remaining.replace('-', ''))}
                      </dd>
                    </div>
                  )}
                </>
              )}
            </dl>
            <ButtonLink
              to="/checkout"
              size="lg"
              aria-disabled={blocked || undefined}
              className={blocked ? 'pointer-events-none opacity-50' : undefined}
            >
              Proceed to checkout
            </ButtonLink>
            {blocked && (
              <p className="text-[13px] text-danger">Resolve the items marked above to continue.</p>
            )}
            {short && (
              <Link to="/wallet?add=1" className="text-center text-sm font-semibold">
                Add funds to wallet
              </Link>
            )}
            <p className="text-center text-[13px] text-ink-muted">
              Free delivery on every order. Final total confirmed at checkout.
            </p>
          </aside>
        </div>
      )}
      {savedForLater.length > 0 && (
        <section aria-labelledby="saved-heading" className="flex flex-col gap-3">
          <h2 id="saved-heading" className="text-[22px] font-bold">
            Saved for later
          </h2>
          <ul className="divide-y divide-border rounded-card border border-border bg-surface px-5">
            {savedForLater.map((line) => (
              <CartRow key={line.id} line={line} saved />
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}

function CartRow({ line, saved = false }: { line: CartLine; saved?: boolean }) {
  const update = useUpdateCartLine()
  const remove = useRemoveCartLine()
  const move = useMoveToWishlist()
  const link = `/p/${line.product.slug}`
  return (
    <li className="flex gap-4 py-5">
      <Link
        to={link}
        tabIndex={-1}
        aria-hidden="true"
        className="tk-img-well flex size-24 shrink-0 items-center justify-center rounded-control bg-surface-2 p-2"
      >
        <ProductImage image={line.product.image} sizes="96px" />
      </Link>
      <div className="flex min-w-0 flex-1 flex-col gap-2">
        <div className="flex flex-wrap items-start gap-x-4 gap-y-1">
          <Link to={link} className="min-w-0 flex-1 font-medium text-ink">
            {line.product.name}
          </Link>
          <span className="font-bold tabular">{formatMoney(line.lineTotal)}</span>
        </div>
        <p className="text-[13px] text-ink-muted tabular">{formatMoney(line.unitPrice)} each</p>
        {line.issue && <p className="text-[13px] text-danger">{cartIssueText(line.issue)}</p>}
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
          {!saved && (
            <QuantityStepper
              size="sm"
              value={line.quantity}
              max={Math.max(line.product.maxQuantity, line.quantity)}
              label={`Quantity of ${line.product.name}`}
              disabled={update.isPending}
              onChange={(quantity) => update.mutate({ id: line.id, quantity })}
            />
          )}
          <Button
            variant="ghost"
            size="sm"
            onClick={() => update.mutate({ id: line.id, savedForLater: !saved })}
          >
            {saved ? 'Move to cart' : 'Save for later'}
          </Button>
          <Button variant="ghost" size="sm" onClick={() => move.mutate(line.id)}>
            Move to wishlist
          </Button>
          <Button variant="ghost" size="sm" onClick={() => remove.mutate(line.id)}>
            Remove<span className="sr-only"> {line.product.name}</span>
          </Button>
        </div>
      </div>
    </li>
  )
}
