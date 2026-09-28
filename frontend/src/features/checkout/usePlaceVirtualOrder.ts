import { useRef } from 'react'
import { useNavigate } from 'react-router'
import { usePlaceOrder, newIdempotencyKey, type PlaceOrder } from '@/data/shopping'
import { ApiError } from '@/lib/api'

/**
 * Places a virtual order with one idempotency key per attempt. If the same attempt is retried (double click,
 * flaky network), it reuses the key, so the server returns the original purchase instead of charging twice.
 * A new key is minted only after the attempt settles with a definite answer.
 */
export function usePlaceVirtualOrder() {
  const place = usePlaceOrder()
  const navigate = useNavigate()
  const key = useRef<string>(newIdempotencyKey())

  function submit(order: Omit<PlaceOrder, 'key'>, onError?: (e: unknown) => void) {
    if (place.isPending) return
    place.mutate(
      { ...order, key: key.current },
      {
        onSuccess: (purchase) => {
          key.current = newIdempotencyKey()
          void navigate(`/account/purchases/${purchase.id}?placed=1`)
        },
        onError: (e) => {
          // A definite rejection (400/409/422) means this attempt is finished; network errors keep the key for a retry.
          if (!(e instanceof ApiError && e.code === 'NETWORK_ERROR')) key.current = newIdempotencyKey()
          onError?.(e)
        },
      },
    )
  }
  return { submit, isPending: place.isPending, isSuccess: place.isSuccess, error: place.error }
}
