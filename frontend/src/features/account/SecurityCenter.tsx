import { Laptop, ShieldAlert, ShieldCheck } from 'lucide-react'
import { useState, type SyntheticEvent } from 'react'
import { Badge } from '@/components/ui/Badge'
import { Button, ButtonLink } from '@/components/ui/Button'
import { ErrorState } from '@/components/ui/ErrorState'
import { PageSpinner } from '@/components/ui/PageSpinner'
import { useToast } from '@/components/ui/Toast'
import {
  useChangePassword,
  useLoginEvents,
  useMe,
  useRevokeOtherSessions,
  useRevokeSession,
  useSessions,
} from '@/data/account'
import { ApiError } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'
import { PasswordChecklist, passwordChecks } from '@/features/auth/PasswordChecklist'
import { PasswordField } from '@/features/auth/PasswordField'

const OUTCOME: Record<string, { label: string; tone: 'trust' | 'danger' | 'neutral' | 'warning' }> = {
  SUCCESS: { label: 'Signed in', tone: 'trust' },
  BAD_CREDENTIALS: { label: 'Wrong password', tone: 'warning' },
  LOCKED: { label: 'Locked out', tone: 'danger' },
  DISABLED: { label: 'Account disabled', tone: 'danger' },
  RATE_LIMITED: { label: 'Rate limited', tone: 'warning' },
  REFRESH_REUSE_DETECTED: { label: 'Stolen session blocked', tone: 'danger' },
  LOGOUT: { label: 'Signed out', tone: 'neutral' },
}

const when = (iso: string) =>
  new Date(iso).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' })

