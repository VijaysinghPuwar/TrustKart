import { Heart } from 'lucide-react'
import { useToggleWishlist, useWishlistIds } from '@/data/shopping'
import { useToast } from '@/components/ui/Toast'
import { cn } from '@/lib/cn'

export function WishlistButton({
  productId,
  name,
  className,
}: {
  productId: number
  name: string
  className?: string
}) {
  const { data: ids = [] } = useWishlistIds()
  const toggle = useToggleWishlist()
  const { notify } = useToast()
  const saved = ids.includes(productId)
  return (
    <button
      type="button"
      aria-pressed={saved}
      aria-label={saved ? `Remove ${name} from wishlist` : `Save ${name} to wishlist`}
      onClick={() =>
        toggle.mutate(
          { productId, saved },
          { onSuccess: () => notify(saved ? 'Removed from wishlist' : 'Saved to your wishlist') },
        )
      }
      className={cn(
        'flex size-10 items-center justify-center rounded-control border border-border bg-surface transition-colors hover:border-border-strong',
        saved ? 'text-danger' : 'text-ink-muted',
        className,
      )}
    >
      <Heart className="size-[18px]" fill={saved ? 'currentColor' : 'none'} aria-hidden="true" />
    </button>
  )
}
