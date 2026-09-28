import { Link } from 'react-router'
import { ProductImage } from '@/components/commerce/ProductImage'
import { Skeleton } from '@/components/ui/Skeleton'
import type { TileItem } from './tileItems'

export function CollectionTile({
  title,
  link,
  items,
}: {
  title: string
  link: { label: string; to: string }
  items?: TileItem[]
}) {
  return (
    <section className="flex flex-col gap-3 rounded-tile border border-border bg-surface p-4">
      <h2 className="text-[17px] font-semibold leading-6">{title}</h2>
      <ul className="grid flex-1 grid-cols-2 gap-2.5">
        {(items ?? Array.from({ length: 4 }, () => undefined)).map((item, i) => (
          <li key={item?.key ?? i} className="min-w-0">
            {item ? (
              <Link
                to={item.to}
                className="flex min-w-0 flex-col gap-[5px] text-ink no-underline hover:text-ink hover:no-underline"
              >
                {item.image ? (
                  <>
                    <span className="tk-img-well flex aspect-square items-center justify-center rounded-control bg-surface-2 p-2">
                      <ProductImage image={item.image} sizes="120px" />
                    </span>
                    <span className="truncate text-xs leading-4">{item.label}</span>
                    <span className="text-[13px] font-semibold tabular">{item.meta}</span>
                  </>
                ) : (
                  <>
                    {/* A stat rather than a product: the value itself sits where a photo would. */}
                    <span
                      className={
                        item.metaTone === 'trust'
                          ? 'flex aspect-square items-center justify-center rounded-control bg-trust-subtle px-2 text-center text-lg font-bold text-trust tabular'
                          : 'flex aspect-square items-center justify-center rounded-control bg-surface-2 px-2 text-center text-lg font-bold tabular'
                      }
                    >
                      {item.meta}
                    </span>
                    <span className="truncate text-xs leading-4">{item.label}</span>
                  </>
                )}
              </Link>
            ) : (
              <div className="flex flex-col gap-1.5">
                <Skeleton className="aspect-square rounded-control" />
                <Skeleton className="h-3 w-4/5" />
              </div>
            )}
          </li>
        ))}
      </ul>
      <Link to={link.to} className="text-[13px] font-semibold">
        {link.label}
      </Link>
    </section>
  )
}
