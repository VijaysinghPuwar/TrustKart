import { Moon, Sun } from 'lucide-react'
import { Link } from 'react-router'
import { useTheme } from '@/state/theme'

const columns = [
  {
    title: 'Shop',
    links: [
      ['Today’s deals', '/deals'],
      ['Dream GPUs', '/collections/dream-gpus'],
      ['Homelab starter', '/collections/homelab-starter'],
      ['Compare products', '/compare'],
    ],
  },
  {
    title: 'Your TrustKart',
    links: [
      ['TrustKart Wallet', '/wallet'],
      ['My collection', '/collection'],
      ['Virtual purchases', '/account/purchases'],
      ['Wishlists', '/wishlist'],
    ],
  },
  {
    title: 'About',
    links: [
      ['How TrustKart works', '/about'],
      ['About this project', '/about/project'],
      ['Privacy', '/about/privacy'],
      ['Image credits', '/about/credits'],
    ],
  },
] as const

export function SiteFooter() {
  const { theme, toggle } = useTheme()
  return (
    <footer className="border-t border-border bg-surface">
      <div className="page-width page-gutter grid grid-cols-[repeat(auto-fit,minmax(150px,1fr))] gap-7 pb-5 pt-9">
        {columns.map((col) => (
          <nav key={col.title} aria-label={col.title} className="flex flex-col gap-2.5 text-sm">
            <h2 className="font-semibold">{col.title}</h2>
            {col.links.map(([label, to]) => (
              <Link key={to} to={to} className="text-ink-muted">
                {label}
              </Link>
            ))}
          </nav>
        ))}
        <div className="flex flex-col gap-2.5 text-sm sm:col-span-2">
          <h2 className="font-semibold">A virtual shopping experience</h2>
          <p className="max-w-[420px] text-ink-muted">
            TrustKart is a portfolio simulation. Browse real technology, build a dream cart and check out with
            virtual funds. Products are not for sale, nothing is charged, and no financial information is ever
            requested.
          </p>
          <button
            type="button"
            onClick={toggle}
            className="flex h-10 w-fit items-center gap-2 rounded-control border border-border-strong px-3 font-semibold text-ink hover:bg-surface-2"
          >
            {theme === 'dark' ? (
              <Sun className="size-4" aria-hidden="true" />
            ) : (
              <Moon className="size-4" aria-hidden="true" />
            )}
            {theme === 'dark' ? 'Light theme' : 'Dark theme'}
          </button>
        </div>
      </div>
      <div className="page-width page-gutter flex flex-wrap items-center gap-3 border-t border-border pb-6 pt-3.5 text-[13px] text-ink-muted">
        <span>© 2026 TrustKart · Shop everything. Spend nothing.</span>
        <span className="sm:ml-auto">
          Brand and product names belong to their owners. No affiliation or endorsement is implied.
        </span>
      </div>
    </footer>
  )
}
