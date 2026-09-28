import { Link } from 'react-router'
import { ButtonLink } from '@/components/ui/Button'
import { ErrorState } from '@/components/ui/ErrorState'
import { PageSpinner } from '@/components/ui/PageSpinner'
import { useCollection } from '@/data/shopping'
import { cn } from '@/lib/cn'
import { AchievementsSection } from './AchievementsSection'
import { formatMoney } from '@/lib/money'
import { usePageTitle } from '@/lib/usePageTitle'

export default function CollectionPage() {
  usePageTitle('My collection')
  const collection = useCollection()
  if (collection.isPending) return <PageSpinner />
  if (collection.isError) return <ErrorState error={collection.error} />
  const { items, stats, achievements } = collection.data

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-[32px] font-bold leading-10">My collection</h1>
        <p className="text-sm text-ink-muted">
          Everything you’ve bought on TrustKart, valued at today’s prices.
        </p>
      </div>
      <section aria-label="Collection value" className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Stat
          label="Collection value"
          value={formatMoney(stats.collectionValue)}
          note="At today’s prices"
          strong
        />
        <Stat
          label="Products owned"
          value={String(stats.productsOwned)}
          note={`${String(stats.distinctProducts)} different products`}
        />
        <Stat
          label="Total spent"
          value={formatMoney(stats.totalVirtualSpend)}
          note={`${String(stats.purchases)} orders`}
        />
        <Stat
          label="Favorite category"
          value={stats.favoriteCategory ?? '—'}
          note={stats.mostExpensive ? `Priciest: ${formatMoney(stats.mostExpensive.amount)}` : 'Nothing yet'}
        />
      </section>

      {items.length === 0 ? (
        <div className="flex flex-col items-start gap-3 rounded-card border border-border bg-surface p-6">
          <p className="font-semibold">Your collection is empty.</p>
          <p className="text-sm text-ink-muted">Every product you buy shows up here.</p>
          <ButtonLink to="/" variant="secondary">
            Start your dream setup
          </ButtonLink>
        </div>
      ) : (
        <ul className="grid grid-cols-[repeat(auto-fill,minmax(min(220px,100%),1fr))] gap-3.5">
          {items.map((i) => (
            <li
              key={i.productId}
              className="flex flex-col gap-2 rounded-card border border-border bg-surface p-3"
            >
              <Link
                to={`/p/${i.slug}`}
                className="tk-img-well flex aspect-[4/3] items-center justify-center rounded-control bg-surface-2 p-3"
                tabIndex={-1}
                aria-hidden="true"
              >
                {i.imageUrl && (
                  <img
                    src={i.imageUrl}
                    alt=""
                    loading="lazy"
                    className="tk-product-img max-h-full max-w-full object-contain"
                  />
                )}
              </Link>
              <Link to={`/p/${i.slug}`} className="line-clamp-2 text-sm font-medium text-ink">
                {i.name}
              </Link>
              <p className="text-[13px] text-ink-muted">
                {i.categoryName} · owned ×{i.quantity}
              </p>
              <p className="text-sm font-semibold tabular">{formatMoney(i.currentValue)}</p>
            </li>
          ))}
        </ul>
      )}

      <AchievementsSection achievements={achievements} />
    </div>
  )
}

function Stat({
  label,
  value,
  note,
  strong,
}: {
  label: string
  value: string
  note: string
  strong?: boolean
}) {
  return (
    <div
      className={cn(
        'flex flex-col gap-1 rounded-tile border p-4',
        strong ? 'border-transparent bg-primary text-on-primary' : 'border-border bg-surface',
      )}
    >
      <span className={cn('text-sm', strong ? '' : 'text-ink-muted')}>{label}</span>
      <span className="truncate text-2xl font-bold tabular">{value}</span>
      <span className={cn('text-xs', strong ? '' : 'text-ink-muted')}>{note}</span>
    </div>
  )
}
