import { Link } from 'react-router'
import { Button, ButtonLink } from '@/components/ui/Button'
import { Price } from '@/components/ui/Price'
import { ProductImage } from '@/components/commerce/ProductImage'
import { useAddToCartAction } from '@/components/commerce/useAddToCart'
import { Skeleton } from '@/components/ui/Skeleton'
import type { ProductCard } from '@/lib/types'

/**
 * The design's blue "Deal of the day" card, carrying TrustKart's identity line and today's pick.
 * The pick is a real discounted product that rotates daily; no "lowest price in 30 days" claim is made
 * because there's no price history behind it.
 */
export function HeroFeature({ product }: { product: ProductCard | null | undefined }) {
  const { addToCart, justAdded, pendingId } = useAddToCartAction()
  return (
    <div className="col-span-2 flex min-h-[340px] flex-col gap-3.5 rounded-tile bg-primary p-6 text-on-primary sm:col-span-1 sm:row-span-2">
      <span className="text-[13px] font-semibold">Virtual technology store</span>
      <h1 className="text-balance text-[clamp(26px,2.6vw,34px)] font-bold leading-[1.15] tracking-[-0.02em]">
        Shop everything. Spend nothing.
      </h1>
      <p className="text-[15px] leading-[22px]">
        Build your dream setup with virtual funds. Real checkout, no real money.
      </p>
      {product === undefined ? (
        <Skeleton className="min-h-[150px] flex-1 rounded-button bg-white/30" />
      ) : product ? (
        <>
          <Link
            to={`/p/${product.slug}`}
            className="tk-img-well flex min-h-[150px] flex-1 items-center justify-center rounded-button bg-surface p-4"
            aria-label={`Today’s pick: ${product.name}`}
          >
            <ProductImage image={product.image} priority sizes="(max-width: 640px) 90vw, 300px" />
          </Link>
          <div>
            <p className="text-[13px] font-semibold">Today’s pick</p>
            <p className="line-clamp-2 font-semibold">{product.name}</p>
            <Price
              amount={product.price}
              compareAt={product.compareAtPrice}
              size="lg"
              className="[&_.line-through]:text-current"
            />
          </div>
          <div className="flex flex-wrap gap-2">
            <Button
              variant="accent"
              size="lg"
              className="h-11"
              loading={pendingId === product.id}
              onClick={() => addToCart(product)}
            >
              {justAdded === product.id ? 'Added ✓' : 'Add to cart'}
            </Button>
            <ButtonLink variant="inverse" size="lg" className="h-11" to={`/p/${product.slug}`}>
              View product
            </ButtonLink>
          </div>
        </>
      ) : null}
    </div>
  )
}
