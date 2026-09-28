import { Eye, EyeOff } from 'lucide-react'
import { useState, type InputHTMLAttributes, type ReactNode } from 'react'
import { Field } from '@/components/ui/Field'

export function PasswordField(
  props: InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string; hint?: ReactNode },
) {
  const [visible, setVisible] = useState(false)
  return (
    <Field
      {...props}
      type={visible ? 'text' : 'password'}
      trailing={
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? 'Hide password' : 'Show password'}
          aria-pressed={visible}
          className="flex size-10 items-center justify-center rounded-control text-ink-muted hover:bg-surface-2"
        >
          {visible ? (
            <EyeOff className="size-4" aria-hidden="true" />
          ) : (
            <Eye className="size-4" aria-hidden="true" />
          )}
        </button>
      }
    />
  )
}
