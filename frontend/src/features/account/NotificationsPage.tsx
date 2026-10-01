import { useState } from 'react'
import { useNavigate } from 'react-router'
import { NotificationRow } from '@/components/layout/NotificationBell'
import { Button } from '@/components/ui/Button'
import { ErrorState } from '@/components/ui/ErrorState'
import { PageSpinner } from '@/components/ui/PageSpinner'
import { Pagination } from '@/features/search/Pagination'
import {
  useMarkAllRead,
  useMarkRead,
  NOTIFICATIONS_PER_PAGE,
  useNotificationPage,
  useNotificationPreferences,
  useUpdateNotificationPreferences,
} from '@/data/notifications'
import { browserAlertsEnabled, browserAlertsSupported, setBrowserAlerts } from '@/lib/browserNotifications'
import type { AppNotification, NotificationPreferences } from '@/lib/types'
import { usePageTitle } from '@/lib/usePageTitle'

function Toggle({
  label,
  hint,
  checked,
  disabled,
  onChange,
}: {
  label: string
  hint: string
  checked: boolean
  disabled?: boolean
  onChange: (v: boolean) => void
}) {
  return (
    <label className="flex cursor-pointer items-start gap-3 py-3">
      <input
        type="checkbox"
        role="switch"
        checked={checked}
        disabled={disabled}
        onChange={(e) => onChange(e.target.checked)}
        className="mt-0.5 size-5 accent-[var(--color-primary)]"
      />
      <span>
        <span className="block text-sm font-semibold">{label}</span>
        <span className="block text-[13px] text-ink-muted">{hint}</span>
      </span>
    </label>
  )
}

export default function NotificationsPage() {
  usePageTitle('Notifications')
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const list = useNotificationPage(page)
  const prefs = useNotificationPreferences()
  const update = useUpdateNotificationPreferences()
  const markRead = useMarkRead()
  const markAll = useMarkAllRead()
  const [browser, setBrowser] = useState(browserAlertsEnabled)
  const [browserNote, setBrowserNote] = useState<string | null>(null)

  if (list.isPending || prefs.isPending) return <PageSpinner />
  if (list.isError) return <ErrorState error={list.error} />
  if (prefs.isError) return <ErrorState error={prefs.error} />

  const totalPages = Math.ceil(list.data.totalItems / NOTIFICATIONS_PER_PAGE)
  // Retention can shrink the history while someone sits on its last page; follow it back.
  if (totalPages > 0 && page >= totalPages) setPage(totalPages - 1)
  const setPref = (patch: Partial<NotificationPreferences>) => update.mutate({ ...prefs.data, ...patch })
  const open = (n: AppNotification) => {
    if (!n.read) markRead.mutate(n.id)
    if (n.link) void navigate(n.link)
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end gap-3">
        <div className="flex-1">
          <h1 className="text-[28px] font-bold leading-9">Notifications</h1>
          <p className="text-sm text-ink-muted">Order confirmations, shipping and delivery updates.</p>
        </div>
        {list.data.unreadCount > 0 && (
          <Button variant="secondary" size="sm" onClick={() => markAll.mutate()} loading={markAll.isPending}>
            Mark all as read
          </Button>
        )}
      </div>

      <section className="rounded-card border border-border bg-surface p-2">
        {list.data.items.length === 0 ? (
          <p className="p-4 text-sm text-ink-muted">
            No updates yet. When you place an order, you’ll see its progress here.
          </p>
        ) : (
          <ul aria-busy={list.isPlaceholderData}>
            {list.data.items.map((n) => (
              <li key={n.id}>
                <NotificationRow n={n} onOpen={open} />
              </li>
            ))}
          </ul>
        )}
      </section>
      <Pagination page={page} totalPages={totalPages} onPage={setPage} />

      <section
        aria-labelledby="notify-settings"
        className="divide-y divide-border rounded-card border border-border bg-surface px-5 py-2"
      >
        <h2 id="notify-settings" className="py-3 text-lg font-bold">
          Settings
        </h2>
        <Toggle
          label="Order updates"
          hint="Order confirmations, shipping, cancellations and refunds."
          checked={prefs.data.orderUpdates}
          disabled={update.isPending}
          onChange={(v) => setPref({ orderUpdates: v })}
        />
        <Toggle
          label="Delivery updates"
          hint="Out for delivery and delivered."
          checked={prefs.data.deliveryUpdates}
          disabled={update.isPending}
          onChange={(v) => setPref({ deliveryUpdates: v })}
        />
        <Toggle
          label="Rank updates"
          hint="Reaching a new tier on the leaderboards, and your result when a month ends."
          checked={prefs.data.leaderboardUpdates}
          disabled={update.isPending}
          onChange={(v) => setPref({ leaderboardUpdates: v })}
        />
        {browserAlertsSupported() && (
          <div>
            <Toggle
              label="Browser alerts"
              hint="Show a system notification for new updates while TrustKart is in a background tab. Saved in this browser only."
              checked={browser}
              onChange={(v) => {
                void setBrowserAlerts(v).then((on) => {
                  setBrowser(on)
                  setBrowserNote(
                    v && !on
                      ? 'Your browser blocked notifications. Allow them in site settings to turn this on.'
                      : null,
                  )
                })
              }}
            />
            {browserNote && <p className="pb-3 text-[13px] text-danger">{browserNote}</p>}
          </div>
        )}
      </section>
    </div>
  )
}
