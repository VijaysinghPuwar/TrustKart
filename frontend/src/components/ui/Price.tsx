import { formatMoney, type Money } from '@/lib/money'
import { cn } from '@/lib/cn'

interface PriceProps {
  amount: Money
  compareAt?: Money
  size?: 'sm' | 'md' | 'lg' | 'xl'
  className?: string
}

const sizes = {
  sm: 'text-sm',
  md: 'text-lg leading-6',
  lg: 'text-[28px] leading-8',
  xl: 'text-[30px] leading-9',
}

/** Price with an optional struck-through compare-at price. Screen readers hear "was ...". */
export function Price({ amount, compareAt, size = 'md', className }: PriceProps) {
  return (
    <div className={cn('flex flex-wrap items-baseline gap-x-2 tabular', className)}>
      <span className={cn('font-bold', sizes[size])}>{formatMoney(amount)}</span>
      {compareAt && (
        <span
          className={cn(
            'text-ink-muted line-through',
            size === 'md' || size === 'sm' ? 'text-[13px]' : 'text-base',
          )}
        >
          <span className="sr-only">was </span>
          {formatMoney(compareAt)}
        </span>
      )}
    </div>
  )
}
