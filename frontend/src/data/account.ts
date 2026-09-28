import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, get } from '@/lib/api'
import type { LoginEvent, Me, SessionInfo } from '@/lib/types'
import { qk } from './keys'

export function useMe() {
  return useQuery({ queryKey: qk.me, queryFn: ({ signal }) => get<Me>('/me', signal), staleTime: 60_000 })
}

/** After sign-in/out every shopper-scoped query (cart, wallet, purchases...) belongs to someone else. */
function useResetShopperData() {
  const client = useQueryClient()
  return async () => {
    await client.invalidateQueries({
      predicate: (q) =>
        ['me', 'cart', 'wallet', 'quote', 'purchases', 'purchase', 'collection', 'wishlist'].includes(
          String(q.queryKey[0]),
        ),
    })
  }
}

export function useAuthProviders() {
  return useQuery({
    queryKey: ['auth-providers'],
    queryFn: ({ signal }) => get<{ google: boolean }>('/auth/providers', signal),
    staleTime: Infinity,
  })
}

export function useSignIn() {
  const reset = useResetShopperData()
  return useMutation({
    mutationFn: (body: { email: string; password: string }) =>
      api<{ id: string; displayName: string }>('POST', '/auth/login', { body }),
    onSuccess: reset,
  })
}

export function useRegister() {
  const reset = useResetShopperData()
  return useMutation({
    mutationFn: (body: { email: string; password: string; displayName: string }) =>
      api<{ id: string; displayName: string }>('POST', '/auth/register', { body }),
    onSuccess: reset,
  })
}

export function useSignOut() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => api<undefined>('POST', '/auth/logout'),
    onSettled: () => {
      client.clear()
    },
  })
}

export function useSessions(enabled: boolean) {
  return useQuery({
    queryKey: qk.sessions,
    queryFn: ({ signal }) => get<SessionInfo[]>('/me/sessions', signal),
    enabled,
  })
}

export function useLoginEvents(enabled: boolean) {
  return useQuery({
    queryKey: qk.loginEvents,
    queryFn: ({ signal }) => get<LoginEvent[]>('/me/login-events', signal),
    enabled,
  })
}

export function useRevokeSession() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api<undefined>('DELETE', `/me/sessions/${id}`),
    onSuccess: () => client.invalidateQueries({ queryKey: qk.sessions }),
  })
}

export function useRevokeOtherSessions() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => api<{ count: number }>('POST', '/me/sessions/revoke-others'),
    onSuccess: () => client.invalidateQueries({ queryKey: qk.sessions }),
  })
}

export function useChangePassword() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (body: { currentPassword: string; newPassword: string }) =>
      api<undefined>('POST', '/me/password', { body }),
    onSuccess: () => client.invalidateQueries({ queryKey: ['me'] }),
  })
}
