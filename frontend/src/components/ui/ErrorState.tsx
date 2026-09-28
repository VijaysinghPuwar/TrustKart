import type { ReactNode } from 'react'
import { ApiError } from '@/lib/api'
import { Button } from './Button'

/** Says what happened, what to do next, and gives a support reference when the server provided one. */
export function ErrorState({
  error,
  title,
  onRetry,
  children,
}: {
  error: unknown
  title?: string
  onRetry?: () => void
  children?: ReactNode
}) {
  const api = error instanceof ApiError ? error : null
  return (
    <div
      role="alert"
      className="flex flex-col items-start gap-3 rounded-card border border-border bg-surface p-6"
    >
      <h2 className="text-lg font-semibold">{title ?? 'Something went wrong'}</h2>
      <p className="text-sm text-ink-muted">{api?.message ?? 'Please try again in a moment.'}</p>
      {api?.supportReference && (
        <p className="text-xs text-ink-muted">
          Support reference: <span className="font-mono">{api.supportReference}</span>
        </p>
      )}
      {children}
      {onRetry && (
        <Button variant="secondary" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  )
}
