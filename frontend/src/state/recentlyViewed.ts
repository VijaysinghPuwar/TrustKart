import { useSyncExternalStore } from 'react'
import { readJson, STORAGE_KEYS, writeJson } from '@/lib/storage'

/** Recently viewed product ids, kept only in this browser. Shoppers can clear it or pause it. */
const MAX = 12
const listeners = new Set<() => void>()
let cache: number[] = readJson<number[]>(STORAGE_KEYS.recentlyViewed, [])

function emit() {
  listeners.forEach((l) => l())
}

export function recordView(productId: number) {
  if (readJson<boolean>(STORAGE_KEYS.historyPaused, false)) return
  cache = [productId, ...cache.filter((id) => id !== productId)].slice(0, MAX)
  writeJson(STORAGE_KEYS.recentlyViewed, cache)
  emit()
}

export function clearRecentlyViewed() {
  cache = []
  writeJson(STORAGE_KEYS.recentlyViewed, cache)
  emit()
}

export function useRecentlyViewed(): { ids: number[]; clear: () => void } {
  const ids = useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    () => cache,
  )
  return { ids, clear: clearRecentlyViewed }
}
