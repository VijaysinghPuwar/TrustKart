import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, get, newIdempotencyKey, queryString } from '@/lib/api'
import type {
  AddressInput,
  Cart,
  SavedAddress,
  CollectionView,
  DeliveryPreset,
  Page,
  Purchase,
  PurchaseSummary,
  Quote,
  SimulationAddress,
  Wallet,
  WalletMode,
  WalletTransaction,
  WishlistList,
} from '@/lib/types'
import { qk } from './keys'

// ---- Cart ------------------------------------------------------------------------------------------------

export function useCart() {
  return useQuery({ queryKey: qk.cart, queryFn: ({ signal }) => get<Cart>('/cart', signal) })
}

function useCartMutation<V>(fn: (vars: V) => Promise<Cart>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: (cart) => {
      client.setQueryData(qk.cart, cart)
      void client.invalidateQueries({ queryKey: ['quote'] })
    },
  })
}

export const useAddToCart = () =>
  useCartMutation((v: { productId: number; quantity: number }) =>
    api<Cart>('POST', '/cart/items', { body: v }),
  )

export const useUpdateCartLine = () =>
  useCartMutation((v: { id: string; quantity?: number; savedForLater?: boolean }) =>
    api<Cart>('PATCH', `/cart/items/${v.id}`, {
      body: { quantity: v.quantity, savedForLater: v.savedForLater },
    }),
  )

export const useRemoveCartLine = () =>
  useCartMutation((id: string) => api<Cart>('DELETE', `/cart/items/${id}`))

export function useMoveToWishlist() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api<Cart>('POST', `/cart/items/${id}/move-to-wishlist`),
    onSuccess: (cart) => {
      client.setQueryData(qk.cart, cart)
      void client.invalidateQueries({ queryKey: ['wishlist'] })
    },
  })
}

// ---- Wishlist --------------------------------------------------------------------------------------------

export function useWishlistIds() {
  return useQuery({
    queryKey: qk.wishlistIds,
    queryFn: ({ signal }) => get<number[]>('/wishlist/ids', signal),
  })
}

export function useWishlists() {
  return useQuery({
    queryKey: qk.wishlist,
    queryFn: ({ signal }) => get<WishlistList[]>('/wishlist', signal),
  })
}

export function useToggleWishlist() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ productId, saved, listId }: { productId: number; saved: boolean; listId?: string }) =>
      saved
        ? api<undefined>('DELETE', `/wishlist/items/${String(productId)}${queryString({ listId })}`)
        : api<undefined>('POST', '/wishlist/items', { body: { productId, listId } }),
    onMutate: async ({ productId, saved }) => {
      await client.cancelQueries({ queryKey: qk.wishlistIds })
      const previous = client.getQueryData<number[]>(qk.wishlistIds)
      client.setQueryData<number[]>(qk.wishlistIds, (ids = []) =>
        saved ? ids.filter((i) => i !== productId) : [...ids, productId],
      )
      return { previous }
    },
    onError: (_e, _v, context) => client.setQueryData(qk.wishlistIds, context?.previous),
    onSettled: () => client.invalidateQueries({ queryKey: ['wishlist'] }),
  })
}

export function useWishlistListMutations() {
  const client = useQueryClient()
  const refresh = () => client.invalidateQueries({ queryKey: ['wishlist'] })
  return {
    create: useMutation({
      mutationFn: (name: string) => api<{ id: string }>('POST', '/wishlist/lists', { body: { name } }),
      onSuccess: refresh,
    }),
    rename: useMutation({
      mutationFn: (v: { id: string; name: string }) =>
        api<undefined>('PATCH', `/wishlist/lists/${v.id}`, { body: { name: v.name } }),
      onSuccess: refresh,
    }),
    remove: useMutation({
      mutationFn: (id: string) => api<undefined>('DELETE', `/wishlist/lists/${id}`),
      onSuccess: refresh,
    }),
  }
}

// ---- Wallet ----------------------------------------------------------------------------------------------

export function useWallet() {
  return useQuery({ queryKey: qk.wallet, queryFn: ({ signal }) => get<Wallet>('/wallet', signal) })
}

function useWalletMutation<V>(fn: (vars: V) => Promise<Wallet>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: (wallet) => {
      client.setQueryData(qk.wallet, wallet)
      void client.invalidateQueries({ queryKey: ['wallet', 'transactions'] })
      void client.invalidateQueries({ queryKey: ['quote'] })
    },
  })
}

