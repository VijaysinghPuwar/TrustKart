import { useState } from 'react'
import { Link } from 'react-router'
import { Badge } from '@/components/ui/Badge'
import { ButtonLink } from '@/components/ui/Button'
import { ErrorState } from '@/components/ui/ErrorState'
import { PageSpinner } from '@/components/ui/PageSpinner'
import { usePurchases } from '@/data/shopping'
import { formatMoney } from '@/lib/money'
import { usePageTitle } from '@/lib/usePageTitle'
import { Pagination } from '@/features/search/Pagination'
import { deliveryHeadline } from './TrackingPanel'

export default function PurchasesPage() {
  usePageTitle('Your orders')
  const [page, setPage] = useState(0)
  const purchases = usePurchases(page)
  if (purchases.isPending) return <PageSpinner />
  if (purchases.isError) return <ErrorState error={purchases.error} />
  const { items, totalPages } = purchases.data
  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="text-[28px] font-bold leading-9">Your orders</h1>
        <p className="text-sm text-ink-muted">Orders paid with your TrustKart Wallet.</p>
      </div>
      {items.length === 0 ? (
        <div className="flex flex-col items-start gap-3 rounded-card border border-border bg-surface p-6">
          <p className="font-semibold">No orders yet.</p>
          <ButtonLink to="/" variant="secondary">
            Find something you’d love
          </ButtonLink>
        </div>
      ) : (
        <ul className="flex flex-col gap-3">
          {items.map((p) => (
            <li key={p.id}>
              <Link
                to={`/account/purchases/${p.id}`}
                className="flex flex-wrap items-center gap-4 rounded-card border border-border bg-surface p-4 text-ink no-underline hover:border-primary hover:text-ink hover:no-underline"
              >
                <div className="flex -space-x-3">
                  {p.thumbnails.map((t) => (
                    <img
                      key={t}
                      src={t}
                      alt=""
                      width={48}
                      height={48}
                      loading="lazy"
                      decoding="async"
                      className="size-12 rounded-control border-2 border-surface bg-surface-2 object-contain p-0.5"
                    />
                  ))}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="font-mono text-sm font-semibold">{p.orderNumber}</p>
                  <p className="text-[13px] text-ink-muted">
                    {new Date(p.createdAt).toLocaleDateString('en-US', {
                      month: 'short',
                      day: 'numeric',
                      year: 'numeric',
                    })}{' '}
                    · {p.itemCount} {p.itemCount === 1 ? 'item' : 'items'}
                  </p>
                </div>
                <span className="font-bold tabular">{formatMoney(p.total)}</span>
                <Badge tone={p.status === 'COMPLETED' ? 'trust' : 'neutral'}>
                  {deliveryHeadline({ stage: p.stage, estimatedDelivery: p.estimatedDelivery })}
                </Badge>
              </Link>
            </li>
          ))}
        </ul>
      )}
      <Pagination page={page} totalPages={totalPages} onPage={setPage} />
    </div>
  )
}
