import { useState, type SyntheticEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { useRegister } from '@/data/account'
import { ApiError } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'
import { AuthCard, safeReturnTo } from './AuthCard'
import { GoogleButton } from './GoogleButton'
import { PasswordChecklist, passwordChecks } from './PasswordChecklist'
import { PasswordField } from './PasswordField'

export default function SignUpPage() {
  usePageTitle('Create account')
  const register = useRegister()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [form, setForm] = useState({ displayName: '', email: '', password: '' })
  const [touched, setTouched] = useState(false)
  const error = register.error instanceof ApiError ? register.error : null
  const fieldError = (name: string) => error?.fieldErrors.find((f) => f.field === name)?.message
  const passwordOk = passwordChecks(form.password, form.email).every((c) => c.ok)

  function submit(e: SyntheticEvent) {
    e.preventDefault()
    setTouched(true)
    if (!passwordOk) return
    register.mutate(form, { onSuccess: () => void navigate(safeReturnTo(params.get('returnTo'))) })
  }

  return (
    <div className="py-8">
      <AuthCard
        title="Create your account"
        subtitle="No payment details, ever. Just an email and a strong password."
      >
        <GoogleButton label="Sign up with Google" />
        <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
          <Field
            label="Name"
            autoComplete="nickname"
            required
            maxLength={60}
            value={form.displayName}
            error={fieldError('displayName')}
            onChange={(e) => setForm({ ...form, displayName: e.target.value })}
          />
          <Field
            label="Email"
            type="email"
            autoComplete="email"
            required
            value={form.email}
            error={fieldError('email')}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
          />
          <PasswordField
            label="Password"
            autoComplete="new-password"
            required
            value={form.password}
            error={
              fieldError('password') ??
              (touched && !passwordOk ? 'Your password doesn’t meet the requirements yet.' : undefined)
            }
            hint={<PasswordChecklist password={form.password} email={form.email} />}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
          />
          {error && error.fieldErrors.length === 0 && (
            <p role="alert" className="rounded-control bg-danger-subtle p-3 text-sm text-ink">
              {error.message}
            </p>
          )}
          <Button type="submit" size="lg" loading={register.isPending}>
            Create account
          </Button>
        </form>
        <p className="text-sm text-ink-muted">
          Already have an account? <Link to="/signin">Sign in</Link>
        </p>
      </AuthCard>
    </div>
  )
}
