import { X } from 'lucide-react'
import { useEffect, useId, useRef, type ReactNode } from 'react'
import { cn } from '@/lib/cn'

interface DialogProps {
  open: boolean
  onClose: () => void
  title: ReactNode
  description?: ReactNode
  children: ReactNode
  /** 'modal' is centred; 'drawer' slides in from the right; 'sheet' rises from the bottom on small screens. */
  variant?: 'modal' | 'drawer' | 'sheet'
  className?: string
  footer?: ReactNode
  hideClose?: boolean
}

/**
 * Built on the native <dialog> element: showModal() gives focus trapping, Escape to close and an inert
 * background for free, and focus returns to the trigger when it closes.
 */
export function Dialog({
  open,
  onClose,
  title,
  description,
  children,
  variant = 'modal',
  className,
  footer,
  hideClose,
}: DialogProps) {
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const descriptionId = useId()

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  const layout = {
    modal: 'm-auto w-[min(560px,calc(100vw-24px))] max-h-[calc(100dvh-32px)] rounded-tile',
    drawer: 'ml-auto mr-0 my-0 h-dvh max-h-dvh w-[min(420px,100vw)] rounded-none tk-drawer',
    sheet:
      'mb-0 mt-auto mx-auto w-full max-w-[640px] max-h-[88dvh] rounded-t-tile sm:m-auto sm:rounded-tile tk-sheet',
  }[variant]

  return (
    // Keyboard users dismiss with Escape (the native cancel event below); the click handler only adds
    // "click the backdrop to close" for pointer users.
    // eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-noninteractive-element-interactions
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      aria-describedby={description ? descriptionId : undefined}
      onClose={onClose}
      onCancel={(e) => {
        e.preventDefault()
        onClose()
      }}
      onClick={(e) => {
        // A click on the backdrop lands on the <dialog> element itself.
        if (e.target === ref.current) onClose()
      }}
      className={cn(
        'bg-surface p-0 text-ink shadow-lg backdrop:bg-[rgb(15_23_42/0.45)] open:flex open:flex-col',
        layout,
        className,
      )}
    >
      <div className="flex items-start gap-3 border-b border-border px-5 py-4">
        <div className="min-w-0 flex-1">
          <h2 id={titleId} className="text-lg font-semibold leading-7">
            {title}
          </h2>
          {description && (
            <p id={descriptionId} className="mt-0.5 text-sm text-ink-muted">
              {description}
            </p>
          )}
        </div>
        {!hideClose && (
          <button
            type="button"
            onClick={onClose}
            className="-mr-2 flex size-10 shrink-0 items-center justify-center rounded-control text-ink-muted hover:bg-surface-2"
            aria-label="Close"
          >
            <X className="size-5" aria-hidden="true" />
          </button>
        )}
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4">{children}</div>
      {footer && <div className="border-t border-border px-5 py-4">{footer}</div>}
    </dialog>
  )
}
