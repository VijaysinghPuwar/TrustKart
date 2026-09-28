import { X } from 'lucide-react'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ProductImage } from '@/components/commerce/ProductImage'
import { useAddToCartAction } from '@/components/commerce/useAddToCart'
import { Button, ButtonLink } from '@/components/ui/Button'
import { ErrorState } from '@/components/ui/ErrorState'
import { PageSpinner } from '@/components/ui/PageSpinner'
import { useCompare } from '@/data/catalog'
import { cn } from '@/lib/cn'
import { usePageTitle } from '@/lib/usePageTitle'
import { useCompareTray } from '@/state/compare'

/** Side-by-side specs for 2 to 4 products, aligned by the server, with a "differences only" switch. */
export default function ComparePage() {
  usePageTitle('Compare products')
  const [params, setParams] = useSearchParams()
  const tray = useCompareTray()
  const slugs = params.getAll('slugs').slice(0, 4)
  const compare = useCompare(slugs)
  const [diffOnly, setDiffOnly] = useState(false)
  const { addToCart, justAdded } = useAddToCartAction()

  if (slugs.length < 2) {
    return (
      <div className="flex flex-col items-start gap-3">
        <h1 className="text-[32px] font-bold leading-10">Compare products</h1>
        <p className="text-ink-muted">
          Tick “Compare” on two to four products, then open the comparison from the tray at the bottom of the
          screen.
        </p>
        <ButtonLink to="/c/components" variant="secondary">
          Browse PC components
        </ButtonLink>
      </div>
    )
  }
  if (compare.isPending) return <PageSpinner />
  if (compare.isError) return <ErrorState error={compare.error} title="We couldn’t compare those products" />
  const { products, rows } = compare.data
  const visible = diffOnly ? rows.filter((r) => r.differs) : rows

  function drop(slug: string) {
    tray.remove(slug)
    const next = new URLSearchParams()
    slugs.filter((s) => s !== slug).forEach((s) => next.append('slugs', s))
    setParams(next)
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-[32px] font-bold leading-10">Compare products</h1>
        <label className="ml-auto flex cursor-pointer items-center gap-2 text-sm font-medium">
          <input
            type="checkbox"
            className="size-[18px] accent-primary"
            checked={diffOnly}
            onChange={(e) => setDiffOnly(e.target.checked)}
          />
          Show differences only
        </label>
      </div>
      <div
        role="region"
        aria-label="Comparison table"
        tabIndex={0}
        className="relative overflow-x-auto rounded-card border border-border bg-surface"
      >
        <table className="w-full min-w-[640px] table-fixed border-collapse text-sm">
          <caption className="sr-only">Specification comparison</caption>
          <thead>
            <tr>
              <td className="w-44 border-b border-border p-3" />
              {products.map((p) => (
                <th
                  key={p.slug}
                  scope="col"
                  className="border-b border-l border-border p-3 text-left align-top font-normal"
                >
                  <div className="flex flex-col gap-2">
                    <div className="relative">
                      <div className="tk-img-well flex aspect-square items-center justify-center rounded-control bg-surface-2 p-3">
                        <ProductImage image={p.image} sizes="200px" />
                      </div>
                      <button
                        type="button"
                        onClick={() => drop(p.slug)}
                        aria-label={`Remove ${p.name} from comparison`}
                        className="absolute right-1.5 top-1.5 flex size-8 items-center justify-center rounded-control border border-border bg-surface text-ink-muted"
                      >
                        <X className="size-4" aria-hidden="true" />
                      </button>
                    </div>
                    <Link to={`/p/${p.slug}`} className="line-clamp-2 font-semibold text-ink">
                      {p.name}
                    </Link>
                    {p.maxQuantity > 0 && (
                      <Button variant="accent" size="sm" onClick={() => addToCart(p)}>
                        {justAdded === p.id ? 'Added ✓' : 'Add to cart'}
                      </Button>
                    )}
                  </div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {visible.map((row) => (
              <tr key={row.key} className={cn(row.differs && 'bg-primary-subtle/40')}>
                <th
                  scope="row"
                  className="border-b border-border p-3 text-left align-top font-medium text-ink-muted"
                >
                  {row.label}
                  {row.differs && <span className="sr-only"> (differs)</span>}
                </th>
                {row.values.map((v, i) => (
                  <td key={i} className="border-b border-l border-border p-3 align-top tabular">
                    {v ?? <span className="text-ink-subtle">—</span>}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {diffOnly && visible.length === 0 && (
        <p className="text-sm text-ink-muted">These products match on every compared specification.</p>
      )}
    </div>
  )
}
