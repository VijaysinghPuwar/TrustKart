import { NavLink, Outlet } from 'react-router'
import { cn } from '@/lib/cn'

const links = [
  ['/account', 'Overview', true],
  ['/account/purchases', 'Virtual purchases', false],
  ['/wallet', 'TrustKart Wallet', false],
  ['/collection', 'My collection', false],
  ['/wishlist', 'Wishlists', false],
  ['/account/addresses', 'Addresses', false],
  ['/account/security', 'Security center', false],
] as const

export default function AccountLayout() {
  return (
    <div className="grid grid-cols-[minmax(0,1fr)] gap-6 md:grid-cols-[220px_minmax(0,1fr)]">
      <nav aria-label="Account" className="md:sticky md:top-40 md:self-start">
        <ul className="no-scrollbar relative flex gap-1 overflow-x-auto md:flex-col">
          {links.map(([to, label, end]) => (
            <li key={to}>
              <NavLink
                to={to}
                end={end}
                className={({ isActive }) =>
                  cn(
                    'flex min-h-10 items-center whitespace-nowrap rounded-control px-3 text-sm text-ink no-underline hover:bg-surface-2 hover:text-ink hover:no-underline',
                    isActive &&
                      'bg-primary-subtle font-semibold text-primary-hover hover:bg-primary-subtle hover:text-primary-hover',
                  )
                }
              >
                {label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      <div className="min-w-0">
        <Outlet />
      </div>
    </div>
  )
}
