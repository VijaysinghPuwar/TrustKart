import { SlidersHorizontal, X } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { ProductCard, ProductCardSkeleton } from '@/components/commerce/ProductCard'
import { Button } from '@/components/ui/Button'
import { Dialog } from '@/components/ui/Dialog'
import { ErrorState } from '@/components/ui/ErrorState'
import { useCategories, useSearch } from '@/data/catalog'
import { usePageTitle } from '@/lib/usePageTitle'
import type { Category } from '@/lib/types'
import { FilterPanel } from './FilterPanel'
import { Pagination } from './Pagination'

const SORTS = [
  ['relevance', 'Best match'],
  ['featured', 'Featured'],
  ['price_asc', 'Price: low to high'],
  ['price_desc', 'Price: high to low'],
  ['discount', 'Biggest discount'],
  ['name', 'Name'],
] as const

const COLLECTION_TITLES: Record<string, string> = {
  'dream-gpus': 'Dream GPUs',
  'developer-setup': 'Developer setup',
  'homelab-starter': 'Homelab starter',
  'ai-lab': 'Build an AI lab',
  'enterprise-lab': 'Enterprise & servers',
  'security-lab': 'Cybersecurity lab',
  'dream-gaming': 'Dream gaming setup',
  'small-business': 'Small business',
  'servers-workstations': 'Servers & workstations',
}

interface SearchPageProps {
  /** Fixed parameters for curated routes such as /deals. */
  preset?: Record<string, string>
  title?: string
}

