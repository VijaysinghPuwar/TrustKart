import { Crown, Medal, Trophy } from 'lucide-react'
import { type KeyboardEvent, useState } from 'react'
import { Link } from 'react-router'
import { ButtonLink } from '@/components/ui/Button'
import { ErrorState } from '@/components/ui/ErrorState'
import { Skeleton } from '@/components/ui/Skeleton'
import { useMe } from '@/data/account'
import { type BoardType, useLeaderboard, useLeaderboardStanding } from '@/data/leaderboards'
import { cn } from '@/lib/cn'
import { formatMoney } from '@/lib/money'
import type { LeaderboardEntry, LeaderboardPosition } from '@/lib/types'
import { usePageTitle } from '@/lib/usePageTitle'

const TABS: { type: BoardType; label: string }[] = [
  { type: 'monthly', label: 'This Month' },
  { type: 'all-time', label: 'All Time' },
]

const PLACE = ['', '1st', '2nd', '3rd']

function initials(name: string) {
  const parts = name
    .replace(/_/g, ' ')
    .trim()
    .split(/\s+|(?=[A-Z][a-z])/)
  return (
    (parts[0]?.[0] ?? '?') + (parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : '')
  ).toUpperCase()
}

function Avatar({ entry, size = 40 }: { entry: LeaderboardEntry; size?: number }) {
  if (entry.avatarUrl) {
    return (
      <img
        src={entry.avatarUrl}
        alt=""
        width={size}
        height={size}
        referrerPolicy="no-referrer"
        className="shrink-0 rounded-full object-cover"
        style={{ width: size, height: size }}
      />
    )
  }
  return (
    <span
      aria-hidden="true"
      className={cn(
        'flex shrink-0 items-center justify-center rounded-full font-semibold',
        entry.anonymous ? 'bg-surface-2 text-ink-muted' : 'bg-primary-subtle text-primary-hover',
      )}
      style={{ width: size, height: size, fontSize: size * 0.36 }}
    >
      {entry.anonymous ? '?' : initials(entry.displayName)}
    </span>
  )
}

/** Rank shown as text first; the icon and colour only reinforce it. */
function RankMark({ rank, large = false }: { rank: number; large?: boolean }) {
  const tone =
    rank === 1 ? 'text-[#d4a106]' : rank === 2 ? 'text-[#9ca3af]' : rank === 3 ? 'text-[#c9824a]' : ''
  const Icon = rank === 1 ? Crown : Medal
  return (
    <span className={cn('inline-flex items-center gap-1 font-bold tabular', large ? 'text-lg' : 'text-sm')}>
      {rank <= 3 && <Icon className={cn(large ? 'size-5' : 'size-4', tone)} aria-hidden="true" />}
      <span className="sr-only">Rank </span>#{rank}
    </span>
  )
}

function YouBadge() {
  return (
    <span className="rounded-full bg-primary px-2 py-0.5 text-[11px] font-semibold text-on-primary">You</span>
  )
}

function Podium({ top, allTime }: { top: LeaderboardEntry[]; allTime: boolean }) {
  // Desktop order 2, 1, 3 with #1 raised; phones get a simple 1, 2, 3 stack.
  const desktopOrder = [top[1], top[0], top[2]].filter((e): e is LeaderboardEntry => Boolean(e))
  const card = (e: LeaderboardEntry, raised: boolean) => (
    <li
      key={`${String(e.rank)}-${e.displayName}`}
      className={cn(
        'flex flex-col items-center gap-2 rounded-card border bg-surface p-5 text-center sm:w-[calc((100%-2rem)/3)]',
        e.currentUser ? 'border-primary' : 'border-border',
        raised && 'sm:-translate-y-3 sm:shadow-lg',
      )}
    >
      <RankMark rank={e.rank} large />
      <span className="text-xs font-semibold uppercase tracking-wide text-ink-subtle">{PLACE[e.rank]}</span>
      <Avatar entry={e} size={raised ? 64 : 52} />
      <p className={cn('max-w-full truncate font-semibold', e.anonymous && 'text-ink-muted')}>
        {e.displayName}
      </p>
      {e.currentUser && <YouBadge />}
      <p className="text-lg font-bold tabular">{formatMoney(e.virtualSpend)}</p>
      <p className="text-xs text-ink-muted">
        virtual spent · {e.orderCount} {e.orderCount === 1 ? 'order' : 'orders'}
        {allTime ? '' : ' this month'}
      </p>
    </li>
  )
  return (
    <>
      <ol className="hidden items-end justify-center gap-4 sm:flex" aria-label="Top three">
        {desktopOrder.map((e) => card(e, e.rank === 1))}
      </ol>
      <ol className="flex flex-col gap-3 sm:hidden" aria-label="Top three">
        {top.map((e) => card(e, false))}
      </ol>
    </>
  )
}

