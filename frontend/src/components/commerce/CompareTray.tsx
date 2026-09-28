import { X } from 'lucide-react'
import { useEffect } from 'react'
import { useLocation } from 'react-router'
import { ButtonLink } from '@/components/ui/Button'
import { useCompareTray, MAX_COMPARE } from '@/state/compare'

/** Floating tray listing up to four picks. Hidden on the compare page itself. */
export function CompareTray() {
  const { picks, remove, clear } = useCompareTray()
  const { pathname } = useLocation()
  const visible = picks.length > 0 && pathname !== '/compare'

  useEffect(() => {
    // Lift toasts above the tray while it is open.
    document.documentElement.style.setProperty('--tk-toast-offset', visible ? '120px' : '24px')
  }, [visible])

  if (!visible) return null
  const hint =
    picks.length < 2 ? 'Add at least one more' : `${String(MAX_COMPARE - picks.length)} more allowed`
  const href = `/compare?${picks.map((p) => `slugs=${encodeURIComponent(p.slug)}`).join('&')}`

  return (
    <section
      aria-label="Compare products"
      className="fixed bottom-5 left-1/2 z-30 flex w-[min(720px,calc(100vw-24px))] -translate-x-1/2 flex-wrap items-center gap-2.5 rounded-tile border border-border bg-surface px-3.5 py-3 shadow-lg"
    >
      <h2 className="text-sm font-semibold">Compare</h2>
      <ul className="flex min-w-0 flex-[1_1_200px] flex-wrap items-center gap-1.5">
        {picks.map((p) => (
          <li
            key={p.slug}
            className="flex h-[30px] max-w-[180px] items-center gap-1 rounded-chip bg-surface-2 pl-2.5 pr-1 text-[13px]"
          >
            <span className="truncate">{p.name}</span>
            <button
              type="button"
              onClick={() => remove(p.slug)}
              className="flex size-6 shrink-0 items-center justify-center rounded text-ink-muted hover:bg-border"
              aria-label={`Remove ${p.name} from compare`}
            >
              <X className="size-3.5" aria-hidden="true" />
            </button>
          </li>
        ))}
        <li className="text-xs text-ink-muted">{hint}</li>
      </ul>
      <button
        type="button"
        onClick={clear}
        className="h-10 px-3 text-sm font-semibold text-ink-muted hover:text-ink"
      >
        Clear
      </button>
      {picks.length >= 2 ? (
        <ButtonLink to={href}>Compare {picks.length}</ButtonLink>
      ) : (
        <button
          type="button"
          disabled
          className="h-10 rounded-button bg-border px-4 text-sm font-semibold text-ink-muted"
        >
          Compare {picks.length}
        </button>
      )}
    </section>
  )
}
