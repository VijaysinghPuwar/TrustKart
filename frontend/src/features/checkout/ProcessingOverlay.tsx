import { Check, Lock } from 'lucide-react'
import { useEffect, useState } from 'react'
import { cn } from '@/lib/cn'

const STAGES = ['Securing checkout', 'Authorizing payment', 'Confirming your order'] as const

/** Shown while an order is placed: stages advance on a timer and the last one completes when the server confirms. */
export function ProcessingOverlay({ done }: { done: boolean }) {
  const [stage, setStage] = useState(0)
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches

  useEffect(() => {
    if (stage >= STAGES.length - 1) return
    const t = window.setTimeout(() => setStage((s) => s + 1), reduced ? 120 : 700)
    return () => window.clearTimeout(t)
  }, [stage, reduced])

  return (
    <div
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="processing-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-[rgb(15_23_42/0.55)] p-4 backdrop-blur-sm"
    >
      <div className="flex w-[min(380px,100%)] flex-col gap-5 rounded-tile bg-surface p-6 text-ink shadow-lg">
        <div className="flex items-center gap-2">
          <Lock className="size-4 text-trust" aria-hidden="true" />
          <h2 id="processing-title" className="font-semibold">
            Processing your order
          </h2>
        </div>
        <ol className="flex flex-col gap-3" aria-live="polite">
          {STAGES.map((label, i) => {
            const complete = i < stage || (done && i === stage && i === STAGES.length - 1)
            const active = i === stage && !complete
            return (
              <li
                key={label}
                className={cn(
                  'flex items-center gap-3 text-sm transition-opacity',
                  i > stage && 'opacity-40',
                )}
              >
                <span
                  className={cn(
                    'flex size-6 items-center justify-center rounded-full',
                    complete ? 'bg-trust text-white' : 'bg-surface-2',
                  )}
                >
                  {complete ? (
                    <Check className="size-4" strokeWidth={3} aria-hidden="true" />
                  ) : active ? (
                    <span
                      className="size-3.5 animate-spin rounded-full border-2 border-primary border-r-transparent"
                      aria-hidden="true"
                    />
                  ) : null}
                </span>
                <span className={active ? 'font-semibold' : undefined}>{label}</span>
              </li>
            )
          })}
        </ol>
        <p className="text-xs text-ink-muted">Please keep this page open.</p>
      </div>
    </div>
  )
}
