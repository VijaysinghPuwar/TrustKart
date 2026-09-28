import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, get } from '@/lib/api'
import type { NotificationPage, NotificationPreferences } from '@/lib/types'
import { qk } from './keys'

/** Order milestones happen on the server's clock, so the bell polls; a minute is plenty for delivery updates. */
const POLL_MS = 60_000

export function useUnreadCount() {
  return useQuery({
    queryKey: qk.notificationCount,
    queryFn: ({ signal }) => get<{ count: number }>('/notifications/unread-count', signal),
    refetchInterval: POLL_MS,
    refetchOnWindowFocus: true,
    select: (d) => d.count,
  })
}

export function useNotifications(enabled = true) {
  return useQuery({
    queryKey: qk.notifications,
    queryFn: ({ signal }) => get<NotificationPage>('/notifications?size=30', signal),
    enabled,
  })
}

function useRefreshNotifications() {
  const client = useQueryClient()
  return () => client.invalidateQueries({ queryKey: ['notifications'] })
}

export function useMarkRead() {
  const refresh = useRefreshNotifications()
  return useMutation({
    mutationFn: (id: string) => api<undefined>('POST', `/notifications/${id}/read`),
    onSuccess: refresh,
  })
}

export function useMarkAllRead() {
  const refresh = useRefreshNotifications()
  return useMutation({
    mutationFn: () => api<undefined>('POST', '/notifications/read-all'),
    onSuccess: refresh,
  })
}

export function useNotificationPreferences() {
  return useQuery({
    queryKey: qk.notificationPrefs,
    queryFn: ({ signal }) => get<NotificationPreferences>('/notifications/preferences', signal),
  })
}

export function useUpdateNotificationPreferences() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (prefs: NotificationPreferences) =>
      api<NotificationPreferences>('PUT', '/notifications/preferences', { body: prefs }),
    onSuccess: (prefs) => {
      client.setQueryData(qk.notificationPrefs, prefs)
      void client.invalidateQueries({ queryKey: qk.notifications })
    },
  })
}
