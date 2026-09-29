import { Link } from 'react-router'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Price } from '@/components/ui/Price'
import { Skeleton } from '@/components/ui/Skeleton'
import { useToast } from '@/components/ui/Toast'
import { MAX_COMPARE, useCompareTray } from '@/state/compare'
import type { ProductCard as Card } from '@/lib/types'
import { ProductImage } from './ProductImage'
import { StockLine } from './StockLine'
import { WishlistButton } from './WishlistButton'
import { useAddToCartAction } from './useAddToCart'

interface ProductCardProps {
  product: Card
  /** First row of a page: load the image eagerly. */
  priority?: boolean
}

/**
 * The approved card: 1:1 image well, up to two badges, wishlist heart, two-line title, price with compare-at,
 * stock line, Add to cart and a Compare checkbox. The ratings row from the design is omitted until reviews
 * exist, so no card ever shows a rating nobody gave.
 */
export function ProductCard({ product, priority }: ProductCardProps) {
  const { addToCart, justAdded, pendingId } = useAddToCartAction()
  const compare = useCompareTray()
  const { notify } = useToast()
  const href = `/p/${product.slug}`
  const available = product.maxQuantity > 0
  const compared = compare.has(product.slug)

  return (
    <article className="relative flex h-full flex-col overflow-hidden rounded-card border border-border bg-surface transition-shadow duration-100 ease-tk hover:shadow-md">
      <div className="relative aspect-square bg-surface-2">
        <Link
          to={href}
          tabIndex={-1}
          aria-hidden="true"
          className="tk-img-well absolute inset-0 flex items-center justify-center p-4"
        >
          <ProductImage image={product.image} priority={priority} />
        </Link>
        <div className="pointer-events-none absolute left-2.5 right-[52px] top-2.5 flex flex-wrap gap-1.5">
          {product.percentOff > 0 && <Badge tone="deal">Save {product.percentOff}%</Badge>}
        </div>
        <WishlistButton productId={product.id} name={product.name} className="absolute right-2 top-2" />
        {!available && (
          <span className="absolute bottom-2.5 left-2.5">
            <Badge tone="neutral">
              {product.stockStatus === 'DISCONTINUED' ? 'Discontinued' : 'Out of stock'}
            </Badge>
          </span>
        )}
      </div>
      <div className="flex flex-1 flex-col gap-[5px] px-3.5 pt-3">
        <h3 className="line-clamp-2 min-h-10 text-sm font-medium leading-5">
          <Link to={href} className="text-ink hover:text-ink hover:underline">
            {product.name}
          </Link>
        </h3>
        <div className="text-[13px] text-ink-muted">{product.brand.name}</div>
        <Price amount={product.price} compareAt={product.compareAtPrice} />
        <StockLine product={product} />
      </div>
      <div className="flex flex-col gap-2.5 px-3.5 py-3">
        {available ? (
          <Button
            variant="accent"
            className="w-full rounded-control"
            loading={pendingId === product.id}
            onClick={() => addToCart(product)}
          >
            {justAdded === product.id ? 'Added ✓' : 'Add to cart'}
            {/* The visible words come first in the accessible name (WCAG 2.5.3), then which product. */}
            <span className="sr-only">: {product.name}</span>
          </Button>
        ) : (
          <Button variant="secondary" className="w-full rounded-control" disabled>
            Unavailable
          </Button>
        )}
        <label className="-my-1.5 flex min-h-10 cursor-pointer items-center gap-2 text-[13px] text-ink-muted">
          <input
            type="checkbox"
            className="size-[18px] cursor-pointer accent-primary"
            checked={compared}
            onChange={() => {
              if (!compare.toggle({ slug: product.slug, name: product.name })) {
                notify(`You can compare up to ${String(MAX_COMPARE)} products`)
              }
            }}
          />
          Compare
        </label>
      </div>
    </article>
  )
}

export function ProductCardSkeleton() {
  return (
    <div
      aria-hidden="true"
      className="flex h-full flex-col overflow-hidden rounded-card border border-border bg-surface"
    >
      <Skeleton className="aspect-square rounded-none" />
      <div className="flex flex-col gap-2.5 p-3.5">
        <Skeleton className="h-3 w-[92%]" />
        <Skeleton className="h-3 w-[64%]" />
        <Skeleton className="h-2.5 w-[40%]" />
        <Skeleton className="h-[18px] w-[36%]" />
        <Skeleton className="mt-1.5 h-10 rounded-control" />
      </div>
    </div>
  )
}
