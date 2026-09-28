import { formatMoney } from '@/lib/money'
import type { WalletMode } from '@/lib/types'

/** The TrustKart Wallet presented as a payment card, the way a saved card appears at checkout. */
export function WalletCard({
  balance,
  mode,
  holder,
}: {
  balance: string
  mode: WalletMode
  holder?: string
}) {
  return (
    <div
      className="relative aspect-[1.586] w-full max-w-[340px] overflow-hidden rounded-[18px] p-5 text-white shadow-lg"
      style={{ background: 'linear-gradient(135deg, #1e5eff 0%, #1649cc 45%, #0f2f8a 100%)' }}
    >
      <div aria-hidden="true" className="absolute -right-16 -top-16 size-56 rounded-full bg-white/10" />
      <div aria-hidden="true" className="absolute -bottom-24 -left-10 size-64 rounded-full bg-white/5" />
      <div className="relative flex h-full flex-col">
        <div className="flex items-center gap-2">
          <img
            src="/brand/trustkart-mark-136.webp"
            alt=""
            width={28}
            height={28}
            className="size-7 rounded-md"
          />
          <span className="font-bold tracking-[-0.01em]">TrustKart Wallet</span>
        </div>
        <div className="mt-auto">
          <p className="text-[11px] uppercase tracking-[0.12em] text-white/80">Available balance</p>
          <p className="text-2xl font-bold tabular">
            {mode === 'UNLIMITED' ? '∞ Unlimited' : formatMoney(balance)}
          </p>
        </div>
        <div className="mt-3 flex items-end justify-between text-[12px] text-white/85">
          <span className="truncate uppercase tracking-[0.08em]">{holder ?? 'TrustKart member'}</span>
          <span className="font-semibold">{mode === 'UNLIMITED' ? 'UNLIMITED' : 'WALLET'}</span>
        </div>
      </div>
    </div>
  )
}
