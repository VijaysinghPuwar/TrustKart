import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { readJson, STORAGE_KEYS, writeJson } from '@/lib/storage'

export const MAX_COMPARE = 4

export interface ComparePick {
  slug: string
  name: string
}

interface CompareContextValue {
  picks: ComparePick[]
  has: (slug: string) => boolean
  /** Returns false when the tray is already full. */
  toggle: (pick: ComparePick) => boolean
  remove: (slug: string) => void
  clear: () => void
}

const CompareContext = createContext<CompareContextValue | null>(null)

export function CompareProvider({ children }: { children: ReactNode }) {
  const [picks, setPicks] = useState<ComparePick[]>(() =>
    readJson<ComparePick[]>(STORAGE_KEYS.compare, []).slice(0, MAX_COMPARE),
  )

  const save = useCallback((next: ComparePick[]) => {
    setPicks(next)
    writeJson(STORAGE_KEYS.compare, next)
  }, [])

  const value = useMemo<CompareContextValue>(
    () => ({
      picks,
      has: (slug) => picks.some((p) => p.slug === slug),
      toggle: (pick) => {
        if (picks.some((p) => p.slug === pick.slug)) {
          save(picks.filter((p) => p.slug !== pick.slug))
          return true
        }
        if (picks.length >= MAX_COMPARE) return false
        save([...picks, pick])
        return true
      },
      remove: (slug) => save(picks.filter((p) => p.slug !== slug)),
      clear: () => save([]),
    }),
    [picks, save],
  )
  return <CompareContext.Provider value={value}>{children}</CompareContext.Provider>
}

export function useCompareTray(): CompareContextValue {
  const ctx = useContext(CompareContext)
  if (!ctx) throw new Error('useCompareTray must be used inside CompareProvider')
  return ctx
}
