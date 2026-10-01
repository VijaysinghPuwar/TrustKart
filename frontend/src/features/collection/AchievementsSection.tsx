import { Lock, Medal, Trophy } from 'lucide-react'
import { useState } from 'react'
import { cn } from '@/lib/cn'
import type { Achievement, AchievementTier } from '@/lib/types'

const TIER: Record<AchievementTier, { label: string; ring: string; icon: string; bar: string }> = {
  BRONZE: {
    label: 'Bronze',
    ring: 'border-[#b87333]/60',
    icon: 'bg-[#b87333]/15 text-[#c9824a]',
    bar: 'bg-[#c9824a]',
  },
  SILVER: {
    label: 'Silver',
    ring: 'border-[#9ca3af]/60',
    icon: 'bg-[#9ca3af]/15 text-[#9ca3af]',
    bar: 'bg-[#9ca3af]',
  },
  GOLD: {
    label: 'Gold',
    ring: 'border-[#eab308]/60',
    icon: 'bg-[#eab308]/15 text-[#d4a106]',
    bar: 'bg-[#eab308]',
  },
  PLATINUM: {
    label: 'Platinum',
    ring: 'border-[#22d3ee]/60',
    icon: 'bg-[#22d3ee]/15 text-[#0ea5c6]',
    bar: 'bg-[#22d3ee]',
  },
  LEGENDARY: {
    label: 'Legendary',
    ring: 'border-[#a855f7]/70',
    icon: 'bg-[#a855f7]/15 text-[#a855f7]',
    bar: 'bg-[#a855f7]',
  },
}

function Card({ a, highlight = false }: { a: Achievement; highlight?: boolean }) {
  const tier = TIER[a.tier]
  return (
    <li
      className={cn(
        'flex gap-3 rounded-card border bg-surface p-4',
        a.unlocked ? tier.ring : 'border-border',
        highlight && !a.unlocked && 'border-primary',
      )}
    >
      <span
        className={cn(
          'flex size-10 shrink-0 items-center justify-center rounded-full',
          a.unlocked ? tier.icon : 'bg-surface-2 text-ink-subtle',
        )}
      >
        {a.unlocked ? (
          a.tier === 'LEGENDARY' || a.tier === 'PLATINUM' ? (
            <Trophy className="size-5" aria-hidden="true" />
          ) : (
            <Medal className="size-5" aria-hidden="true" />
          )
        ) : (
          <Lock className="size-5" aria-hidden="true" />
        )}
      </span>
      <div className="min-w-0 flex-1">
        <p className="flex flex-wrap items-center gap-x-2 font-semibold">
          {a.title}
          <span className="text-[11px] font-semibold uppercase tracking-wide text-ink-subtle">
            {tier.label}
          </span>
          <span className="sr-only">{a.unlocked ? ' (unlocked)' : ' (locked)'}</span>
        </p>
        <p className="text-[13px] text-ink-muted">{a.description}</p>
        <div className="mt-2 flex items-center gap-2">
          <div
            className="h-1.5 flex-1 rounded-full bg-surface-2"
            role="progressbar"
            aria-label={`${a.title} progress`}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={a.percent}
          >
            <div
              className={cn('h-full rounded-full', a.unlocked ? tier.bar : 'bg-primary')}
              style={{ width: `${String(Math.max(a.percent, a.percent > 0 ? 3 : 0))}%` }}
            />
          </div>
          <span className="shrink-0 text-xs font-semibold tabular text-ink-muted">{a.progress}</span>
        </div>
      </div>
    </li>
  )
}

/**
 * Achievements with an overall score, the three goals closest to completion ("Up next") and group filters.
 * Everything is computed by the server from order history, so progress can't drift from what was bought.
 */
export function AchievementsSection({ achievements }: { achievements: Achievement[] }) {
  const [group, setGroup] = useState<string>('All')
  const unlocked = achievements.filter((a) => a.unlocked).length
  const groups = ['All', ...new Set(achievements.map((a) => a.group))]
  const upNext = achievements
    .filter((a) => !a.unlocked)
    .sort((x, y) => y.percent - x.percent)
    .slice(0, 3)
  const shown = achievements
    .filter((a) => group === 'All' || a.group === group)
    // Unlocked first, then the nearest goals.
    .sort((x, y) => Number(y.unlocked) - Number(x.unlocked) || y.percent - x.percent)

  return (
    <section aria-labelledby="achievements-heading" className="flex flex-col gap-4">
      <div className="flex flex-wrap items-end gap-x-4 gap-y-2">
        <h2 id="achievements-heading" className="text-[22px] font-bold">
          Achievements
        </h2>
        <p className="text-sm text-ink-muted">
          <span className="font-semibold text-ink tabular">{unlocked}</span> of {achievements.length} unlocked
        </p>
      </div>
      <div
        className="h-2 rounded-full bg-surface-2"
        role="progressbar"
        aria-label="Achievements unlocked"
        aria-valuemin={0}
        aria-valuemax={achievements.length}
        aria-valuenow={unlocked}
      >
        <div
          className="h-full rounded-full bg-trust transition-[width] duration-700"
          style={{ width: `${String(achievements.length ? (unlocked / achievements.length) * 100 : 0)}%` }}
        />
      </div>

      {upNext.length > 0 && (
        <div className="flex flex-col gap-2">
          <h3 className="text-sm font-semibold">Up next</h3>
          <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {upNext.map((a) => (
              <Card key={a.code} a={a} highlight />
            ))}
          </ul>
        </div>
      )}

      <div role="group" aria-label="Filter achievements" className="no-scrollbar flex gap-2 overflow-x-auto">
        {groups.map((g) => (
          <button
            key={g}
            type="button"
            aria-pressed={group === g}
            onClick={() => setGroup(g)}
            className={cn(
              'h-9 shrink-0 rounded-chip border px-3.5 text-sm',
              group === g
                ? 'border-primary bg-primary-subtle font-semibold text-ink'
                : 'border-border text-ink-muted hover:text-ink',
            )}
          >
            {g}
            <span className="ml-1.5 text-xs text-ink-muted tabular">
              {g === 'All'
                ? `${String(unlocked)}/${String(achievements.length)}`
                : `${String(achievements.filter((a) => a.group === g && a.unlocked).length)}/${String(achievements.filter((a) => a.group === g).length)}`}
            </span>
          </button>
        ))}
      </div>

      <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {shown.map((a) => (
          <Card key={a.code} a={a} />
        ))}
      </ul>
    </section>
  )
}
