import { Link } from 'react-router'
import { usePageTitle } from '@/lib/usePageTitle'

export function NotFoundPage() {
  usePageTitle('Page not found')
  return (
    <div className="flex flex-col items-start gap-4 py-16">
      <p className="font-mono text-sm text-ink-muted">404</p>
      <h1 className="text-[32px] font-bold leading-10">We couldn’t find that page</h1>
      <p className="max-w-prose text-ink-muted">
        The link may be old or mistyped. Try the search box above, or head back to the store.
      </p>
      <Link to="/" className="font-semibold">
        Back to the store
      </Link>
    </div>
  )
}