export default function SearchPage({ preset, title }: SearchPageProps) {
  const { category: routeCategory, collection } = useParams()
  const [params, setParams] = useSearchParams()
  const [filtersOpen, setFiltersOpen] = useState(false)
  const { data: categories } = useCategories()

  const apiParams = useMemo(() => {
    const p = new URLSearchParams(params)
    if (routeCategory) p.set('category', routeCategory)
    if (collection) p.set('collection', collection)
    Object.entries(preset ?? {}).forEach(([k, v]) => {
      if (!p.has(k)) p.set(k, v)
    })
    p.set('size', '24')
    return p
  }, [params, routeCategory, collection, preset])

  const qs = `?${apiParams.toString()}`
  const search = useSearch(qs)
  const q = params.get('q') ?? ''
  const category = routeCategory ?? params.get('category') ?? undefined
  const categoryNode = findCategory(categories, category)
  const heading = q
    ? `Results for “${q}”`
    : (title ??
      categoryNode?.name ??
      (collection ? COLLECTION_TITLES[collection] : undefined) ??
      'All products')
  usePageTitle(heading)

  function update(mutate: (next: URLSearchParams) => void) {
    const next = new URLSearchParams(params)
    mutate(next)
    next.delete('page')
    setParams(next)
  }

  const data = search.data
  const sort = params.get('sort') ?? (q ? 'relevance' : (preset?.sort ?? 'featured'))
  const filterCount = [...params.keys()].filter(
    (k) => !['q', 'sort', 'page', 'ignore', 'category'].includes(k),
  ).length

  const filters = (
    <FilterPanel key={category ?? 'all'} category={category} params={params} onChange={update} />
  )

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-3">
        <h1 className="text-[28px] font-bold leading-9 tracking-[-0.01em]">{heading}</h1>
        {categoryNode && categoryNode.children.length > 0 && (
          <ul className="flex flex-wrap gap-2" aria-label="Subcategories">
            {categoryNode.children.map((c) => (
              <li key={c.slug}>
                <Link
                  to={`/c/${c.slug}`}
                  className="flex h-9 items-center rounded-chip border border-border bg-surface px-3 text-sm text-ink hover:border-primary hover:no-underline"
                >
                  {c.name}
                </Link>
              </li>
            ))}
          </ul>
        )}
        {data && data.interpretation.length > 0 && (
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-[13px] font-semibold text-ink-muted">Understood as</span>
            {data.interpretation.map((chip) => (
              <span
                key={chip.ignoreKey}
                className="flex h-[30px] items-center gap-1 rounded-chip bg-primary-subtle pl-3 pr-1 text-[13px] font-semibold text-primary-hover"
              >
                <span className="sr-only">{chip.label}: </span>
                {chip.value}
                <button
                  type="button"
                  aria-label={`Remove ${chip.label.toLowerCase()} ${chip.value}`}
                  onClick={() => update((p) => p.append('ignore', chip.ignoreKey))}
                  className="flex size-6 items-center justify-center rounded hover:bg-[color-mix(in_srgb,var(--tk-primary)_18%,transparent)]"
                >
                  <X className="size-3.5" aria-hidden="true" />
                </button>
              </span>
            ))}
          </div>
        )}
        {data?.relaxed && (
          <p className="text-sm text-ink-muted">
            No product matched every word, so these results match some of them.
          </p>
        )}
      </div>

      <div className="flex gap-7">
        <aside aria-label="Filters" className="hidden w-[232px] shrink-0 lg:block">
          {filters}
        </aside>
        <div className="flex min-w-0 flex-1 flex-col gap-4">
          <div className="flex flex-wrap items-center gap-3">
            <p aria-live="polite" className="text-sm text-ink-muted">
              {data
                ? `${data.results.totalItems.toLocaleString()} ${data.results.totalItems === 1 ? 'result' : 'results'}`
                : ' '}
            </p>
            <Button variant="secondary" size="sm" className="lg:hidden" onClick={() => setFiltersOpen(true)}>
              <SlidersHorizontal className="size-4" aria-hidden="true" />
              Filters{filterCount > 0 ? ` (${String(filterCount)})` : ''}
            </Button>
            <label className="ml-auto flex items-center gap-2 text-sm">
              <span className="text-ink-muted">Sort</span>
              <select
                value={sort}
                onChange={(e) => update((p) => p.set('sort', e.target.value))}
                className="h-10 rounded-control border border-border-strong bg-surface px-2.5 text-sm text-ink"
              >
                {SORTS.filter(([v]) => v !== 'relevance' || q).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {search.isError ? (
            <ErrorState
              error={search.error}
              title="Search didn’t work"
              onRetry={() => void search.refetch()}
            />
          ) : !data ? (
            <ul className="grid grid-cols-[repeat(auto-fill,minmax(min(210px,calc(50%-5px)),1fr))] gap-2.5 sm:gap-3.5">
              {Array.from({ length: 8 }, (_, i) => (
                <li key={i}>
                  <ProductCardSkeleton />
                </li>
              ))}
            </ul>
          ) : data.results.items.length === 0 ? (
            <div className="rounded-card border border-border bg-surface px-6 py-14 text-center">
              <p className="font-semibold">No matches.</p>
              <p className="mt-1 text-sm text-ink-muted">
                Try fewer words, remove a filter, or remove one of the “Understood as” chips.
              </p>
            </div>
          ) : (
            <ul
              className="grid grid-cols-[repeat(auto-fill,minmax(min(210px,calc(50%-5px)),1fr))] gap-2.5 transition-opacity sm:gap-3.5"
              style={{ opacity: search.isPlaceholderData ? 0.6 : 1 }}
            >
              {data.results.items.map((p, i) => (
                <li key={p.id}>
                  <ProductCard product={p} priority={i < 4} />
                </li>
              ))}
            </ul>
          )}
          {data && (
            <Pagination
              page={data.results.page}
              totalPages={data.results.totalPages}
              onPage={(page) => {
                const next = new URLSearchParams(params)
                next.set('page', String(page))
                setParams(next)
                window.scrollTo({ top: 0 })
              }}
            />
          )}
        </div>
      </div>

      <Dialog
        open={filtersOpen}
        onClose={() => setFiltersOpen(false)}
        variant="sheet"
        title="Filters"
        footer={
          <Button className="w-full" onClick={() => setFiltersOpen(false)}>
            Show {data?.results.totalItems ?? ''} results
          </Button>
        }
      >
        {filters}
      </Dialog>
    </div>
  )
}

function findCategory(tree: Category[] | undefined, slug: string | undefined): Category | undefined {
  if (!tree || !slug) return undefined
  for (const c of tree) {
    if (c.slug === slug) return c
    const child = findCategory(c.children, slug)
    if (child) return child
  }
  return undefined
}
