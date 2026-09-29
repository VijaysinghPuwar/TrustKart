import { Plus, Trash2 } from 'lucide-react'
import { useState, type SyntheticEvent } from 'react'
import { ProductCard } from '@/components/commerce/ProductCard'
import { Button } from '@/components/ui/Button'
import { ErrorState } from '@/components/ui/ErrorState'
import { PageSpinner } from '@/components/ui/PageSpinner'
import { useToast } from '@/components/ui/Toast'
import { useAddToCart, useWishlistListMutations, useWishlists } from '@/data/shopping'
import { ApiError } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'

export default function WishlistPage() {
  usePageTitle('Wishlists')
  const lists = useWishlists()
  const { create, remove } = useWishlistListMutations()
  const addToCart = useAddToCart()
  const { notify } = useToast()
  const [name, setName] = useState('')

  if (lists.isPending) return <PageSpinner />
  if (lists.isError) return <ErrorState error={lists.error} />

  function createList(e: SyntheticEvent) {
    e.preventDefault()
    if (!name.trim()) return
    create.mutate(name.trim(), {
      onSuccess: () => {
        setName('')
        notify('List created')
      },
      onError: (err) => notify(err instanceof ApiError ? err.message : 'Could not create the list'),
    })
  }

  async function moveAllToCart(productIds: number[]) {
    let added = 0
    for (const productId of productIds) {
      try {
        await addToCart.mutateAsync({ productId, quantity: 1 })
        added++
      } catch (e) {
        if (!(e instanceof ApiError)) throw e
      }
    }
    notify(`${String(added)} of ${String(productIds.length)} items added to your cart`)
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end gap-4">
        <div className="flex-1">
          <h1 className="text-[32px] font-bold leading-10">Wishlists</h1>
          <p className="text-sm text-ink-muted">Save dream setups now, buy them later.</p>
        </div>
        <form onSubmit={createList} className="flex w-full gap-2 sm:w-auto">
          <label htmlFor="new-list" className="sr-only">
            New list name
          </label>
          <input
            id="new-list"
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={60}
            placeholder="Dream Homelab"
            className="h-10 min-w-0 flex-1 rounded-control border border-border-strong bg-surface px-3 text-sm sm:w-48 sm:flex-none"
          />
          <Button type="submit" variant="secondary" loading={create.isPending}>
            <Plus className="size-4" aria-hidden="true" />
            New list
          </Button>
        </form>
      </div>
      {lists.data.length === 0 && (
        <p className="rounded-card border border-border bg-surface p-6 text-sm text-ink-muted">
          Tap the heart on any product to start your first list.
        </p>
      )}
      {lists.data.map((list) => (
        <section key={list.id} aria-labelledby={`list-${list.id}`} className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center gap-3">
            <h2 id={`list-${list.id}`} className="text-[22px] font-bold">
              {list.name}
            </h2>
            <span className="text-[13px] text-ink-muted">{list.items.length} items</span>
            <div className="ml-auto flex gap-2">
              {list.items.length > 0 && (
                <Button
                  variant="secondary"
                  size="sm"
                  loading={addToCart.isPending}
                  onClick={() => void moveAllToCart(list.items.map((i) => i.id))}
                >
                  Add all to cart
                </Button>
              )}
              {!list.isDefault && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => remove.mutate(list.id)}
                  aria-label={`Delete list ${list.name}`}
                >
                  <Trash2 className="size-4" aria-hidden="true" />
                </Button>
              )}
            </div>
          </div>
          {list.items.length === 0 ? (
            <p className="text-sm text-ink-muted">Nothing here yet.</p>
          ) : (
            <ul className="grid grid-cols-[repeat(auto-fill,minmax(min(210px,calc(50%-5px)),1fr))] gap-2.5 sm:gap-3.5">
              {list.items.map((p) => (
                <li key={p.id}>
                  <ProductCard product={p} />
                </li>
              ))}
            </ul>
          )}
        </section>
      ))}
    </div>
  )
}
