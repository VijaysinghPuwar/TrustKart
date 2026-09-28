import { ChevronRight, Menu } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { Dialog } from '@/components/ui/Dialog'
import { useCategories } from '@/data/catalog'

const link =
  'my-[3px] whitespace-nowrap rounded-[5px] px-2.5 py-[9px] text-sm text-white no-underline hover:text-white hover:no-underline hover:bg-white/10'

/** Second header row: "All" opens the full category tree; the rest scroll horizontally on small screens. */
export function CategoryNav() {
  const { data: categories = [] } = useCategories()
  const [open, setOpen] = useState(false)
  return (
    <nav aria-label="Categories" className="bg-header-2">
      <ul className="page-width page-gutter no-scrollbar relative flex items-center gap-0.5 overflow-x-auto">
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
        {categories.map((c) => (
          <li key={c.slug}>
            <Link to={`/c/${c.slug}`} className={link}>
              {shortName(c.name)}
            </Link>
          </li>
        ))}
        <li>
          <Link to="/collection" className={link}>
            My collection
          </Link>
        </li>
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
