import { readJson, STORAGE_KEYS, writeJson } from '@/lib/storage'

const MAX = 5

export function getRecentSearches(): string[] {
  return readJson<string[]>(STORAGE_KEYS.recentSearches, [])
}

export function addRecentSearch(q: string): void {
  const trimmed = q.trim()
  if (!trimmed) return
  writeJson(
    STORAGE_KEYS.recentSearches,
    [trimmed, ...getRecentSearches().filter((r) => r !== trimmed)].slice(0, MAX),
  )
}

export function removeRecentSearch(q: string): string[] {
  const next = getRecentSearches().filter((r) => r !== q)
  writeJson(STORAGE_KEYS.recentSearches, next)
  return next
}
