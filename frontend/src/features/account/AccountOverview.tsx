import { Link, useNavigate } from 'react-router'
import { Button, ButtonLink } from '@/components/ui/Button'
import { PageSpinner } from '@/components/ui/PageSpinner'
import { useMe, useSignOut } from '@/data/account'
import { useLeaderboardStanding } from '@/data/leaderboards'
import { useCollection, useWallet } from '@/data/shopping'
import { formatMoney } from '@/lib/money'
import { usePageTitle } from '@/lib/usePageTitle'

export default function AccountOverview() {
  usePageTitle('Account')
  const me = useMe()
  const { data: wallet } = useWallet()
  const { data: collection } = useCollection()
  const signOut = useSignOut()
  const navigate = useNavigate()
  if (me.isPending) return <PageSpinner />
  const profile = me.data?.profile

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="flex-1 text-[28px] font-bold leading-9">
          {profile ? `Hello, ${profile.displayName}` : 'Your TrustKart'}
        </h1>
        {profile ? (
          <Button
            variant="secondary"
            loading={signOut.isPending}
            onClick={() => signOut.mutate(undefined, { onSettled: () => void navigate('/') })}
          >
            Sign out
          </Button>
        ) : (
          <ButtonLink to="/signin?returnTo=/account">Sign in</ButtonLink>
        )}
      </div>
      {!profile && (
        <p className="rounded-card border border-border bg-surface p-4 text-sm">
          You’re shopping as a guest. Your cart, wallet and purchases are kept for this browser.{' '}
          <Link to="/signup">Create an account</Link> to keep them across devices; everything you have now
          comes with you.
        </p>
      )}
      {/* Columns follow the space beside the account menu, not the viewport: three across a tablet's narrow
          content column truncated balances like "$450,0…". */}
      <div className="grid grid-cols-[repeat(auto-fit,minmax(12rem,1fr))] gap-3">
        <Summary
          to="/wallet"
          label="Wallet balance"
          value={wallet ? (wallet.mode === 'UNLIMITED' ? '∞ Unlimited' : formatMoney(wallet.balance)) : '…'}
        />
        <Summary
          to="/collection"
          label="Collection value"
          value={collection ? formatMoney(collection.stats.collectionValue) : '…'}
        />
        <Summary
          to="/account/purchases"
          label="Orders"
          value={collection ? String(collection.stats.purchases) : '…'}
        />
      </div>
      {profile && <RankCard />}
      {profile && (
        <section className="rounded-card border border-border bg-surface p-5">
          <div className="mb-3 flex items-center gap-3">
            {profile.avatarUrl && (
              <img
                src={profile.avatarUrl}
                alt=""
                width={48}
                height={48}
                referrerPolicy="no-referrer"
                className="size-12 rounded-full"
              />
            )}
            <h2 className="font-semibold">Profile</h2>
          </div>
          <dl className="grid grid-cols-[140px_1fr] gap-y-2 text-sm">
            <dt className="text-ink-muted">Name</dt>
            <dd>{profile.displayName}</dd>
            <dt className="text-ink-muted">Email</dt>
            <dd className="break-all">{profile.email}</dd>
            <dt className="text-ink-muted">Member since</dt>
            <dd>{new Date(profile.memberSince).toLocaleDateString('en-US', { dateStyle: 'medium' })}</dd>
            <dt className="text-ink-muted">Sign-in methods</dt>
            <dd>
              {['Password', ...profile.linkedProviders.map((p) => (p === 'google' ? 'Google' : p))].join(
                ', ',
              )}
            </dd>
            <dt className="text-ink-muted">Roles</dt>
            <dd>{profile.roles.join(', ')}</dd>
          </dl>
        </section>
      )}
    </div>
  )
}

function Summary({ to, label, value }: { to: string; label: string; value: string }) {
  return (
    <Link
      to={to}
      className="flex flex-col gap-1 rounded-tile border border-border bg-surface p-4 text-ink no-underline hover:border-primary hover:text-ink hover:no-underline"
    >
      <span className="text-sm text-ink-muted">{label}</span>
      <span className="text-2xl font-bold break-words tabular">{value}</span>
    </Link>
  )
}

/** "Your rankings" summary; all amounts are virtual. */
function RankCard() {
  const { data: s } = useLeaderboardStanding(true)
  const line = (label: string, rank?: number, spend?: string) => (
    <div>
      <dt className="text-xs text-ink-muted">{label}</dt>
      <dd className="text-xl font-bold tabular">
        {rank ? `#${rank.toLocaleString('en-US')}` : 'Not ranked'}
      </dd>
      {spend && <dd className="text-xs text-ink-muted tabular">{formatMoney(spend)} virtual spent</dd>}
    </div>
  )
  return (
    <section aria-labelledby="rank-card" className="rounded-card border border-border bg-surface p-5">
      <div className="mb-3 flex items-center gap-3">
        <h2 id="rank-card" className="flex-1 font-semibold">
          Your rankings
        </h2>
        <Link to="/rankings" className="text-sm">
          View leaderboard
        </Link>
      </div>
      <dl className="grid grid-cols-2 gap-4">
        {line('This month', s?.monthly?.rank, s?.monthly?.virtualSpend)}
        {line('All time', s?.allTime?.rank, s?.allTime?.virtualSpend)}
      </dl>
    </section>
  )
}