function Rest({ entries, allTime }: { entries: LeaderboardEntry[]; allTime: boolean }) {
  if (entries.length === 0) return null
  return (
    <>
      <table className="hidden w-full text-sm md:table">
        <caption className="sr-only">Ranks 4 and below</caption>
        <thead>
          <tr className="border-b border-border text-left text-ink-muted">
            <th scope="col" className="w-20 py-2 font-medium">
              Rank
            </th>
            <th scope="col" className="py-2 font-medium">
              Collector
            </th>
            <th scope="col" className="py-2 text-right font-medium">
              Virtual spend
            </th>
            <th scope="col" className="py-2 text-right font-medium">
              Orders
            </th>
            {allTime && (
              <th scope="col" className="py-2 text-right font-medium">
                Member since
              </th>
            )}
          </tr>
        </thead>
        <tbody>
          {entries.map((e) => (
            <tr
              key={`${String(e.rank)}-${e.displayName}`}
              className={cn('border-b border-border', e.currentUser && 'bg-primary-subtle/60')}
            >
              <td className="py-2.5">
                <RankMark rank={e.rank} />
              </td>
              <td className="py-2.5">
                <span className="flex min-w-0 items-center gap-3">
                  <Avatar entry={e} size={32} />
                  <span className={cn('truncate font-medium', e.anonymous && 'text-ink-muted')}>
                    {e.displayName}
                  </span>
                  {e.currentUser && <YouBadge />}
                </span>
              </td>
              <td className="py-2.5 text-right font-semibold tabular">{formatMoney(e.virtualSpend)}</td>
              <td className="py-2.5 text-right tabular">{e.orderCount}</td>
              {allTime && (
                <td className="py-2.5 text-right text-ink-muted">
                  {e.memberSince
                    ? new Date(e.memberSince).toLocaleDateString('en-US', { month: 'short', year: 'numeric' })
                    : 'Hidden'}
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
      <ol className="flex flex-col gap-2 md:hidden" aria-label="Ranks 4 and below">
        {entries.map((e) => (
          <li
            key={`${String(e.rank)}-${e.displayName}`}
            className={cn(
              'flex items-center gap-3 rounded-card border bg-surface p-3',
              e.currentUser ? 'border-primary' : 'border-border',
            )}
          >
            <span className="w-12 shrink-0">
              <RankMark rank={e.rank} />
            </span>
            <Avatar entry={e} size={36} />
            <span className="min-w-0 flex-1">
              <span className="flex items-center gap-2">
                <span className={cn('truncate font-medium', e.anonymous && 'text-ink-muted')}>
                  {e.displayName}
                </span>
                {e.currentUser && <YouBadge />}
              </span>
              <span className="block text-sm">
                <span className="font-semibold tabular">{formatMoney(e.virtualSpend)}</span>
                <span className="text-xs text-ink-muted">
                  {' '}
                  virtual · {e.orderCount} {e.orderCount === 1 ? 'order' : 'orders'}
                </span>
              </span>
            </span>
          </li>
        ))}
      </ol>
    </>
  )
}

function PositionLine({ label, p, listName }: { label: string; p?: LeaderboardPosition; listName: string }) {
  if (!p) {
    return (
      <div>
        <p className="text-xs text-ink-muted">{label}</p>
        <p className="font-semibold">Not ranked yet</p>
      </div>
    )
  }
  return (
    <div>
      <p className="text-xs text-ink-muted">{label}</p>
      <p className="text-2xl font-bold tabular">
        #{p.rank.toLocaleString('en-US')}
        <span className="ml-1 text-sm font-normal text-ink-muted">
          of {p.rankedCount.toLocaleString('en-US')}
        </span>
      </p>
      <p className="text-sm tabular">{formatMoney(p.virtualSpend)} virtual spent</p>
      {p.gapToTopList && (
        <p className="text-xs text-ink-muted">
          {formatMoney(p.gapToTopList)} of virtual spending separates you from the {listName}.
        </p>
      )}
    </div>
  )
}

function YourPosition({ signedIn }: { signedIn: boolean }) {
  const standing = useLeaderboardStanding(signedIn)
  if (!signedIn) {
    return (
      <section className="flex flex-wrap items-center gap-3 rounded-card border border-border bg-surface p-4 text-sm">
        <Trophy className="size-5 text-primary" aria-hidden="true" />
        <p className="flex-1">
          <Link to="/signin?returnTo=/rankings">Sign in</Link> to see your own rank. Guest purchases count
          once they move into your account.
        </p>
      </section>
    )
  }
  const s = standing.data
  return (
    <section aria-labelledby="your-rank" className="rounded-card border border-primary/40 bg-surface p-5">
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <h2 id="your-rank" className="flex-1 font-semibold">
          Your position
        </h2>
        {s && (
          <span className="text-sm text-ink-muted">
            Shown as <strong className="text-ink">{s.visible ? s.displayName : 'Anonymous collector'}</strong>{' '}
            · <Link to="/account/rankings">Change</Link>
          </span>
        )}
      </div>
      {standing.isPending ? (
        <Skeleton className="h-16" />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          <PositionLine
            label={`This month (${s?.periodLabel ?? ''})`}
            p={s?.monthly}
            listName="monthly Top 50"
          />
          <PositionLine label="All time" p={s?.allTime} listName="all-time Top 100" />
        </div>
      )}
    </section>
  )
}

function monthEnds(iso?: string) {
  if (!iso) return null
  const end = new Date(iso)
  return end.toLocaleDateString('en-US', { month: 'long', day: 'numeric', timeZone: 'UTC' })
}

export default function RankingsPage() {
  usePageTitle('Rankings')
  const [tab, setTab] = useState<BoardType>('monthly')
  const board = useLeaderboard(tab)
  const { data: me } = useMe()
  const allTime = tab === 'all-time'
  const b = board.data
  const top = b?.entries.slice(0, 3) ?? []
  const rest = b?.entries.slice(3) ?? []

  const onTabKey = (e: KeyboardEvent<HTMLButtonElement>) => {
    const i = TABS.findIndex((t) => t.type === tab)
    const next =
      e.key === 'ArrowRight'
        ? (i + 1) % TABS.length
        : e.key === 'ArrowLeft'
          ? (i - 1 + TABS.length) % TABS.length
          : e.key === 'Home'
            ? 0
            : e.key === 'End'
              ? TABS.length - 1
              : -1
    const target = TABS[next]
    if (!target) return
    e.preventDefault()
    setTab(target.type)
    document.getElementById(`tab-${target.type}`)?.focus()
  }

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-2">
        <h1 className="text-[28px] font-bold leading-9">Top Virtual Spenders</h1>
        <p className="text-ink-muted">See who is building the biggest technology collection on TrustKart.</p>
        <p className="text-xs text-ink-subtle">
          All spending shown here is virtual. No real money is involved. Only completed TrustKart orders
          count; adding wallet funds does not.
        </p>
      </header>

      {/* WAI-ARIA tabs: one tab stop, arrows (and Home/End) move between periods and select them. */}
      <div role="tablist" aria-label="Leaderboard period" className="flex gap-2">
        {TABS.map((t) => (
          <button
            key={t.type}
            role="tab"
            type="button"
            id={`tab-${t.type}`}
            aria-selected={tab === t.type}
            aria-controls="leaderboard-panel"
            tabIndex={tab === t.type ? 0 : -1}
            onKeyDown={onTabKey}
            onClick={() => setTab(t.type)}
            className={cn(
              'h-10 rounded-chip border px-4 text-sm font-semibold',
              tab === t.type
                ? 'border-primary bg-primary text-on-primary'
                : 'border-border text-ink hover:border-ink-muted',
            )}
          >
            {t.label}
          </button>
        ))}
      </div>

      <YourPosition signedIn={Boolean(me?.authenticated)} />

      <section
        id="leaderboard-panel"
        role="tabpanel"
        aria-labelledby={`tab-${tab}`}
        className="flex flex-col gap-5"
      >
        {board.isError ? (
          <ErrorState error={board.error} onRetry={() => void board.refetch()} />
        ) : !b ? (
          <Skeleton className="h-64" />
        ) : (
          <>
            <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
              <h2 className="text-xl font-bold">
                {allTime ? 'All-time Top 100' : `${b.periodLabel} Top 50`}
              </h2>
              <p className="text-sm text-ink-muted">
                {b.rankedCount.toLocaleString('en-US')} ranked
                {!allTime &&
                  b.periodEndsAt &&
                  ` · a new month starts ${monthEnds(b.periodEndsAt) ?? ''} (UTC)`}
              </p>
            </div>
            {b.entries.length === 0 ? (
              <div className="flex flex-col items-start gap-3 rounded-card border border-border bg-surface p-6">
                <Trophy className="size-8 text-ink-subtle" aria-hidden="true" />
                <p className="text-lg font-semibold">
                  {allTime ? 'No rankings yet' : 'No rankings yet this month'}
                </p>
                <p className="text-sm text-ink-muted">Complete a virtual purchase to join the leaderboard.</p>
                <ButtonLink to="/deals" variant="secondary">
                  Browse today’s deals
                </ButtonLink>
              </div>
            ) : (
              <>
                <Podium top={top} allTime={allTime} />
                <Rest entries={rest} allTime={allTime} />
              </>
            )}
          </>
        )}
      </section>
    </div>
  )
}