/** The key is created per dialog submission by the caller, so a retried click can't add funds twice. */
export const useAddFunds = () =>
  useWalletMutation((v: { amount: string; key: string }) =>
    api<Wallet>('POST', '/wallet/credits', { body: { amount: v.amount }, idempotencyKey: v.key }),
  )

export const useSetWalletMode = () =>
  useWalletMutation((mode: WalletMode) => api<Wallet>('PUT', '/wallet/mode', { body: { mode } }))

export function useWalletTransactions(page: number) {
  return useQuery({
    queryKey: qk.walletTx(page),
    queryFn: ({ signal }) =>
      get<{ items: WalletTransaction[]; page: number; size: number; totalItems: number }>(
        `/wallet/transactions${queryString({ page, size: 20 })}`,
        signal,
      ),
    placeholderData: keepPreviousData,
  })
}

// ---- Checkout & purchases --------------------------------------------------------------------------------

export function useQuote(instant?: { productId: number; quantity: number }) {
  const qs = instant ? queryString({ productId: instant.productId, quantity: instant.quantity }) : ''
  return useQuery({
    queryKey: qk.quote(instant ? `${String(instant.productId)}x${String(instant.quantity)}` : undefined),
    queryFn: ({ signal }) => get<Quote>(`/checkout/quote${qs}`, signal),
  })
}

export interface PlaceOrder {
  deliveryPreset: DeliveryPreset
  simulationAddress?: SimulationAddress
  addressId?: string
  expectedTotal: string
  instant?: { productId: number; quantity: number }
  key: string
}

export function usePlaceOrder() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ key, ...body }: PlaceOrder) =>
      api<Purchase>('POST', '/purchases', { body, idempotencyKey: key }),
    onSuccess: (purchase) => {
      client.setQueryData(qk.purchase(purchase.id), purchase)
      void client.invalidateQueries({
        predicate: (q) =>
          ['cart', 'wallet', 'quote', 'purchases', 'collection', 'home', 'product', 'search'].includes(
            String(q.queryKey[0]),
          ),
      })
    },
  })
}

export { newIdempotencyKey }

export function usePurchases(page: number) {
  return useQuery({
    queryKey: qk.purchases(page),
    queryFn: ({ signal }) =>
      get<Page<PurchaseSummary>>(`/purchases${queryString({ page, size: 10 })}`, signal),
    placeholderData: keepPreviousData,
  })
}

export function usePurchase(id: string) {
  return useQuery({
    queryKey: qk.purchase(id),
    queryFn: ({ signal }) => get<Purchase>(`/purchases/${id}`, signal),
  })
}

export function useRefund() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api<Purchase>('POST', `/purchases/${id}/refund`),
    onSuccess: (purchase) => {
      client.setQueryData(qk.purchase(purchase.id), purchase)
      void client.invalidateQueries({
        predicate: (q) =>
          ['wallet', 'purchases', 'collection', 'quote', 'notifications'].includes(String(q.queryKey[0])),
      })
    },
  })
}

export function useCollection() {
  return useQuery({
    queryKey: qk.collection,
    queryFn: ({ signal }) => get<CollectionView>('/collection', signal),
  })
}

// ---- Addresses -------------------------------------------------------------------------------------------

export function useAddresses() {
  return useQuery({
    queryKey: qk.addresses,
    queryFn: ({ signal }) => get<SavedAddress[]>('/addresses', signal),
  })
}

/** Lets checkout hold "Continue" while a new address is still being saved. */
export const ADDRESS_CREATE = ['addresses', 'create'] as const

export function useAddressMutations() {
  const client = useQueryClient()
  const refresh = () => client.invalidateQueries({ queryKey: qk.addresses })
  return {
    create: useMutation({
      mutationKey: ADDRESS_CREATE,
      mutationFn: (v: { input: AddressInput; makeDefault?: boolean }) =>
        api<SavedAddress>('POST', `/addresses${queryString({ makeDefault: v.makeDefault })}`, {
          body: v.input,
        }),
      onSuccess: refresh,
    }),
    update: useMutation({
      mutationFn: (v: { id: string; input: AddressInput }) =>
        api<SavedAddress>('PUT', `/addresses/${v.id}`, { body: v.input }),
      onSuccess: refresh,
    }),
    remove: useMutation({
      mutationFn: (id: string) => api<undefined>('DELETE', `/addresses/${id}`),
      onSuccess: refresh,
    }),
    makeDefault: useMutation({
      mutationFn: (id: string) => api<SavedAddress>('POST', `/addresses/${id}/default`),
      onSuccess: refresh,
    }),
  }
}
