import { Trophy } from 'lucide-react'
import { Link } from 'react-router'
import { useMe } from '@/data/account'
import { useCart, useWallet } from '@/data/shopping'
import { formatMoneyWhole } from '@/lib/money'
import { useCartDrawer } from '@/state/cartDrawer'
import { SmartSearch } from '@/components/search/SmartSearch'
import { CategoryNav } from './CategoryNav'
import { NotificationBell } from './NotificationBell'

const navItem =
  'flex h-[46px] flex-col justify-center whitespace-nowrap rounded-chip px-2 leading-4 text-white no-underline hover:text-white hover:no-underline hover:bg-white/10'

/**
 * Two-row dark header from the approved design. The design's "Deliver to" slot shows the virtual balance
 * instead, since nothing is delivered in TrustKart.
 */
export function SiteHeader() {
  const { data: me } = useMe()
  const { data: cart } = useCart()
  const { data: wallet } = useWallet()
  const drawer = useCartDrawer()
  const count = cart?.itemCount ?? 0
  const name = me?.profile?.displayName.split(' ')[0]

  return (
    <header className="on-dark sticky top-0 z-20">
      <div className="bg-header text-white">
        <div className="page-width page-gutter flex flex-wrap items-center gap-x-3 gap-y-2.5 py-2.5">
          <Link
            to="/"
            aria-label="TrustKart home"
            className="flex shrink-0 items-center gap-2.5 rounded-chip px-1.5 py-1 no-underline hover:bg-white/10"
          >
            <img
              src="/brand/trustkart-mark-136.webp"
              alt=""
              width={34}
              height={34}
              className="size-[34px] rounded-control"
            />
            <span className="text-[21px] font-bold tracking-[-0.015em] text-white">TrustKart</span>
          </Link>

          <Link
            to="/wallet"
            className={`${navItem} hidden sm:flex`}
            aria-label={balanceLabel(wallet?.mode, wallet?.balance)}
          >
            <span className="text-xs text-header-ink-muted">Wallet balance</span>
            <span className="text-sm font-bold tabular">
              {wallet
                ? wallet.mode === 'UNLIMITED'
                  ? '∞ Unlimited'
                  : formatMoneyWhole(wallet.balance)
                : '…'}
            </span>
          </Link>

          <div className="order-3 min-w-0 flex-[1_1_100%] search:order-1 search:flex-[1_1_320px]">
            <SmartSearch />
          </div>

          <nav aria-label="Account" className="order-2 ml-auto flex items-center gap-0.5">
            <Link
              to={me?.authenticated ? '/account' : '/signin'}
              className={`${navItem} ${me?.profile?.avatarUrl ? 'flex-row items-center gap-2' : ''}`}
            >
              {me?.profile?.avatarUrl && (
                <img
                  src={me.profile.avatarUrl}
                  alt=""
                  width={28}
                  height={28}
                  referrerPolicy="no-referrer"
                  className="size-7 rounded-full"
                />
              )}
              <span className="flex flex-col">
                <span className="text-xs text-header-ink-muted">
                  {name ? `Hello, ${name}` : 'Hello, sign in'}
                </span>
                <span className="text-sm font-bold">Account</span>
              </span>
            </Link>
            <Link to="/account/purchases" className={`${navItem} hidden sm:flex`}>
              <span className="text-xs text-header-ink-muted">Orders</span>
              <span className="text-sm font-bold">&amp; Collection</span>
            </Link>
            <Link
              to="/rankings"
              aria-label="Rankings"
              className="flex size-[46px] items-center justify-center rounded-chip text-white hover:bg-white/10 lg:w-auto lg:gap-1.5 lg:px-2.5"
            >
              <Trophy className="size-[20px]" aria-hidden="true" />
              <span className="hidden text-sm font-bold lg:inline">Rankings</span>
            </Link>
            <NotificationBell />
            <button
              type="button"
              onClick={drawer.show}
              aria-label={`Cart, ${String(count)} ${count === 1 ? 'item' : 'items'}`}
              className="flex h-[46px] items-center gap-2 whitespace-nowrap rounded-chip px-2.5 text-sm font-bold text-white hover:bg-white/10"
            >
              <span
                key={count}
                className="tk-bump flex h-6 min-w-6 items-center justify-center rounded-chip bg-accent px-1.5 text-[13px] font-bold text-on-accent tabular"
              >
                {count}
              </span>
              Cart
            </button>
          </nav>
        </div>
      </div>
      <CategoryNav />
    </header>
  )
}

function balanceLabel(mode?: string, balance?: string): string {
  if (!balance) return 'TrustKart Wallet'
  return mode === 'UNLIMITED'
    ? 'TrustKart Wallet, unlimited mode'
    : `TrustKart Wallet, balance ${formatMoneyWhole(balance)}`
}
