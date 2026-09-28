import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'

type Tone = 'trust' | 'deal' | 'info' | 'warning' | 'neutral' | 'danger'

const tones: Record<Tone, string> = {
  trust: 'bg-trust-subtle text-trust',
  deal: 'bg-accent text-on-accent',
  info: 'bg-primary-subtle text-primary-hover',
  warning: 'bg-warning-subtle text-warning',
  neutral: 'border border-border bg-surface text-ink-muted',
  danger: 'bg-danger-subtle text-danger',
}

export function Badge({
  tone = 'neutral',
  children,
  className,
}: {
  tone?: Tone
  children: ReactNode
  className?: string
}) {
  return (
    <span
      className={cn(
        'inline-flex h-[22px] items-center whitespace-nowrap rounded-badge px-[7px] text-[11px] font-semibold',
        tones[tone],
        className,
      )}
    >
      {children}
    </span>
  )
}
