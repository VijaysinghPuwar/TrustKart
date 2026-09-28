import { useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { usePlaceOrder, newIdempotencyKey, type PlaceOrder } from '@/data/shopping'
import { ApiError } from '@/lib/api'

const MIN_PROCESSING_MS = 2300

/**
 * Places an order with one idempotency key per attempt. A retried attempt (double click, flaky network) reuses
 * the key, so the server returns the original order instead of charging twice. The processing sequence stays
 * on screen for a minimum time so the stages read naturally, then the confirmation opens.
 */
export function usePlaceVirtualOrder() {
  const place = usePlaceOrder()
  const navigate = useNavigate()
  const key = useRef<string>(newIdempotencyKey())
  const [processing, setProcessing] = useState(false)
  const [confirmed, setConfirmed] = useState(false)

  function submit(order: Omit<PlaceOrder, 'key'>, onError?: (e: unknown) => void) {
    if (place.isPending || processing) return
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const started = Date.now()
    setProcessing(true)
    place.mutate(
      { ...order, key: key.current },
      {
        onSuccess: (purchase) => {
          key.current = newIdempotencyKey()
          setConfirmed(true)
          const wait = Math.max(0, (reduced ? 400 : MIN_PROCESSING_MS) - (Date.now() - started))
          window.setTimeout(() => void navigate(`/account/purchases/${purchase.id}?placed=1`), wait + 350)
        },
        onError: (e) => {
          // A definite rejection (400/409/422) ends this attempt; a network error keeps the key for a retry.
          if (!(e instanceof ApiError && e.code === 'NETWORK_ERROR')) key.current = newIdempotencyKey()
          setProcessing(false)
          onError?.(e)
        },
      },
    )
  }
  return {
    submit,
    processing,
    confirmed,
    isPending: place.isPending || processing,
    isSuccess: place.isSuccess,
    error: place.error,
  }
}
