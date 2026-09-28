import { useState } from 'react'
import { useAddToCart } from '@/data/shopping'
import { ApiError } from '@/lib/api'
import type { OptionSelection, ProductCard } from '@/lib/types'
import { useToast } from '@/components/ui/Toast'

/** Add-to-cart with the design's feedback: brief "Added ✓", a toast, and readable errors. */
export function useAddToCartAction() {
  const add = useAddToCart()
  const { notify } = useToast()
  const [justAdded, setJustAdded] = useState<number | null>(null)

  function addToCart(product: Pick<ProductCard, 'id' | 'name'>, quantity = 1, options?: OptionSelection) {
    add.mutate(
      // Products without options send none, so the request is unchanged for them.
      { productId: product.id, quantity, ...(options && Object.keys(options).length ? { options } : {}) },
      {
        onSuccess: () => {
          setJustAdded(product.id)
          window.setTimeout(() => setJustAdded((id) => (id === product.id ? null : id)), 1400)
          notify(`Added to cart · ${product.name}`)
        },
        onError: (e) =>
          notify(e instanceof ApiError ? e.message : 'Could not add to cart. Please try again.'),
      },
    )
  }

  return { addToCart, justAdded, pendingId: add.isPending ? add.variables.productId : null }
}
