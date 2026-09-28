import { formatMoney } from '@/lib/money'
import type { Quote } from '@/lib/types'

/** Balance, order total and what's left, straight from the server quote. */
export function WalletSummary({ quote }: { quote: Quote }) {
  const unlimited = quote.walletMode === 'UNLIMITED'
  return (
    <dl className="flex flex-col gap-2 text-sm tabular">
      <div className="flex">
        <dt className="text-ink-muted">Wallet balance</dt>
        <dd className="ml-auto">{unlimited ? '∞ Unlimited' : formatMoney(quote.balance)}</dd>
      </div>
      <div className="flex">
        <dt className="text-ink-muted">Order total</dt>
        <dd className="ml-auto font-semibold">{formatMoney(quote.total)}</dd>
      </div>
      {!unlimited && (
        <div className="flex">
          <dt className="text-ink-muted">{quote.shortfall ? 'Wallet short by' : 'Balance after order'}</dt>
          <dd className={quote.shortfall ? 'ml-auto font-semibold text-danger' : 'ml-auto font-semibold'}>
            {formatMoney(quote.shortfall ?? quote.balanceAfter ?? '0')}
          </dd>
        </div>
      )}
    </dl>
  )
}
