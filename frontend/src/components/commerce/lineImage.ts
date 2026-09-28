import type { CartLine, ProductImage } from '@/lib/types'

/** A cart line shows the configuration it holds: the chosen colour's photo when there is one. */
export function lineImage(line: CartLine): ProductImage | undefined {
  const base = line.product.image
  if (!line.optionImage) return base
  return {
    small: line.optionImage,
    large: line.optionImage,
    width: base?.width ?? 400,
    height: base?.height ?? 400,
    alt: line.optionsLabel ? `${line.product.name}, ${line.optionsLabel}` : (base?.alt ?? line.product.name),
    match: base?.match ?? 'EXACT',
  }
}
