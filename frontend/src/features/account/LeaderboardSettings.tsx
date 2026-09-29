import { useState } from 'react'
import { Link } from 'react-router'
import { Button } from '@/components/ui/Button'
import { PageSpinner } from '@/components/ui/PageSpinner'
import { useToast } from '@/components/ui/Toast'
import { useMe } from '@/data/account'
import { useLeaderboardProfile, useUpdateLeaderboardProfile } from '@/data/leaderboards'
import { ApiError } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'

/**
 * Public leaderboard identity. Hidden by default: until the user opts in they are ranked as "Anonymous collector".
 * The public name is separate from the account name and is never the email.
 */
export default function LeaderboardSettings() {
  usePageTitle('Leaderboard profile')
  const { data: me } = useMe()
  const signedIn = Boolean(me?.authenticated)
  const profile = useLeaderboardProfile(signedIn)
  const update = useUpdateLeaderboardProfile()
  const { notify } = useToast()
  const [draft, setDraft] = useState<{ displayName: string; visible: boolean; showAvatar: boolean } | null>(
    null,
  )

  if (!signedIn) {
    return (
      <p className="rounded-card border border-border bg-surface p-4 text-sm">
        <Link to="/signin?returnTo=/account/rankings">Sign in</Link> to choose how you appear on the
        leaderboards.
      </p>
    )
  }
  if (profile.isPending) return <PageSpinner />
  const current = draft ?? profile.data ?? { displayName: '', visible: false, showAvatar: false }
  const error = update.error instanceof ApiError ? update.error : null
  const nameError = error?.fieldErrors.find((f) => f.field === 'displayName')?.message
  const hasPhoto = Boolean(me?.profile?.avatarUrl)

  return (
    <div className="flex max-w-xl flex-col gap-5">
      <div>
        <h1 className="text-[28px] font-bold leading-9">Leaderboard profile</h1>
        <p className="text-sm text-ink-muted">
          Choose how you appear on <Link to="/rankings">Rankings</Link>. Your email and account details are
          never shown.
        </p>
      </div>
      <form
        className="flex flex-col gap-4 rounded-card border border-border bg-surface p-5"
        onSubmit={(e) => {
          e.preventDefault()
          update.mutate(current, {
            onSuccess: () => {
              setDraft(null)
              notify('Leaderboard profile saved')
            },
          })
        }}
      >
        <label className="flex flex-col gap-1.5 text-sm">
          <span className="font-semibold">Public name</span>
          <input
            value={current.displayName}
            onChange={(e) => setDraft({ ...current, displayName: e.target.value })}
            maxLength={20}
            autoComplete="off"
            aria-invalid={Boolean(nameError)}
            aria-describedby="name-hint"
            className="h-11 rounded-control border border-border bg-surface px-3 text-base"
          />
          <span id="name-hint" className={nameError ? 'text-danger' : 'text-ink-muted'}>
            {nameError ?? '3 to 20 letters, numbers or underscores.'}
          </span>
        </label>
        <label className="flex cursor-pointer items-start gap-3">
          <input
            type="checkbox"
            role="switch"
            checked={current.visible}
            onChange={(e) => setDraft({ ...current, visible: e.target.checked })}
            className="mt-0.5 size-5 accent-[var(--color-primary)]"
          />
          <span className="text-sm">
            <span className="block font-semibold">Show me on public leaderboards</span>
            <span className="block text-ink-muted">
              When off, you are still ranked but appear as “Anonymous collector”. You can always see your own
              rank.
            </span>
          </span>
        </label>
        {hasPhoto && (
          <label className="flex cursor-pointer items-start gap-3">
            <input
              type="checkbox"
              role="switch"
              checked={current.showAvatar}
              disabled={!current.visible}
              onChange={(e) => setDraft({ ...current, showAvatar: e.target.checked })}
              className="mt-0.5 size-5 accent-[var(--color-primary)]"
            />
            <span className="text-sm">
              <span className="block font-semibold">Use my profile photo</span>
              <span className="block text-ink-muted">Otherwise your initials are shown.</span>
            </span>
          </label>
        )}
        {error && !nameError && <p className="text-sm text-danger">{error.message}</p>}
        <div>
          <Button type="submit" loading={update.isPending} disabled={!draft}>
            Save
          </Button>
        </div>
      </form>
    </div>
  )
}
