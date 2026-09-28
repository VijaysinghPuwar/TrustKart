import { useEffect } from 'react'

/** Sets a descriptive document title per page (announced by screen readers on navigation). */
export function usePageTitle(title: string | undefined) {
  useEffect(() => {
    if (title) document.title = `${title} · TrustKart`
  }, [title])
}
