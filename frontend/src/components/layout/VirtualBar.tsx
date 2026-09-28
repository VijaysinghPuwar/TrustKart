import { Link } from 'react-router'
import { useWallet } from '@/data/shopping'
import { formatMoneyWhole } from '@/lib/money'

/**
 * The design's demo strip, used for TrustKart's one global disclosure: this is a virtual store. It shows on
 * every page so individual product cards don't have to say "not real".
 */
export function VirtualBar() {
  const { data: wallet } = useWallet()
  return (
    <div className="bg-ink text-[13px] text-bg">
      <div className="page-width page-gutter flex flex-wrap items-center gap-x-4 gap-y-1 py-[7px]">
        <span className="font-semibold">Virtual store</span>
        <span>
          No real money, cards or shipping.{' '}
          <Link to="/about" className="text-bg underline hover:text-bg">
            How it works
          </Link>
        </span>
        {wallet && (
          <Link
            to="/wallet"
            className="ml-auto font-semibold text-bg no-underline tabular hover:text-bg hover:underline sm:hidden"
          >
            {wallet.mode === 'UNLIMITED' ? '∞ Unlimited' : `${formatMoneyWhole(wallet.balance)} virtual`}
          </Link>
        )}
      </div>
    </div>
  )
}
