import { Link } from 'react-router'
import type { Category } from '@/lib/types'
import categoryImages from './categoryImages.json'

const images = categoryImages as Record<
  string,
  { src: string; width: number; height: number; alt: string } | undefined
>

export function CategoryGrid({ categories }: { categories: Category[] }) {
  return (
    <section aria-labelledby="shop-by-category" className="flex flex-col gap-3.5">
      <h2 id="shop-by-category" className="text-[22px] font-bold leading-[30px] tracking-[-0.01em]">
        Shop by category
      </h2>
      <ul className="grid grid-cols-[repeat(auto-fill,minmax(min(132px,calc(50%-6px)),1fr))] gap-3">
        {categories.map((c) => {
          const img = images[c.slug]
          return (
            <li key={c.slug}>
              <Link
                to={`/c/${c.slug}`}
                className="flex h-full flex-col gap-2 rounded-card border border-border bg-surface p-2.5 text-ink no-underline transition-colors hover:border-primary hover:text-ink hover:no-underline"
              >
                <span className="flex aspect-[4/3] items-center justify-center overflow-hidden rounded-control bg-surface-2">
                  {img && (
                    <img
                      src={img.src}
                      alt=""
                      width={img.width}
                      height={img.height}
                      loading="lazy"
                      decoding="async"
                      className="size-full object-cover"
                    />
                  )}
                </span>
                <span className="text-sm font-medium">{c.name}</span>
                <span className="-mt-1.5 text-xs text-ink-muted">{c.productCount} items</span>
              </Link>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
