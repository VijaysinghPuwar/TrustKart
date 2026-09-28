/**
 * Per-browser conveniences only (recent searches, recently viewed, compare picks, theme). Anything that
 * matters, such as cart, wallet and purchases, lives on the server. Storage can be unavailable (private
 * mode, blocked cookies), so every access is guarded and failures fall back to defaults.
 */
export function readJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key)
    return raw ? (JSON.parse(raw) as T) : fallback
  } catch {
    return fallback
  }
}

/** Returns false when storage is full or blocked; callers keep their in-memory state either way. */
export function writeString(key: string, value: string): boolean {
  try {
    localStorage.setItem(key, value)
    return true
  } catch {
    return false
  }
}

export function writeJson(key: string, value: unknown): boolean {
  return writeString(key, JSON.stringify(value))
}

export const STORAGE_KEYS = {
  recentSearches: 'tk-recent-searches',
  recentlyViewed: 'tk-recently-viewed',
  historyPaused: 'tk-history-paused',
  compare: 'tk-compare',
  theme: 'tk-theme',
  searchMode: 'tk-search-mode',
} as const
