import { formatMoney } from '@/lib/money'
import type { Quote } from '@/lib/types'
import { Badge } from '@/components/ui/Badge'

/** The virtual payment breakdown shown before any purchase. Values come from the server quote. */
export function WalletSummary({ quote }: { quote: Quote }) {
  const unlimited = quote.walletMode === 'UNLIMITED'
  return (
    <dl className="flex flex-col gap-2 text-sm tabular">
      <div className="flex items-center gap-2">
        <dt className="font-semibold">Payment method</dt>
        <dd className="ml-auto flex items-center gap-2">
          TrustKart Virtual Balance <Badge tone="info">Simulation</Badge>
        </dd>
      </div>
      <div className="flex">
        <dt className="text-ink-muted">Virtual balance</dt>
        <dd className="ml-auto">{unlimited ? '∞ Unlimited' : formatMoney(quote.balance)}</dd>
      </div>
      <div className="flex">
        <dt className="text-ink-muted">Purchase total</dt>
        <dd className="ml-auto font-semibold">{formatMoney(quote.total)}</dd>
      </div>
      {!unlimited && (
        <div className="flex">
          <dt className="text-ink-muted">{quote.shortfall ? 'Virtual funds needed' : 'After purchase'}</dt>
          <dd className={quote.shortfall ? 'ml-auto font-semibold text-danger' : 'ml-auto font-semibold'}>
            {formatMoney(quote.shortfall ?? quote.balanceAfter ?? '0')}
          </dd>
        </div>
      )}
    </dl>
  )
}
