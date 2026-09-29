import { Minus, Plus } from 'lucide-react'
import { cn } from '@/lib/cn'

interface QuantityStepperProps {
  value: number
  max: number
  onChange: (value: number) => void
  label: string
  size?: 'sm' | 'md'
  disabled?: boolean
}

/** − value + with min 1 and max = what the server says can be bought. */
export function QuantityStepper({
  value,
  max,
  onChange,
  label,
  size = 'md',
  disabled,
}: QuantityStepperProps) {
  const h = size === 'sm' ? 'h-10' : 'h-11'
  const w = size === 'sm' ? 'w-10' : 'w-11'
  return (
    <div
      role="group"
      aria-label={label}
      className={cn('inline-flex items-center rounded-control border border-border-strong bg-surface', h)}
    >
      <button
        type="button"
        className={cn(
          'flex h-full items-center justify-center rounded-l-control text-ink hover:bg-surface-2 disabled:text-ink-subtle',
          w,
        )}
        onClick={() => onChange(value - 1)}
        disabled={disabled || value <= 1}
        aria-label="Decrease quantity"
      >
        <Minus className="size-4" aria-hidden="true" />
      </button>
      <output aria-live="polite" className="w-8 text-center text-sm font-semibold tabular">
        {value}
      </output>
      <button
        type="button"
        className={cn(
          'flex h-full items-center justify-center rounded-r-control text-ink hover:bg-surface-2 disabled:text-ink-subtle',
          w,
        )}
        onClick={() => onChange(value + 1)}
        disabled={disabled || value >= max}
        aria-label="Increase quantity"
      >
        <Plus className="size-4" aria-hidden="true" />
      </button>
    </div>
  )
}
