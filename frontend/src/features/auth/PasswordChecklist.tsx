import { Check, X } from 'lucide-react'

/** Mirrors the server's length-first policy for live feedback. The server still enforces it on submit. */
export function passwordChecks(password: string, email: string) {
  const local = email.split('@')[0]?.toLowerCase() ?? ''
  return [
    { label: 'At least 12 characters', ok: password.length >= 12 },
    { label: 'Not just a repeated character or pattern', ok: new Set(password.toLowerCase()).size > 3 },
    {
      label: 'Doesn’t contain your email name',
      ok: local.length < 4 || !password.toLowerCase().includes(local),
    },
  ]
}

export function PasswordChecklist({ password, email }: { password: string; email: string }) {
  const checks = passwordChecks(password, email)
  const passed = checks.filter((c) => c.ok).length
  const strength =
    password.length === 0 ? 0 : password.length >= 20 && passed === 3 ? 4 : passed === 3 ? 3 : passed
  return (
    <div className="flex flex-col gap-2">
      <div className="flex gap-1" aria-hidden="true">
        {[1, 2, 3, 4].map((i) => (
          <span
            key={i}
            className={`h-1.5 flex-1 rounded-full ${i <= strength ? (strength >= 3 ? 'bg-trust' : 'bg-warning') : 'bg-border'}`}
          />
        ))}
      </div>
      <ul className="flex flex-col gap-1 text-[13px]" aria-label="Password requirements">
        {checks.map((c) => (
          <li
            key={c.label}
            className={
              c.ok ? 'flex items-center gap-1.5 text-trust' : 'flex items-center gap-1.5 text-ink-muted'
            }
          >
            {c.ok ? (
              <Check className="size-3.5" aria-hidden="true" />
            ) : (
              <X className="size-3.5" aria-hidden="true" />
            )}
            {c.label}
            <span className="sr-only">{c.ok ? ' (met)' : ' (not met)'}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
