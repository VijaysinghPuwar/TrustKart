import { useCallback } from 'react'

/**
 * Callback ref that publishes an element's current height as a CSS custom property on <html>, so other fixed layers
 * can stack above it (the compare tray sits on top of the phone purchase bar). A hidden element publishes 0, and
 * the property is removed when the element unmounts.
 */
export function usePublishedHeight(property: `--${string}`) {
  return useCallback(
    (el: HTMLElement | null) => {
      if (!el) return
      const root = document.documentElement.style
      const observer = new ResizeObserver(() => {
        root.setProperty(property, `${String(Math.round(el.getBoundingClientRect().height))}px`)
      })
      observer.observe(el)
      return () => {
        observer.disconnect()
        root.removeProperty(property)
      }
    },
    [property],
  )
}
