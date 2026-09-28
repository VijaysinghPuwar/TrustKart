import { ShoppingCart } from 'lucide-react'
import { Link, useNavigate } from 'react-router'
import { Button, ButtonLink } from '@/components/ui/Button'
import { Dialog } from '@/components/ui/Dialog'
import { QuantityStepper } from '@/components/ui/QuantityStepper'
import { useCart, useRemoveCartLine, useUpdateCartLine, useWallet } from '@/data/shopping'
import { formatMoney, subtractMoney } from '@/lib/money'
import { useCartDrawer } from '@/state/cartDrawer'
import { ProductImage } from './ProductImage'
import { cartIssueText } from './cartIssue'

export function CartDrawer() {
  const { open, hide } = useCartDrawer()
  const { data: cart } = useCart()
  const { data: wallet } = useWallet()
  const update = useUpdateCartLine()
  const remove = useRemoveCartLine()
  const navigate = useNavigate()
  const lines = cart?.items ?? []
  const subtotal = cart?.subtotal ?? '0.00'
  const unlimited = wallet?.mode === 'UNLIMITED'
  const remaining = wallet ? subtractMoney(wallet.balance, subtotal) : null
  const short = remaining?.startsWith('-') ?? false

  return (
    <Dialog
      open={open}
      onClose={hide}
      variant="drawer"
      title="Cart"
      description={lines.length ? `${String(cart?.itemCount ?? 0)} items` : undefined}
      footer={
        lines.length > 0 && (
          <div className="flex flex-col gap-3.5">
            <dl className="flex flex-col gap-1.5 text-sm tabular">
              <div className="flex text-base font-bold">
                <dt>Subtotal</dt>
                <dd className="ml-auto">{formatMoney(subtotal)}</dd>
              </div>
              {wallet && (
                <>
                  <div className="flex text-ink-muted">
                    <dt>Wallet balance</dt>
                    <dd className="ml-auto">{unlimited ? '∞ Unlimited' : formatMoney(wallet.balance)}</dd>
                  </div>
                  {!unlimited && remaining !== null && (
                    <div className="flex text-ink-muted">
                      <dt>{short ? 'Wallet short by' : 'Remaining after purchase'}</dt>
                      <dd className={short ? 'ml-auto font-semibold text-danger' : 'ml-auto'}>
                        {formatMoney(remaining.replace('-', ''))}
                      </dd>
                    </div>
                  )}
                </>
              )}
            </dl>
            <Button
              size="lg"
              onClick={() => {
                hide()
                void navigate('/checkout')
              }}
            >
              Proceed to checkout
            </Button>
            <ButtonLink to="/cart" variant="secondary" onClick={hide}>
              View cart
            </ButtonLink>
            <p className="text-center text-[13px] text-ink-muted">Free delivery. Taxes included.</p>
          </div>
        )
      }
    >
      {lines.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-12 text-center">
          <ShoppingCart className="size-10 text-ink-subtle" aria-hidden="true" />
          <p className="text-sm text-ink-muted">
            Your cart is empty. Search for anything you'd love to own and add it here.
          </p>
          <ButtonLink to="/search" variant="secondary" onClick={hide}>
            Start browsing
          </ButtonLink>
        </div>
      ) : (
        <ul className="-my-4 divide-y divide-border">
          {lines.map((line) => (
            <li key={line.id} className="flex gap-3.5 py-4">
              <Link
                to={`/p/${line.product.slug}`}
                onClick={hide}
                className="tk-img-well flex size-16 shrink-0 items-center justify-center rounded-control bg-surface-2 p-1.5"
                tabIndex={-1}
                aria-hidden="true"
              >
                <ProductImage image={line.product.image} sizes="64px" />
              </Link>
              <div className="flex min-w-0 flex-1 flex-col gap-2">
                <Link
                  to={`/p/${line.product.slug}`}
                  onClick={hide}
                  className="line-clamp-2 text-sm leading-5 text-ink"
                >
                  {line.product.name}
                </Link>
                {line.issue && <p className="text-[13px] text-danger">{cartIssueText(line.issue)}</p>}
                <div className="flex flex-wrap items-center gap-3">
                  <QuantityStepper
                    size="sm"
                    value={line.quantity}
                    max={Math.max(line.product.maxQuantity, line.quantity)}
                    label={`Quantity of ${line.product.name}`}
                    disabled={update.isPending}
                    onChange={(quantity) => update.mutate({ id: line.id, quantity })}
                  />
                  <button
                    type="button"
                    className="text-[13px] text-primary hover:underline"
                    onClick={() => remove.mutate(line.id)}
                  >
                    Remove<span className="sr-only"> {line.product.name}</span>
                  </button>
                  <span className="ml-auto text-[15px] font-bold tabular">{formatMoney(line.lineTotal)}</span>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </Dialog>
  )
}
