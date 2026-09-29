import { useIsFetching } from '@tanstack/react-query'
import { Loader2 } from 'lucide-react'
import { useEffect, useState } from 'react'

const SLOW_AFTER_MS = 6_000

/**
 * The hosted API sleeps after a quiet spell and takes up to a minute to start again. When data is still loading
 * after a few seconds, say so instead of leaving the visitor looking at empty placeholders.
 */
export function ServerWakeNotice() {
  const fetching = useIsFetching() > 0
  const [slow, setSlow] = useState(false)

  useEffect(() => {
    if (!fetching) return
    const timer = setTimeout(() => setSlow(true), SLOW_AFTER_MS)
    return () => {
      clearTimeout(timer)
      setSlow(false)
    }
  }, [fetching])

  if (!slow) return null
  return (
    <div role="status" className="border-b border-border bg-surface-2">
      <p className="page-width page-gutter flex items-center gap-2 py-2 text-sm">
        <Loader2 className="size-4 shrink-0 animate-spin text-primary" aria-hidden="true" />
        Starting up the store server. It sleeps when nobody is visiting and is usually back within a minute;
        this page will fill in on its own.
      </p>
    </div>
  )
}
