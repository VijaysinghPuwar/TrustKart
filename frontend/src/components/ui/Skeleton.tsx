import { cn } from '@/lib/cn'

export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden="true" className={cn('animate-pulse rounded bg-surface-2', className)} />
}