export default function SecurityCenter() {
  usePageTitle('Security center')
  const me = useMe()
  const signedIn = me.data?.authenticated === true
  const sessions = useSessions(signedIn)
  const events = useLoginEvents(signedIn)
  const revoke = useRevokeSession()
  const revokeOthers = useRevokeOtherSessions()
  const { notify } = useToast()

  if (me.isPending) return <PageSpinner />
  if (!signedIn) {
    return (
      <div className="flex flex-col items-start gap-3">
        <h1 className="text-[28px] font-bold leading-9">Security center</h1>
        <p className="text-ink-muted">Sign in to see your devices, sign-in history and password settings.</p>
        <ButtonLink to="/signin?returnTo=/account/security">Sign in</ButtonLink>
      </div>
    )
  }
  const failures = events.data?.filter((e) => e.outcome !== 'SUCCESS' && e.outcome !== 'LOGOUT').length ?? 0

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-[28px] font-bold leading-9">Security center</h1>

      <section
        aria-labelledby="status-heading"
        className="flex items-start gap-4 rounded-tile border border-border bg-surface p-5"
      >
        {failures === 0 ? (
          <ShieldCheck className="size-10 shrink-0 text-trust" aria-hidden="true" />
        ) : (
          <ShieldAlert className="size-10 shrink-0 text-warning" aria-hidden="true" />
        )}
        <div className="flex flex-col gap-1">
          <h2 id="status-heading" className="text-lg font-semibold">
            {failures === 0
              ? 'No unusual sign-in activity'
              : `${String(failures)} unsuccessful sign-in attempts recently`}
          </h2>
          <ul className="text-sm text-ink-muted">
            <li>✓ Password stored as an Argon2id hash</li>
            <li>✓ Sessions revocable from any device</li>
            {me.data?.profile?.linkedProviders.includes('google') && (
              <li>✓ Google account linked (OpenID Connect)</li>
            )}
            <li>Two-factor authentication and passkeys: coming in a later release</li>
          </ul>
        </div>
      </section>

      <section aria-labelledby="sessions-heading" className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center gap-3">
          <h2 id="sessions-heading" className="text-[22px] font-bold">
            Active sessions
          </h2>
          {sessions.data && sessions.data.length > 1 && (
            <Button
              variant="danger"
              size="sm"
              className="ml-auto"
              loading={revokeOthers.isPending}
              onClick={() =>
                revokeOthers.mutate(undefined, {
                  onSuccess: (r) => notify(`Signed out of ${String(r.count)} other devices`),
                })
              }
            >
              Sign out of all other devices
            </Button>
          )}
        </div>
        {sessions.isError ? (
          <ErrorState error={sessions.error} />
        ) : (
          <ul className="flex flex-col divide-y divide-border rounded-card border border-border bg-surface">
            {sessions.data?.map((s) => (
              <li key={s.id} className="flex flex-wrap items-center gap-3 p-4">
                <Laptop className="size-5 text-ink-muted" aria-hidden="true" />
                <div className="min-w-0 flex-1">
                  <p className="font-medium">
                    {s.device} {s.current && <Badge tone="trust">This device</Badge>}
                  </p>
                  <p className="text-[13px] text-ink-muted">
                    {s.ipAddress} · signed in {when(s.createdAt)} · last active {when(s.lastUsedAt)}
                  </p>
                </div>
                {!s.current && (
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => revoke.mutate(s.id, { onSuccess: () => notify('Device signed out') })}
                  >
                    Sign out<span className="sr-only"> {s.device}</span>
                  </Button>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section aria-labelledby="history-heading" className="flex flex-col gap-3">
        <h2 id="history-heading" className="text-[22px] font-bold">
          Sign-in activity
        </h2>
        <div
          role="region"
          aria-label="Sign-in activity"
          tabIndex={0}
          className="relative overflow-x-auto rounded-card border border-border bg-surface"
        >
          <table className="w-full min-w-[520px] text-sm">
            <thead>
              <tr className="border-b border-border text-left text-ink-muted">
                <th scope="col" className="px-4 py-2.5 font-medium">
                  When
                </th>
                <th scope="col" className="px-4 py-2.5 font-medium">
                  Event
                </th>
                <th scope="col" className="px-4 py-2.5 font-medium">
                  Device
                </th>
                <th scope="col" className="px-4 py-2.5 font-medium">
                  IP address
                </th>
              </tr>
            </thead>
            <tbody>
              {events.data?.map((e) => {
                const o = OUTCOME[e.outcome] ?? { label: e.outcome, tone: 'neutral' as const }
                return (
                  <tr key={e.id} className="border-b border-border last:border-0">
                    <td className="whitespace-nowrap px-4 py-2.5">{when(e.at)}</td>
                    <td className="px-4 py-2.5">
                      <Badge tone={o.tone}>{o.label}</Badge>
                    </td>
                    <td className="px-4 py-2.5">
                      {e.device}
                      {e.method === 'GOOGLE' && (
                        <span className="block text-xs text-ink-muted">via Google</span>
                      )}
                    </td>
                    <td className="px-4 py-2.5 font-mono text-xs">{e.ipAddress ?? '—'}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </section>

      <ChangePassword email={me.data?.profile?.email ?? ''} />
    </div>
  )
}

function ChangePassword({ email }: { email: string }) {
  const change = useChangePassword()
  const { notify } = useToast()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const error = change.error instanceof ApiError ? change.error : null
  const fieldError = (f: string) => error?.fieldErrors.find((x) => x.field === f)?.message

  function submit(e: SyntheticEvent) {
    e.preventDefault()
    if (!passwordChecks(next, email).every((c) => c.ok)) return
    change.mutate(
      { currentPassword: current, newPassword: next },
      {
        onSuccess: () => {
          setCurrent('')
          setNext('')
          notify('Password changed. Other devices were signed out.')
        },
      },
    )
  }

  return (
    <section
      aria-labelledby="password-heading"
      className="flex max-w-lg flex-col gap-3 rounded-tile border border-border bg-surface p-5"
    >
      <h2 id="password-heading" className="text-lg font-semibold">
        Change password
      </h2>
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        <PasswordField
          label="Current password"
          autoComplete="current-password"
          value={current}
          error={fieldError('currentPassword')}
          onChange={(e) => setCurrent(e.target.value)}
        />
        <PasswordField
          label="New password"
          autoComplete="new-password"
          value={next}
          error={fieldError('newPassword')}
          hint={<PasswordChecklist password={next} email={email} />}
          onChange={(e) => setNext(e.target.value)}
        />
        {error && error.fieldErrors.length === 0 && (
          <p role="alert" className="text-sm text-danger">
            {error.message}
          </p>
        )}
        <Button type="submit" loading={change.isPending} className="self-start">
          Change password
        </Button>
      </form>
    </section>
  )
}
