import { ChevronDown, ChevronRight, Menu } from 'lucide-react'
import { useLayoutEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { Dialog } from '@/components/ui/Dialog'
import { useCategories } from '@/data/catalog'

const link =
  'my-[3px] whitespace-nowrap rounded-[5px] px-2.5 py-[9px] text-sm text-white no-underline hover:text-white hover:no-underline hover:bg-white/10'

/** Below this width the row scrolls sideways instead of collapsing into "More". */
const COLLAPSE_MIN = 768

/**
 * Second header row. "All" opens the full category tree. On wider screens the row shows as many departments
 * as fit and gathers the rest behind "More" (which opens the same tree), so it never overflows no matter how
 * many departments the catalog has; on phones the row scrolls horizontally.
 */
export function CategoryNav() {
  const { data: categories = [] } = useCategories()
  const [open, setOpen] = useState(false)
  const [fit, setFit] = useState<number | null>(null)
  const row = useRef<HTMLUListElement>(null)
  const measure = useRef<HTMLUListElement>(null)

  useLayoutEffect(() => {
    const el = row.current
    const ruler = measure.current
    if (!el || !ruler) return
    const update = () => {
      if (window.innerWidth < COLLAPSE_MIN) return setFit(null)
      const items = [...ruler.children] as HTMLElement[]
      // ruler holds: All, Today's deals, one entry per category, More.
      const more = items.at(-1)?.offsetWidth ?? 0
      const style = getComputedStyle(el)
      let room = el.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight) - more
      room -= (items[0]?.offsetWidth ?? 0) + (items[1]?.offsetWidth ?? 0)
      let n = 0
      for (const item of items.slice(2, -1)) {
        room -= item.offsetWidth + 2
        if (room < 0) break
        n++
      }
      setFit(n >= categories.length ? null : n)
    }
    update()
    const ro = new ResizeObserver(update)
    ro.observe(el)
    return () => ro.disconnect()
  }, [categories])

  const shown = fit === null ? categories : categories.slice(0, fit)
  return (
    <nav aria-label="Categories" className="relative bg-header-2">
      {/* Off-screen copy used only to measure label widths. */}
      <div aria-hidden="true" className="pointer-events-none absolute inset-x-0 top-0 h-0 overflow-hidden">
        <ul ref={measure} className="invisible flex w-max whitespace-nowrap">
          <li className={`${link} flex gap-1.5 font-bold`}>
            <Menu className="size-4" />
            All
          </li>
          <li className={link}>Today’s deals</li>
          {categories.map((c) => (
            <li key={c.slug} className={link}>
              {shortName(c.name)}
            </li>
          ))}
          <li className={`${link} flex gap-1`}>
            More
            <ChevronDown className="size-4" />
          </li>
        </ul>
      </div>
      <ul
        ref={row}
        className="page-width page-gutter no-scrollbar relative flex items-center gap-0.5 overflow-x-auto"
      >
        <li>
          <button
            type="button"
            onClick={() => setOpen(true)}
            className={`${link} flex items-center gap-1.5 font-bold`}
            aria-haspopup="dialog"
          >
            <Menu className="size-4" aria-hidden="true" />
            All
          </button>
        </li>
        <li>
          <Link to="/deals" className={link}>
            Today’s deals
          </Link>
        </li>
        {shown.map((c) => (
          <li key={c.slug}>
            <Link to={`/c/${c.slug}`} className={link}>
              {shortName(c.name)}
            </Link>
          </li>
        ))}
        {fit !== null && (
          <li>
            <button
              type="button"
              onClick={() => setOpen(true)}
              className={`${link} flex items-center gap-1`}
              aria-haspopup="dialog"
            >
              More
              <ChevronDown className="size-4" aria-hidden="true" />
            </button>
          </li>
        )}
      </ul>
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        variant="drawer"
        title="Shop by category"
        className="mr-auto ml-0"
      >
        <ul className="flex flex-col gap-4">
          {categories.map((c) => (
            <li key={c.slug}>
              <Link
                to={`/c/${c.slug}`}
                onClick={() => setOpen(false)}
                className="flex items-center font-semibold text-ink"
              >
                {c.name}
                <span className="ml-2 text-xs font-normal text-ink-muted">{c.productCount}</span>
                <ChevronRight className="ml-auto size-4 text-ink-muted" aria-hidden="true" />
              </Link>
              <ul className="mt-1.5 flex flex-col">
                {c.children.map((child) => (
                  <li key={child.slug}>
                    <Link
                      to={`/c/${child.slug}`}
                      onClick={() => setOpen(false)}
                      className="flex min-h-9 items-center rounded-chip px-2 text-sm text-ink-muted hover:bg-surface-2 hover:text-ink hover:no-underline"
                    >
                      {child.name}
                    </Link>
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ul>
      </Dialog>
    </nav>
  )
}

function shortName(name: string): string {
  return name.replace('Desktops & Workstations', 'Desktops').replace('Tablets, VR & More', 'Tablets & more')
}
