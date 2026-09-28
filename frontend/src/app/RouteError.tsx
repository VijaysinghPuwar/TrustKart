import { isRouteErrorResponse, Link, useRouteError } from 'react-router'
import { ApiError } from '@/lib/api'

/** Last-resort boundary: a crash in one page never blanks the whole app, and nothing internal is shown. */
export function RouteError() {
  const error = useRouteError()
  const notFound =
    (isRouteErrorResponse(error) && error.status === 404) ||
    (error instanceof ApiError && error.status === 404)
  const reference = error instanceof ApiError ? error.supportReference : undefined
  return (
    <div className="page-width page-gutter flex min-h-[60dvh] flex-col items-start justify-center gap-4 py-16">
      <p className="font-mono text-sm text-ink-muted">{notFound ? '404' : 'Error'}</p>
      <h1 className="text-[32px] font-bold leading-10">
        {notFound ? 'We couldn’t find that page' : 'Something went wrong on our side'}
      </h1>
      <p className="max-w-prose text-ink-muted">
        {notFound
          ? 'The link may be old or mistyped. Try searching for what you wanted instead.'
          : 'Reload the page to try again. If it keeps happening, the reference below helps us find the problem.'}
      </p>
      {reference && <p className="font-mono text-xs text-ink-muted">Support reference: {reference}</p>}
      <Link to="/" className="font-semibold">
        Back to the store
      </Link>
    </div>
  )
}
