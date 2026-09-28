import { Search, X } from 'lucide-react'
import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { useSuggestions } from '@/data/catalog'
import { cn } from '@/lib/cn'
import { formatMoney } from '@/lib/money'
import { ProductImage } from '@/components/commerce/ProductImage'
import type { ProductCard } from '@/lib/types'
import { addRecentSearch, getRecentSearches, removeRecentSearch } from './recentSearches'
import { useDebounced } from './useDebounced'

const ROTATING = [
  'Quiet mechanical keyboard for programming under $100',
  'GPU with at least 24 GB for local AI',
  'Homelab switch with 10G SFP+',
  'Rack server with ECC memory',
]

type Row =
  | { kind: 'label'; text: string }
  | { kind: 'query'; text: string; sub?: string; to: string; removable?: boolean }
  | { kind: 'product'; product: ProductCard; to: string }

/**
 * The signature search box: a WAI-ARIA combobox with a suggestion listbox, a Smart/Exact mode switch and a
 * rotating example placeholder. Smart (semantic) search is shown but disabled until it is enabled on the
 * server; exact search with rule-based interpretation always works.
 */
export function SmartSearch() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const urlQuery = params.get('q') ?? ''
  const [query, setQuery] = useState(urlQuery)
  const [syncedQuery, setSyncedQuery] = useState(urlQuery)
  // Keep the box in sync when navigating between searches (adjusting state during render, not in an effect).
  if (urlQuery !== syncedQuery) {
    setSyncedQuery(urlQuery)
    setQuery(urlQuery)
  }
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const [recent, setRecent] = useState<string[]>(getRecentSearches)
  const [placeholderIndex, setPlaceholderIndex] = useState(0)
  const [focused, setFocused] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const listId = useId()
  const optionId = (i: number) => `${listId}-opt-${String(i)}`

  const debounced = useDebounced(query.trim(), 150)
  const { data: suggestions } = useSuggestions(debounced)

  // Rotate example queries while idle, unless the user prefers reduced motion.
  useEffect(() => {
    if (focused || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
    const t = window.setInterval(() => setPlaceholderIndex((i) => (i + 1) % ROTATING.length), 3500)
    return () => window.clearInterval(t)
  }, [focused])

  // "/" focuses search from anywhere that isn't already a text field.
  useEffect(() => {
    const onKey = (e: globalThis.KeyboardEvent) => {
      const target = e.target as HTMLElement
      if (
        e.key === '/' &&
        !['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName) &&
        !target.isContentEditable
      ) {
        e.preventDefault()
        inputRef.current?.focus()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  const rows = useMemo<Row[]>(() => {
    const q = query.trim()
    const out: Row[] = []
    if (!q) {
      if (recent.length) {
        out.push({ kind: 'label', text: 'Recent searches' })
        recent.forEach((r) => out.push({ kind: 'query', text: r, to: searchUrl(r), removable: true }))
      }
      out.push({ kind: 'label', text: 'Try describing it' })
      ;(suggestions?.queries.length ? suggestions.queries : ROTATING.slice(0, 3)).forEach((t) =>
        out.push({ kind: 'query', text: t, to: searchUrl(t) }),
      )
      return out
    }
    if (suggestions && debounced === q) {
      if (suggestions.queries.length) {
        out.push({ kind: 'label', text: 'Suggestions' })
        suggestions.queries.forEach((t) => out.push({ kind: 'query', text: t, to: searchUrl(t) }))
      }
      if (suggestions.categories.length) {
        out.push({ kind: 'label', text: 'Categories' })
        suggestions.categories.forEach((c) =>
          out.push({
            kind: 'query',
            text: q,
            sub: ` in ${c.name}`,
            to: `/search?q=${encodeURIComponent(q)}&category=${c.slug}`,
          }),
        )
      }
      if (suggestions.products.length) {
        out.push({ kind: 'label', text: 'Products' })
        suggestions.products.forEach((p) => out.push({ kind: 'product', product: p, to: `/p/${p.slug}` }))
      }
    }
    out.push({ kind: 'query', text: `Search for “${q}”`, to: searchUrl(q) })
    return out
  }, [query, debounced, suggestions, recent])

  const selectable = rows.map((r, i) => (r.kind === 'label' ? -1 : i)).filter((i) => i >= 0)
  const chips = query.trim() && suggestions && debounced === query.trim() ? suggestions.interpretation : []

  function go(to: string, text?: string) {
    if (text) addRecentSearch(text)
    setRecent(getRecentSearches())
    setOpen(false)
    setActive(-1)
    inputRef.current?.blur()
    void navigate(to)
  }

  function submit() {
    const q = query.trim()
    if (!q) return
    go(searchUrl(q), q)
  }

  function pick(row: Row) {
    if (row.kind === 'label') return
    if (row.kind === 'product') go(row.to)
    else go(row.to, row.text.startsWith('Search for') ? query.trim() : row.text)
  }

  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault()
      setOpen(true)
      if (!selectable.length) return
      const pos = selectable.indexOf(active)
      const next =
        e.key === 'ArrowDown' ? (pos + 1) % selectable.length : pos <= 0 ? selectable.length - 1 : pos - 1
      setActive(selectable[next] ?? -1)
    } else if (e.key === 'Enter') {
      e.preventDefault()
      const row = active >= 0 ? rows[active] : undefined
      if (row && open) pick(row)
      else submit()
    } else if (e.key === 'Escape') {
      setOpen(false)
      setActive(-1)
    }
  }

  const expanded = open && rows.length > 0

  return (
    <div className="relative min-w-0">
      <form
        role="search"
        onSubmit={(e) => {
          e.preventDefault()
          submit()
        }}
        className={cn(
          'flex h-[46px] items-center gap-2 rounded-control bg-white pl-3.5 pr-1 transition-shadow duration-100',
          focused && 'shadow-[0_0_0_3px_var(--color-accent)]',
        )}
      >
        <Search className="hidden size-[18px] shrink-0 text-[#64748B] sm:block" aria-hidden="true" />
        <input
          ref={inputRef}
          role="combobox"
          aria-label="Describe what you're looking for"
          aria-autocomplete="list"
          aria-expanded={expanded}
          aria-controls={listId}
          aria-activedescendant={active >= 0 ? optionId(active) : undefined}
          value={query}
          onChange={(e) => {
            setQuery(e.target.value)
            setOpen(true)
            setActive(-1)
          }}
          onFocus={() => {
            setFocused(true)
            setOpen(true)
          }}
          onBlur={() => {
            setFocused(false)
            window.setTimeout(() => setOpen(false), 120)
          }}
          onKeyDown={onKeyDown}
          placeholder={`${ROTATING[placeholderIndex] ?? ''}…`}
          className="min-w-0 flex-1 bg-transparent text-[15px] text-[#0F172A] outline-none placeholder:text-[#64748B]"
          maxLength={200}
          enterKeyHint="search"
        />
        <fieldset className="hidden shrink-0 items-center gap-0.5 rounded-[7px] bg-[#F1F5F9] p-[3px] md:flex">
          <legend className="sr-only">Search mode</legend>
          <label
            className="flex h-[30px] cursor-not-allowed items-center gap-1.5 rounded-[5px] px-2 text-xs font-semibold text-[#64748B]"
            title="Smart Search isn't enabled in this demo yet. Exact search understands categories and budgets."
          >
            <input type="radio" name="search-mode" value="smart" disabled className="sr-only" />
            <span
              className="rounded-[3px] bg-[#E8EFFF] px-1 py-px text-[10px] font-bold text-[#1E5EFF]"
              aria-hidden="true"
            >
              AI
            </span>
            Smart
            <span className="sr-only"> (not enabled in this demo)</span>
          </label>
          <label className="flex h-[30px] cursor-pointer items-center rounded-[5px] bg-white px-2 text-xs font-semibold text-[#0F172A] has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-[#1E5EFF]">
            <input type="radio" name="search-mode" value="exact" defaultChecked className="sr-only" />
            Exact
          </label>
        </fieldset>
        <button
          type="submit"
          className="h-[38px] shrink-0 rounded-chip bg-[#1E5EFF] px-4 text-sm font-bold text-white hover:bg-[#1649CC] sm:px-[18px]"
        >
          Search
        </button>
      </form>

      <div
        id={listId}
        role="listbox"
        aria-label="Search suggestions"
        hidden={!expanded}
        className="absolute left-0 right-0 top-[52px] z-30 flex max-h-[min(70vh,560px)] flex-col overflow-auto rounded-button border border-border bg-surface py-1.5 text-ink shadow-lg"
      >
        {chips.length > 0 && (
          <div
            role="presentation"
            className="flex flex-wrap items-center gap-1.5 border-b border-border px-4 pb-3 pt-2.5"
          >
            <span className="mr-1 text-xs font-semibold text-ink-muted">Looking for</span>
            {chips.map((c) => (
              <span
                key={`${c.label}-${c.value}`}
                className="flex h-[26px] items-center rounded-chip bg-primary-subtle px-2.5 text-xs font-semibold text-primary-hover"
              >
                <span className="sr-only">{c.label}: </span>
                {c.value}
              </span>
            ))}
          </div>
        )}
        {rows.map((row, i) =>
          row.kind === 'label' ? (
            <div
              key={`l-${row.text}`}
              role="presentation"
              className="px-4 pb-1 pt-2.5 text-xs font-semibold text-ink-muted"
            >
              {row.text}
            </div>
          ) : (
            <div
              key={`${row.kind}-${row.to}-${String(i)}`}
              id={optionId(i)}
              role="option"
              tabIndex={-1}
              aria-selected={active === i}
              onMouseDown={(e) => {
                e.preventDefault()
                pick(row)
              }}
              onMouseEnter={() => setActive(i)}
              className={cn(
                'flex min-h-10 cursor-pointer items-center gap-3 pl-4 pr-2.5',
                active === i && 'bg-surface-2',
              )}
            >
              {row.kind === 'product' ? (
                <>
                  <span className="tk-img-well flex size-9 shrink-0 items-center justify-center rounded-chip border border-border bg-surface-2 p-0.5">
                    <ProductImage image={row.product.image} sizes="36px" />
                  </span>
                  <span className="min-w-0 flex-1 truncate py-2 text-sm">{row.product.name}</span>
                  <span className="text-sm font-semibold tabular">{formatMoney(row.product.price)}</span>
                </>
              ) : (
                <>
                  <span
                    className={cn(
                      'min-w-0 flex-1 truncate py-2 text-sm',
                      row.text.startsWith('Search for') && 'font-semibold text-primary',
                    )}
                  >
                    {row.text}
                    {row.sub && <span className="font-normal text-ink-muted">{row.sub}</span>}
                  </span>
                  {row.removable && (
                    <button
                      type="button"
                      tabIndex={-1}
                      aria-label={`Remove ${row.text} from recent searches`}
                      onMouseDown={(e) => {
                        e.preventDefault()
                        e.stopPropagation()
                        setRecent(removeRecentSearch(row.text))
                      }}
                      className="flex size-7 items-center justify-center rounded-chip text-ink-muted hover:bg-border"
                    >
                      <X className="size-4" aria-hidden="true" />
                    </button>
                  )}
                </>
              )}
            </div>
          ),
        )}
      </div>
    </div>
  )
}

function searchUrl(q: string): string {
  return `/search?q=${encodeURIComponent(q)}`
}
