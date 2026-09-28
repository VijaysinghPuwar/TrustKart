import { useCallback, useSyncExternalStore } from 'react'
import { STORAGE_KEYS, writeString } from '@/lib/storage'

type Theme = 'light' | 'dark'

const listeners = new Set<() => void>()

function current(): Theme {
  return document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light'
}

/** Mirrors public/theme-init.js, which applies the saved or system theme before first paint. */
export function useTheme(): { theme: Theme; toggle: () => void } {
  const theme = useSyncExternalStore((l) => {
    listeners.add(l)
    return () => listeners.delete(l)
  }, current)
  const toggle = useCallback(() => {
    const next: Theme = current() === 'dark' ? 'light' : 'dark'
    document.documentElement.setAttribute('data-theme', next)
    writeString(STORAGE_KEYS.theme, next)
    listeners.forEach((l) => l())
  }, [])
  return { theme, toggle }
}
