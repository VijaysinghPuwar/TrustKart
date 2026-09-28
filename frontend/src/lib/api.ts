/**
 * The only way the SPA talks to the API. Same-origin requests (Vite proxy in dev, Vercel rewrite in prod),
 * so auth cookies stay first-party and HttpOnly. This module:
 *  - echoes the XSRF-TOKEN cookie in X-XSRF-TOKEN on writes (Spring Security's SPA CSRF pattern)
 *  - on a 401 from an expired access token, refreshes once (deduplicated across calls) and retries
 *  - turns every failure into an ApiError carrying the server's code and support reference
 */

export interface FieldError {
  field: string
  message: string
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly requestId: string | undefined
  readonly fieldErrors: FieldError[]
  readonly details: Record<string, unknown>

  constructor(
    status: number,
    code: string,
    message: string,
    requestId?: string,
    fieldErrors: FieldError[] = [],
    details: Record<string, unknown> = {},
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.requestId = requestId
    this.fieldErrors = fieldErrors
    this.details = details
  }

  /** Short reference a shopper can quote to support, e.g. REQ-8K2M4T9QXA. */
  get supportReference(): string | undefined {
    return this.requestId ? `REQ-${this.requestId}` : undefined
  }
}

interface ErrorBody {
  code?: string
  message?: string
  requestId?: string
  fieldErrors?: FieldError[]
  details?: Record<string, unknown>
}

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export interface RequestOptions {
  body?: unknown
  idempotencyKey?: string
  signal?: AbortSignal
}

const BASE = '/api/v1'
const AUTH_PATHS = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout']

function readCookie(name: string): string | undefined {
  const prefix = `${name}=`
  for (const part of document.cookie.split(';')) {
    const trimmed = part.trim()
    if (trimmed.startsWith(prefix)) return decodeURIComponent(trimmed.slice(prefix.length))
  }
  return undefined
}

let csrfReady: Promise<void> | null = null
async function ensureCsrfCookie(): Promise<void> {
  if (readCookie('XSRF-TOKEN')) return
  csrfReady ??= fetch(`${BASE}/auth/csrf`, { credentials: 'same-origin' }).then(() => undefined)
  try {
    await csrfReady
  } finally {
    csrfReady = null
  }
}

let refreshing: Promise<boolean> | null = null
async function refreshSession(): Promise<boolean> {
  refreshing ??= (async () => {
    await ensureCsrfCookie()
    const res = await fetch(`${BASE}/auth/refresh`, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'X-XSRF-TOKEN': readCookie('XSRF-TOKEN') ?? '' },
    })
    // 409 means another tab refreshed a moment ago; its new cookies are already in place.
    return res.ok || res.status === 409
  })()
  try {
    return await refreshing
  } finally {
    refreshing = null
  }
}

/** Listeners notified when the session is found to be gone (so the UI can drop cached account data). */
const sessionListeners = new Set<() => void>()
export function onSessionEnded(listener: () => void): () => void {
  sessionListeners.add(listener)
  return () => sessionListeners.delete(listener)
}

async function send(method: Method, path: string, options: RequestOptions): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') {
    await ensureCsrfCookie()
    headers['X-XSRF-TOKEN'] = readCookie('XSRF-TOKEN') ?? ''
  }
  if (options.idempotencyKey) headers['Idempotency-Key'] = options.idempotencyKey
  return fetch(`${BASE}${path}`, {
    method,
    headers,
    credentials: 'same-origin',
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    signal: options.signal,
  })
}

async function toError(res: Response): Promise<ApiError> {
  let body: ErrorBody = {}
  try {
    body = (await res.json()) as ErrorBody
  } catch {
    body = {}
  }
  const requestId = body.requestId ?? res.headers.get('X-Request-ID') ?? undefined
  return new ApiError(
    res.status,
    body.code ?? 'HTTP_' + String(res.status),
    body.message ?? 'Something went wrong. Please try again.',
    requestId,
    body.fieldErrors ?? [],
    body.details ?? {},
  )
}

export async function api<T>(method: Method, path: string, options: RequestOptions = {}): Promise<T> {
  let res: Response
  try {
    res = await send(method, path, options)
    if (res.status === 401 && !AUTH_PATHS.includes(path) && (await refreshSession())) {
      res = await send(method, path, options)
    }
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e
    throw new ApiError(
      0,
      'NETWORK_ERROR',
      "We couldn't reach TrustKart. Check your connection and try again.",
    )
  }
  if (res.status === 401) sessionListeners.forEach((l) => l())
  if (!res.ok) throw await toError(res)
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

export const get = <T>(path: string, signal?: AbortSignal) => api<T>('GET', path, { signal })

/** A fresh key per user action; retries of the same action must reuse it. */
export function newIdempotencyKey(): string {
  return crypto.randomUUID()
}

export function queryString(
  params: Record<string, string | number | boolean | string[] | undefined | null>,
): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '' || value === false) continue
    if (Array.isArray(value)) value.forEach((v) => search.append(key, v))
    else search.set(key, String(value))
  }
  const s = search.toString()
  return s ? `?${s}` : ''
}
