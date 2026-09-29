import { Bell } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useMarkAllRead, useMarkRead, useNotifications, useUnreadCount } from '@/data/notifications'
import { cn } from '@/lib/cn'
import { browserAlertsEnabled } from '@/lib/browserNotifications'
import type { AppNotification } from '@/lib/types'

export function timeAgo(iso: string): string {
  const s = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000)
  if (s < 60) return 'Just now'
  if (s < 3600) return `${String(Math.floor(s / 60))} min ago`
  if (s < 86_400) return `${String(Math.floor(s / 3600))} h ago`
  if (s < 7 * 86_400) return `${String(Math.floor(s / 86_400))} d ago`
  return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

export function NotificationRow({ n, onOpen }: { n: AppNotification; onOpen: (n: AppNotification) => void }) {
  return (
    <button
      type="button"
      onClick={() => onOpen(n)}
      className={cn(
        'flex w-full gap-3 rounded-control p-2.5 text-left text-ink hover:bg-surface-2',
        !n.read && 'bg-primary-subtle/60',
      )}
    >
      <span className="tk-img-well flex size-12 shrink-0 items-center justify-center rounded-control bg-surface-2 p-1">
        {n.imageUrl && (
          <img
            src={n.imageUrl}
            alt=""
            width={48}
            height={48}
            loading="lazy"
            decoding="async"
            className="size-full object-contain"
          />
        )}
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex items-center gap-2 text-sm font-semibold">
          {n.title}
          {!n.read && <span className="size-2 rounded-full bg-primary" aria-label="Unread" />}
        </span>
        <span className="line-clamp-2 block text-[13px] text-ink-muted">{n.body}</span>
        <span className="text-xs text-ink-subtle">{timeAgo(n.createdAt)}</span>
      </span>
    </button>
  )
}

/**
 * Header bell: unread badge (polled), a dropdown of recent updates, and, if the shopper opted in on the
 * notifications page, a system notification when new updates arrive while the tab is in the background.
 */
export function NotificationBell() {
  const [open, setOpen] = useState(false)
  const panelId = useId()
  const root = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()
  const { data: unread = 0 } = useUnreadCount()
  const list = useNotifications(open)
  const markRead = useMarkRead()
  const markAll = useMarkAllRead()
  const lastUnread = useRef<number | null>(null)

  // Browser alert when the unread count goes up (never on first load, never for a visible tab).
  useEffect(() => {
    const previous = lastUnread.current
    lastUnread.current = unread
    if (previous === null || unread <= previous) return
    if (document.visibilityState === 'visible' || !browserAlertsEnabled()) return
    const added = unread - previous
    new Notification('TrustKart', {
      body: added === 1 ? 'You have a new order update.' : `You have ${String(added)} new order updates.`,
      icon: '/brand/trustkart-mark-136.webp',
      tag: 'trustkart-orders',
    })
  }, [unread])

  useEffect(() => {
    if (!open) return
    const onDown = (e: PointerEvent) => {
      if (!root.current?.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const openNotification = (n: AppNotification) => {
    if (!n.read) markRead.mutate(n.id)
    setOpen(false)
    if (n.link) void navigate(n.link)
  }

  return (
    <div ref={root} className="relative">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={panelId}
        aria-label={unread > 0 ? `Notifications, ${String(unread)} unread` : 'Notifications'}
        onClick={() => setOpen((v) => !v)}
        className="relative flex size-11 items-center justify-center rounded-chip text-white hover:bg-white/10 sm:size-[46px]"
      >
        <Bell className="size-[22px]" aria-hidden="true" />
        {unread > 0 && (
          <span
            key={unread}
            className="tk-bump absolute right-1 top-1.5 flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-danger px-1 text-[11px] font-bold text-white tabular"
          >
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>
      {open && (
        <div
          id={panelId}
          role="region"
          aria-label="Notifications"
          className="absolute right-0 top-[52px] z-30 flex max-h-[min(560px,75vh)] w-[min(380px,calc(100vw-24px))] flex-col rounded-card border border-border bg-surface text-ink shadow-2xl"
        >
          <div className="flex items-center gap-2 border-b border-border px-4 py-3">
            <h2 className="flex-1 text-base font-bold">Notifications</h2>
            {unread > 0 && (
              <button
                type="button"
                onClick={() => markAll.mutate()}
                className="text-[13px] font-semibold text-primary hover:underline"
              >
                Mark all as read
              </button>
            )}
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto p-1.5">
            {list.isPending ? (
              <p className="p-4 text-sm text-ink-muted">Loading…</p>
            ) : !list.data || list.data.items.length === 0 ? (
              <p className="p-4 text-sm text-ink-muted">
                No updates yet. Order updates and delivery alerts will show up here.
              </p>
            ) : (
              <ul>
                {list.data.items.slice(0, 8).map((n) => (
                  <li key={n.id}>
                    <NotificationRow n={n} onOpen={openNotification} />
                  </li>
                ))}
              </ul>
            )}
          </div>
          <div className="border-t border-border px-4 py-2.5 text-[13px]">
            <Link to="/account/notifications" onClick={() => setOpen(false)}>
              See all notifications and settings
            </Link>
          </div>
        </div>
      )}
    </div>
  )
}
