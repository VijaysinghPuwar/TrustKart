import type { ReactNode } from 'react'

export function AuthCard({
  title,
  subtitle,
  children,
}: {
  title: string
  subtitle?: ReactNode
  children: ReactNode
}) {
  return (
    <div className="mx-auto flex w-full max-w-[440px] flex-col gap-5 rounded-tile border border-border bg-surface p-6 shadow-sm sm:p-8">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold">{title}</h1>
        {subtitle && <p className="text-sm text-ink-muted">{subtitle}</p>}
      </div>
      {children}
    </div>
  )
}

/** Only same-site relative paths are followed after sign-in (no open redirects). */
export function safeReturnTo(raw: string | null): string {
  if (!raw || !raw.startsWith('/') || raw.startsWith('//') || raw.includes('\\')) return '/'
  return raw
}
