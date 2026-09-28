import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'

interface ToastContextValue {
  notify: (message: string) => void
}

const ToastContext = createContext<ToastContextValue | null>(null)

/**
 * One persistent polite live region; messages replace each other so screen readers announce each once.
 * Bottom-left on desktop, raised above the compare tray when it is open (via --tk-toast-offset).
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [message, setMessage] = useState('')
  const timer = useRef<number | undefined>(undefined)

  const notify = useCallback((next: string) => {
    window.clearTimeout(timer.current)
    setMessage(next)
    timer.current = window.setTimeout(() => setMessage(''), 3200)
  }, [])

  useEffect(() => () => window.clearTimeout(timer.current), [])
  const value = useMemo(() => ({ notify }), [notify])

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        role="status"
        aria-live="polite"
        className="pointer-events-none fixed bottom-[var(--tk-toast-offset,24px)] left-3 z-40 sm:left-6"
      >
        {message && (
          <div className="tk-toast max-w-[calc(100vw-24px)] rounded-button bg-ink px-4 py-3 text-sm text-surface shadow-lg">
            {message}
          </div>
        )}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be used inside ToastProvider')
  return ctx
}
