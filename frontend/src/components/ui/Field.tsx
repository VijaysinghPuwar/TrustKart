import { AlertCircle } from 'lucide-react'
import { useId, type InputHTMLAttributes, type ReactNode } from 'react'
import { cn } from '@/lib/cn'

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  error?: string
  hint?: ReactNode
  trailing?: ReactNode
}

/** Labelled input. Labels are always visible; errors are tied to the input with aria-describedby. */
export function Field({ label, error, hint, trailing, className, id, ...rest }: FieldProps) {
  const autoId = useId()
  const inputId = id ?? autoId
  const hintId = `${inputId}-hint`
  const errorId = `${inputId}-error`
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ') || undefined
  return (
    <div className={cn('flex flex-col gap-1.5', className)}>
      <label htmlFor={inputId} className="text-sm font-medium">
        {label}
      </label>
      <div className="relative">
        <input
          id={inputId}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={cn(
            'h-11 w-full rounded-control border bg-surface px-3 text-[15px] text-ink outline-none transition-shadow',
            'focus:border-primary focus:shadow-[0_0_0_3px_var(--tk-primary-subtle)]',
            error ? 'border-danger' : 'border-border-strong',
            trailing ? 'pr-12' : '',
          )}
          {...rest}
        />
        {trailing && <div className="absolute inset-y-0 right-1 flex items-center">{trailing}</div>}
      </div>
      {hint && (
        <div id={hintId} className="text-[13px] text-ink-muted">
          {hint}
        </div>
      )}
      {error && (
        <p id={errorId} className="flex items-start gap-1.5 text-[13px] text-danger">
          <AlertCircle className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
          {error}
        </p>
      )}
    </div>
  )
}
