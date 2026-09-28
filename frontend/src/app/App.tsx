import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { RouterProvider } from 'react-router'
import { ToastProvider } from '@/components/ui/Toast'
import { ApiError, onSessionEnded } from '@/lib/api'
import { CartDrawerProvider } from '@/state/cartDrawer'
import { CompareProvider } from '@/state/compare'
import { router } from './router'

function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        // Client errors (404, validation, auth) won't fix themselves; retry only transient failures.
        retry: (count, error) =>
          count < 2 && !(error instanceof ApiError && error.status >= 400 && error.status < 500),
      },
    },
  })
}

export function App() {
  const [client] = useState(createQueryClient)
  useEffect(() => onSessionEnded(() => void client.invalidateQueries({ queryKey: ['me'] })), [client])
  return (
    <QueryClientProvider client={client}>
      <ToastProvider>
        <CompareProvider>
          <CartDrawerProvider>
            <RouterProvider router={router} />
          </CartDrawerProvider>
        </CompareProvider>
      </ToastProvider>
    </QueryClientProvider>
  )
}
