import { Scale, ShieldCheck } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { ProductImage } from '@/components/commerce/ProductImage'
import { Shelf } from '@/components/commerce/Shelf'
import { StockLine } from '@/components/commerce/StockLine'
import { useAddToCartAction } from '@/components/commerce/useAddToCart'
import { WishlistButton } from '@/components/commerce/WishlistButton'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ErrorState } from '@/components/ui/ErrorState'
import { PageSpinner } from '@/components/ui/PageSpinner'
import { Price } from '@/components/ui/Price'
import { QuantityStepper } from '@/components/ui/QuantityStepper'
import { useToast } from '@/components/ui/Toast'
import { useProduct } from '@/data/catalog'
import { ApiError } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'
import { MAX_COMPARE, useCompareTray } from '@/state/compare'
import { recordView } from '@/state/recentlyViewed'
import { NotFoundPage } from '@/features/errors/NotFoundPage'
import { InstantBuyDialog } from './InstantBuyDialog'
import { SpecTable } from './SpecTable'

const MATCH_NOTE = {
  EXACT: null,
  PRODUCT_LINE: 'Image shows a similar model from the same product line.',
  REPRESENTATIVE: 'Representative image of this kind of product.',
} as const

export default function ProductPage() {
  const { slug = '' } = useParams()
  const product = useProduct(slug)
  const [quantity, setQuantity] = useState(1)
  const [quantityFor, setQuantityFor] = useState(slug)
  if (quantityFor !== slug) {
    setQuantityFor(slug)
    setQuantity(1)
  }
  const [instantOpen, setInstantOpen] = useState(false)
  const { addToCart, justAdded, pendingId } = useAddToCartAction()
  const compare = useCompareTray()
  const { notify } = useToast()
  usePageTitle(product.data?.product.name)
  const viewedId = product.data?.product.id

  useEffect(() => {
    if (viewedId !== undefined) recordView(viewedId)
  }, [viewedId])

  if (product.isPending) return <PageSpinner />
  if (product.isError) {
    if (product.error instanceof ApiError && product.error.status === 404) return <NotFoundPage />
    return <ErrorState error={product.error} onRetry={() => void product.refetch()} />
  }

  const detail = product.data
  const p = detail.product
  const image = detail.images[0]
  const available = p.maxQuantity > 0
  const note = image ? MATCH_NOTE[image.match] : null

  return (
    <div className="flex flex-col gap-10 pb-20 md:pb-0">
      <nav aria-label="Breadcrumb">
        <ol className="flex flex-wrap items-center gap-1.5 text-[13px] text-ink-muted">
          <li>
            <Link to="/">Home</Link>
          </li>
          {detail.breadcrumbs.map((b) => (
            <li key={b.slug} className="flex items-center gap-1.5">
              <span aria-hidden="true">/</span>
              <Link to={`/c/${b.slug}`}>{b.name}</Link>
            </li>
          ))}
        </ol>
      </nav>

      <div className="grid items-start gap-8 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:gap-12">
        <figure className="flex flex-col gap-2 md:sticky md:top-40">
          <div className="tk-img-well flex aspect-square items-center justify-center rounded-card border border-border bg-surface-2 p-6">
            <ProductImage
              image={image}
              priority
              sizes="(max-width: 768px) 100vw, 600px"
              className="max-h-full"
            />
          </div>
          {image && (
            <figcaption className="text-xs text-ink-muted">
              {note && <span className="block">{note}</span>}
              Photo: {image.credit} via{' '}
              <a href={image.sourceUrl} target="_blank" rel="noopener noreferrer">
                Wikimedia Commons
              </a>
            </figcaption>
          )}
        </figure>

        <div className="flex flex-col gap-5">
          <div className="flex flex-col gap-2">
            <Link to={`/search?brand=${p.brand.slug}`} className="text-sm font-semibold">
              {p.brand.name}
            </Link>
            <h1 className="text-pretty text-[30px] font-bold leading-[38px] tracking-[-0.01em]">{p.name}</h1>
            <p className="text-ink-muted">{p.summary}</p>
            <p className="font-mono text-xs text-ink-muted">SKU {p.sku}</p>
          </div>
          <div className="flex flex-col gap-1.5">
            <div className="flex flex-wrap items-center gap-3">
              <Price amount={p.price} compareAt={p.compareAtPrice} size="xl" />
              {p.percentOff > 0 && <Badge tone="deal">Save {p.percentOff}%</Badge>}
            </div>
            <StockLine product={p} />
          </div>

          {available ? (
            <div className="flex flex-col gap-3">
              <div className="flex items-center gap-4">
                <QuantityStepper
                  value={quantity}
                  max={p.maxQuantity}
                  onChange={setQuantity}
                  label="Quantity"
                />
                <span className="text-sm text-ink-muted">Up to {p.maxQuantity}</span>
              </div>
              <div className="flex flex-wrap gap-3">
                <Button
                  variant="accent"
                  size="lg"
                  className="flex-1"
                  loading={pendingId === p.id}
                  onClick={() => addToCart(p, quantity)}
                >
                  {justAdded === p.id ? 'Added ✓' : 'Add to cart'}
                </Button>
                <Button size="lg" className="flex-1" onClick={() => setInstantOpen(true)}>
                  Buy now
                </Button>
              </div>
            </div>
          ) : (
            <p className="rounded-control bg-surface-2 p-3 text-sm">
              This product can’t be bought right now.
            </p>
          )}

          <div className="flex flex-wrap gap-2">
            <WishlistButton productId={p.id} name={p.name} />
            <Button
              variant="secondary"
              size="sm"
              className="h-9"
              aria-pressed={compare.has(p.slug)}
              onClick={() => {
                if (!compare.toggle({ slug: p.slug, name: p.name }))
                  notify(`You can compare up to ${String(MAX_COMPARE)} products`)
              }}
            >
              <Scale className="size-4" aria-hidden="true" />
              {compare.has(p.slug) ? 'In compare' : 'Compare'}
            </Button>
          </div>

          <section
            aria-label="How this purchase works"
            className="flex flex-col gap-2 rounded-card border border-border bg-surface p-5 text-sm"
          >
            <p className="flex items-center gap-2 font-semibold text-trust">
              <ShieldCheck className="size-4" aria-hidden="true" />
              Secure checkout
            </p>
            <p>Pay with your TrustKart Wallet. Free delivery on every order.</p>
            <p className="text-ink-muted">
              Changed your mind? Cancel any order from your order page and the full amount returns to your
              wallet.
            </p>
            <p className="text-ink-muted">
              Manufacturer warranty (for reference):{' '}
              {detail.warrantyMonths ? `${String(detail.warrantyMonths)} months` : 'none'}.
            </p>
          </section>
        </div>
      </div>

      <section
        aria-labelledby="about-heading"
        className="grid gap-8 lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]"
      >
        <div className="flex flex-col gap-3">
          <h2 id="about-heading" className="text-[22px] font-bold">
            About this product
          </h2>
          <p className="leading-7 text-ink">{detail.description}</p>
        </div>
        <SpecTable groups={detail.specGroups} />
      </section>

      {detail.related.length > 0 && (
        <Shelf
          id="related"
          layout="row"
          title="Similar products"
          subtitle="Same category, close in price"
          items={detail.related}
        />
      )}

      {available && (
        <div className="fixed inset-x-0 bottom-0 z-20 flex items-center gap-3 border-t border-border bg-surface px-4 py-3 shadow-lg md:hidden">
          <Price amount={p.price} className="flex-1" />
          <Button variant="accent" loading={pendingId === p.id} onClick={() => addToCart(p, quantity)}>
            {justAdded === p.id ? 'Added ✓' : 'Add to cart'}
          </Button>
        </div>
      )}
      <InstantBuyDialog
        product={p}
        quantity={quantity}
        open={instantOpen}
        onClose={() => setInstantOpen(false)}
      />
    </div>
  )
}
