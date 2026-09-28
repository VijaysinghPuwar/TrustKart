import { formatMoney } from '@/lib/money'
import type { ProductCard } from '@/lib/types'

export interface TileItem {
  key: string
  label: string
  meta: string
  metaTone?: 'default' | 'trust'
  to: string
  image?: ProductCard['image']
}

export function productTileItems(products: ProductCard[]): TileItem[] {
  return products.slice(0, 4).map((p) => ({
    key: p.slug,
    label: p.name,
    meta: formatMoney(p.price),
    to: `/p/${p.slug}`,
    image: p.image,
  }))
}
