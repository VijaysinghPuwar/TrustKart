import { ChevronLeft, ChevronRight } from 'lucide-react'
import { cn } from '@/lib/cn'

export function Pagination({
  page,
  totalPages,
  onPage,
}: {
  page: number
  totalPages: number
  onPage: (page: number) => void
}) {
  if (totalPages <= 1) return null
  const pages = Array.from({ length: totalPages }, (_, i) => i).filter(
    (i) => i === 0 || i === totalPages - 1 || Math.abs(i - page) <= 1,
  )
  const base =
    'flex h-10 min-w-10 items-center justify-center rounded-control border px-3 text-sm font-semibold'
  const btn = cn(base, 'border-border-strong bg-surface text-ink hover:bg-surface-2 disabled:opacity-40')
  const current = cn(base, 'border-primary bg-primary text-on-primary')
  return (
    <nav aria-label="Pagination" className="flex flex-wrap items-center justify-center gap-1.5">
      <button
        type="button"
        className={btn}
        disabled={page === 0}
        onClick={() => onPage(page - 1)}
        aria-label="Previous page"
      >
        <ChevronLeft className="size-4" aria-hidden="true" />
      </button>
      {pages.map((p, idx) => (
        <span key={p} className="flex items-center gap-1.5">
          {idx > 0 && p - (pages[idx - 1] ?? p) > 1 && <span aria-hidden="true">…</span>}
          <button
            type="button"
            className={p === page ? current : btn}
            aria-current={p === page ? 'page' : undefined}
            onClick={() => onPage(p)}
          >
            {p + 1}
          </button>
        </span>
      ))}
      <button
        type="button"
        className={btn}
        disabled={page >= totalPages - 1}
        onClick={() => onPage(page + 1)}
        aria-label="Next page"
      >
        <ChevronRight className="size-4" aria-hidden="true" />
      </button>
    </nav>
  )
}
