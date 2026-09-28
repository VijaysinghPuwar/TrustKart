import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'

interface CartDrawerValue {
  open: boolean
  show: () => void
  hide: () => void
}

const CartDrawerContext = createContext<CartDrawerValue | null>(null)

export function CartDrawerProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false)
  const value = useMemo(() => ({ open, show: () => setOpen(true), hide: () => setOpen(false) }), [open])
  return <CartDrawerContext.Provider value={value}>{children}</CartDrawerContext.Provider>
}

export function useCartDrawer(): CartDrawerValue {
  const ctx = useContext(CartDrawerContext)
  if (!ctx) throw new Error('useCartDrawer must be used inside CartDrawerProvider')
  return ctx
}
