import { useState, type SyntheticEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { useSignIn } from '@/data/account'
import { ApiError } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'
import { AuthCard, safeReturnTo } from './AuthCard'
import { GoogleButton, googleErrorMessage } from './GoogleButton'
import { PasswordField } from './PasswordField'

export default function SignInPage() {
  usePageTitle('Sign in')
  const signIn = useSignIn()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const error = signIn.error instanceof ApiError ? signIn.error : null
  const googleError = googleErrorMessage(params.get('error'))

  function submit(e: SyntheticEvent) {
    e.preventDefault()
    signIn.mutate(
      { email, password },
      { onSuccess: () => void navigate(safeReturnTo(params.get('returnTo'))) },
    )
  }

  return (
    <div className="py-8">
      <AuthCard title="Sign in" subtitle="Your cart and wallet from this visit come with you.">
        {googleError && (
          <p role="alert" className="rounded-control bg-danger-subtle p-3 text-sm text-ink">
            {googleError}
          </p>
        )}
        <GoogleButton />
        <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
          <Field
            label="Email"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <PasswordField
            label="Password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          {error && (
            <p role="alert" className="rounded-control bg-danger-subtle p-3 text-sm text-ink">
              {error.message}
            </p>
          )}
          <Button type="submit" size="lg" loading={signIn.isPending}>
            Sign in
          </Button>
        </form>
        <p className="text-sm text-ink-muted">
          New to TrustKart?{' '}
          <Link
            to={`/signup${params.get('returnTo') ? `?returnTo=${encodeURIComponent(params.get('returnTo') ?? '')}` : ''}`}
          >
            Create an account
          </Link>
        </p>
        <p className="text-xs text-ink-muted">
          You can shop, fill a cart and check out as a guest. An account keeps your collection across devices.
        </p>
      </AuthCard>
    </div>
  )
}
