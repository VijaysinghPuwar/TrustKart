import { useAuthProviders } from '@/data/account'

/**
 * Starts the OpenID Connect authorization-code flow. It's a plain top-level navigation (not fetch): the
 * browser goes to Google and comes back to /api/v1/auth/oauth2/callback/google, where the server finishes
 * sign-in and sets TrustKart's own HttpOnly cookies. Hidden unless the server has Google configured.
 */
export function GoogleButton({ label = 'Continue with Google' }: { label?: string }) {
  const providers = useAuthProviders()
  if (!providers.data?.google) return null
  return (
    <>
      <a
        href="/api/v1/auth/oauth2/authorization/google"
        className="flex h-12 items-center justify-center gap-3 rounded-button border border-border-strong bg-surface text-[15px] font-semibold text-ink no-underline hover:bg-surface-2 hover:text-ink hover:no-underline"
      >
        <svg viewBox="0 0 48 48" className="size-5" aria-hidden="true">
          <path
            fill="#EA4335"
            d="M24 9.5c3.5 0 6.6 1.2 9 3.5l6.7-6.7C35.6 2.4 30.2 0 24 0 14.6 0 6.6 5.4 2.7 13.3l7.8 6C12.4 13.7 17.7 9.5 24 9.5z"
          />
          <path
            fill="#4285F4"
            d="M46.1 24.5c0-1.6-.1-3.1-.4-4.5H24v9h12.4c-.5 2.9-2.2 5.4-4.6 7l7.4 5.7c4.3-4 6.9-9.9 6.9-17.2z"
          />
          <path
            fill="#FBBC05"
            d="M10.5 28.7A14.5 14.5 0 0 1 9.5 24c0-1.6.3-3.2.8-4.7l-7.8-6A24 24 0 0 0 0 24c0 3.9.9 7.5 2.6 10.7l7.9-6z"
          />
          <path
            fill="#34A853"
            d="M24 48c6.5 0 11.9-2.1 15.9-5.8l-7.4-5.7c-2.1 1.4-4.8 2.3-8.5 2.3-6.3 0-11.6-4.2-13.5-9.9l-7.9 6C6.6 42.6 14.6 48 24 48z"
          />
        </svg>
        {label}
      </a>
      <div className="flex items-center gap-3 text-xs text-ink-muted" aria-hidden="true">
        <span className="h-px flex-1 bg-border" />
        or
        <span className="h-px flex-1 bg-border" />
      </div>
    </>
  )
}

export function googleErrorMessage(code: string | null): string | null {
  if (code === 'google') return 'Google sign-in didn’t complete. Please try again.'
  if (code === 'google-link')
    return 'An account with this email already exists and Google couldn’t confirm the address. Sign in with your password instead.'
  return null
}
