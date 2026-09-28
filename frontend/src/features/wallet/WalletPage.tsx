import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ErrorState } from '@/components/ui/ErrorState'
import { PageSpinner } from '@/components/ui/PageSpinner'
import { useToast } from '@/components/ui/Toast'
import { useSetWalletMode, useWallet, useWalletTransactions } from '@/data/shopping'
import { cn } from '@/lib/cn'
import { formatMoney } from '@/lib/money'
import { usePageTitle } from '@/lib/usePageTitle'
import type { WalletMode } from '@/lib/types'
import { Pagination } from '@/features/search/Pagination'
import { AddFundsDialog } from './AddFundsDialog'

const TX_LABEL = {
  CREDIT: 'Added funds',
  PURCHASE: 'Order',
  REFUND: 'Refund',
  RESET: 'Reset',
} as const

export default function WalletPage() {
  usePageTitle('TrustKart Wallet')
  const wallet = useWallet()
  const [params, setParams] = useSearchParams()
  const [page, setPage] = useState(0)
  const tx = useWalletTransactions(page)
  const setMode = useSetWalletMode()
  const { notify } = useToast()
  const addOpen = params.get('add') === '1'

  if (wallet.isPending) return <PageSpinner />
  if (wallet.isError) return <ErrorState error={wallet.error} onRetry={() => void wallet.refetch()} />
  const w = wallet.data
  const unlimited = w.mode === 'UNLIMITED'

  function openAdd(open: boolean) {
    const next = new URLSearchParams(params)
    if (open) next.set('add', '1')
    else next.delete('add')
    setParams(next, { replace: true })
  }

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-[32px] font-bold leading-10">TrustKart Wallet</h1>
      <div className="grid gap-4 md:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        <section
          aria-label="Wallet balance"
          className="flex flex-col gap-4 rounded-tile bg-primary p-6 text-on-primary"
        >
          <div className="flex items-center gap-2">
            <span className="text-sm font-semibold">Wallet balance</span>
          </div>
          <p className="text-[clamp(32px,5vw,48px)] font-bold leading-none tabular">
            {unlimited ? '∞ Unlimited' : formatMoney(w.balance)}
          </p>
          <p className="text-sm">Use your balance for any order. Top up any time.</p>
          <div className="flex flex-wrap gap-2">
            <Button variant="inverse" size="lg" onClick={() => openAdd(true)}>
              Add funds
            </Button>
          </div>
        </section>
        <section
          aria-labelledby="mode-heading"
          className="flex flex-col gap-3 rounded-tile border border-border bg-surface p-5"
        >
          <h2 id="mode-heading" className="font-semibold">
            Shopping mode
          </h2>
          <fieldset className="flex flex-col gap-2">
            <legend className="sr-only">Shopping mode</legend>
            {(
              [
                ['BUDGET', 'Budget mode', 'Orders are paid from your balance. Top up any time.'],
                [
                  'UNLIMITED',
                  'Unlimited mode',
                  'Buy anything. Checkout always succeeds and your balance stays put.',
                ],
              ] as [WalletMode, string, string][]
            ).map(([mode, title, text]) => (
              <label
                key={mode}
                className={cn(
                  'flex cursor-pointer gap-3 rounded-card border p-3 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-primary',
                  w.mode === mode
                    ? 'border-primary bg-primary-subtle'
                    : 'border-border hover:border-border-strong',
                )}
              >
                <input
                  type="radio"
                  name="mode"
                  className="mt-1 accent-primary"
                  checked={w.mode === mode}
                  disabled={setMode.isPending}
                  onChange={() => setMode.mutate(mode, { onSuccess: () => notify(`${title} on`) })}
                />
                <span>
                  <span className="block font-semibold">{title}</span>
                  <span className="text-[13px] text-ink-muted">{text}</span>
                </span>
              </label>
            ))}
          </fieldset>
          <p className="text-[13px] text-ink-muted">
            See what you’ve bought in <Link to="/collection">My collection</Link>.
          </p>
        </section>
      </div>

      <section aria-labelledby="tx-heading" className="flex flex-col gap-3">
        <h2 id="tx-heading" className="text-[22px] font-bold">
          Transaction history
        </h2>
        {!w.exists ? (
          <p className="rounded-card border border-border bg-surface p-5 text-sm text-ink-muted">
            Your wallet starts with {formatMoney(w.startingBalance)} the first time you use it.
          </p>
        ) : tx.data && tx.data.items.length > 0 ? (
          <div
            role="region"
            aria-label="Transaction history"
            tabIndex={0}
            className="relative overflow-x-auto rounded-card border border-border bg-surface"
          >
            <table className="w-full min-w-[560px] text-sm">
              <thead>
                <tr className="border-b border-border text-left text-ink-muted">
                  <th scope="col" className="px-4 py-2.5 font-medium">
                    Date
                  </th>
                  <th scope="col" className="px-4 py-2.5 font-medium">
                    Type
                  </th>
                  <th scope="col" className="px-4 py-2.5 font-medium">
                    Details
                  </th>
                  <th scope="col" className="px-4 py-2.5 text-right font-medium">
                    Amount
                  </th>
                  <th scope="col" className="px-4 py-2.5 text-right font-medium">
                    Balance
                  </th>
                </tr>
              </thead>
              <tbody>
                {tx.data.items.map((t) => {
                  const negative = t.amount.startsWith('-')
                  return (
                    <tr key={t.id} className="border-b border-border last:border-0">
                      <td className="whitespace-nowrap px-4 py-2.5 text-ink-muted">
                        {new Date(t.createdAt).toLocaleString('en-US', {
                          dateStyle: 'medium',
                          timeStyle: 'short',
                        })}
                      </td>
                      <td className="px-4 py-2.5">
                        <Badge
                          tone={t.type === 'PURCHASE' ? 'info' : t.type === 'RESET' ? 'neutral' : 'trust'}
                        >
                          {TX_LABEL[t.type]}
                        </Badge>
                      </td>
                      <td className="px-4 py-2.5">{t.description}</td>
                      <td
                        className={cn(
                          'whitespace-nowrap px-4 py-2.5 text-right font-semibold tabular',
                          !negative && 'text-trust',
                        )}
                      >
                        {negative ? '−' : '+'}
                        {formatMoney(t.amount.replace('-', ''))}
                      </td>
                      <td className="whitespace-nowrap px-4 py-2.5 text-right tabular">
                        {formatMoney(t.balanceAfter)}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        ) : (
          <PageSpinner />
        )}
        {tx.data && (
          <Pagination
            page={page}
            totalPages={Math.ceil(tx.data.totalItems / tx.data.size)}
            onPage={setPage}
          />
        )}
      </section>

      <AddFundsDialog open={addOpen} onClose={() => openAdd(false)} />
    </div>
  )
}
