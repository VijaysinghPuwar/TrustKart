import type { ProductCard } from '@/lib/types'

/** Stock state in words, never colour alone. */
export function stockText(p: Pick<ProductCard, 'stockStatus' | 'stockLeft'>): {
  text: string
  tone: 'muted' | 'warning'
} {
  switch (p.stockStatus) {
    case 'IN_STOCK':
      return { text: 'In stock', tone: 'muted' }
    case 'LOW_STOCK':
      return { text: `Only ${String(p.stockLeft ?? 0)} left in stock`, tone: 'warning' }
    case 'BACKORDER':
      return { text: 'Available on backorder', tone: 'muted' }
    case 'DISCONTINUED':
      return { text: 'Discontinued', tone: 'muted' }
    default:
      return { text: 'Currently unavailable', tone: 'muted' }
  }
}

export function StockLine({ product }: { product: Pick<ProductCard, 'stockStatus' | 'stockLeft'> }) {
  const { text, tone } = stockText(product)
  return (
    <div
      className={
        tone === 'warning'
          ? 'text-[13px] font-medium text-warning'
          : 'text-[13px] leading-[18px] text-ink-muted'
      }
    >
      {text}
    </div>
  )
}
