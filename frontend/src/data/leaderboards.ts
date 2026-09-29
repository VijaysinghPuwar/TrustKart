import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, get } from '@/lib/api'
import type { LeaderboardBoard, LeaderboardProfile, LeaderboardStanding } from '@/lib/types'
import { qk } from './keys'

export type BoardType = 'monthly' | 'all-time'

export function useLeaderboard(type: BoardType) {
  return useQuery({
    queryKey: qk.leaderboard(type),
    queryFn: ({ signal }) => get<LeaderboardBoard>(`/leaderboards/${type}`, signal),
    placeholderData: keepPreviousData,
    staleTime: 30_000,
  })
}

/** The signed-in user's own position, even outside the public top lists. */
export function useLeaderboardStanding(enabled: boolean) {
  return useQuery({
    queryKey: qk.leaderboardMe,
    queryFn: ({ signal }) => get<LeaderboardStanding>('/leaderboards/me', signal),
    enabled,
    staleTime: 30_000,
  })
}

export function useLeaderboardProfile(enabled: boolean) {
  return useQuery({
    queryKey: qk.leaderboardProfile,
    queryFn: ({ signal }) => get<LeaderboardProfile>('/leaderboards/profile', signal),
    enabled,
  })
}

export function useUpdateLeaderboardProfile() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (profile: LeaderboardProfile) =>
      api<LeaderboardProfile>('PUT', '/leaderboards/profile', { body: profile }),
    onSuccess: (profile) => {
      client.setQueryData(qk.leaderboardProfile, profile)
      void client.invalidateQueries({ queryKey: ['leaderboard'] })
    },
  })
}
