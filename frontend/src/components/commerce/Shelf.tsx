import type { ReactNode } from 'react'
import { Link } from 'react-router'
import type { ProductCard as Card } from '@/lib/types'
import { ProductCard, ProductCardSkeleton } from './ProductCard'

interface ShelfProps {
  title: string
  subtitle?: string
  action?: { label: string; to?: string; onClick?: () => void }
  layout: 'row' | 'grid'
  items?: Card[]
  loading?: boolean
  empty?: ReactNode
  id?: string
}

/**
 * A titled product section. Rows scroll inside their own container (never the page body), grids reflow.
 * Column widths come from the approved design: 220 px (172 px on phones) for rows, min 210 px for grids.
 */
export function Shelf({ title, subtitle, action, layout, items, loading, empty, id }: ShelfProps) {
  const headingId = id ? `${id}-heading` : undefined
  const list = loading ? Array.from({ length: layout === 'row' ? 6 : 10 }, () => null) : (items ?? [])
  return (
    <section id={id} aria-labelledby={headingId} className="flex scroll-mt-40 flex-col gap-3.5">
      <div className="flex flex-wrap items-baseline gap-x-3.5 gap-y-1">
        <h2 id={headingId} className="text-[22px] font-bold leading-[30px] tracking-[-0.01em]">
          {title}
        </h2>
        {subtitle && <span className="text-[13px] text-ink-muted">{subtitle}</span>}
        {action &&
          (action.to ? (
            <Link to={action.to} className="ml-auto py-1.5 text-sm font-semibold">
              {action.label}
            </Link>
          ) : (
            <button
              type="button"
              onClick={action.onClick}
              className="ml-auto py-1.5 text-sm font-semibold text-primary hover:underline"
            >
              {action.label}
            </button>
          ))}
      </div>
      {!loading && list.length === 0 && empty ? (
        <div className="rounded-card border border-dashed border-border-strong p-5 text-sm text-ink-muted">
          {empty}
        </div>
      ) : layout === 'row' ? (
        <ul className="no-scrollbar relative grid snap-x snap-proximity auto-cols-[172px] grid-flow-col gap-3.5 overflow-x-auto pb-1.5 sm:auto-cols-[220px]">
          {list.map((p, i) => (
            <li key={p?.id ?? i} className="snap-start">
              {p ? <ProductCard product={p} /> : <ProductCardSkeleton />}
            </li>
          ))}
        </ul>
      ) : (
        <ul className="grid grid-cols-[repeat(auto-fill,minmax(min(210px,calc(50%-5px)),1fr))] gap-2.5 sm:gap-3.5">
          {list.map((p, i) => (
            <li key={p?.id ?? i}>
              {p ? <ProductCard product={p} priority={i < 4} /> : <ProductCardSkeleton />}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
