import { ImageOff } from 'lucide-react'
import { useState } from 'react'
import { cn } from '@/lib/cn'
import type { ProductImage as Img } from '@/lib/types'

interface ProductImageProps {
  image?: Img | null
  /** Rendered size hint for the browser's srcset choice. */
  sizes?: string
  priority?: boolean
  className?: string
}

/**
 * Responsive product photo: 400/800 px WebP with intrinsic width/height (no layout shift), lazy below the fold,
 * and a neutral fallback if the file fails to load. Photos keep their aspect ratio and are never cropped.
 */
export function ProductImage({
  image,
  sizes = '(max-width: 640px) 50vw, 220px',
  priority = false,
  className,
}: ProductImageProps) {
  const [failed, setFailed] = useState(false)
  if (!image || failed) {
    return (
      <div
        className={cn('flex size-full flex-col items-center justify-center gap-2 text-ink-subtle', className)}
      >
        <ImageOff className="size-8" aria-hidden="true" />
        <span className="text-xs">Image unavailable</span>
      </div>
    )
  }
  return (
    <img
      src={image.small}
      srcSet={`${image.small} 400w, ${image.large} 800w`}
      sizes={sizes}
      width={image.width}
      height={image.height}
      alt={image.alt}
      loading={priority ? 'eager' : 'lazy'}
      fetchPriority={priority ? 'high' : 'auto'}
      decoding="async"
      onError={() => setFailed(true)}
      className={cn('tk-product-img max-h-full max-w-full object-contain', className)}
    />
  )
}
