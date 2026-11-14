// Small fetch wrapper for AuthReflector's own API. Same origin, so the browser sends the session cookie
// automatically, which is exactly why every POST must also prove it came from our page (CSRF token).
// Errors arrive in the backend's ErrorResponse format and become an ApiError the pages can display.

export type FieldError = { field: string; message: string }

export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: FieldError[]

  constructor(status: number, message: string, fieldErrors: FieldError[] = []) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

type ErrorBody = { message?: string; fieldErrors?: FieldError[] }

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

function readCookie(name: string): string | undefined {
  const entry = document.cookie.split('; ').find((cookie) => cookie.startsWith(`${name}=`))
  return entry ? decodeURIComponent(entry.slice(name.length + 1)) : undefined
}

// The server sets XSRF-TOKEN on every response; it is missing only before the first call or right after
// login/logout rotated it. Another site can't read our cookies, so echoing the value proves the origin.
async function csrfHeader(): Promise<Record<string, string>> {
  let token = readCookie(CSRF_COOKIE)
  if (!token) {
    await send('/api/v1/auth/csrf', { method: 'GET' })
    token = readCookie(CSRF_COOKIE)
  }
  return token ? { [CSRF_HEADER]: token } : {}
}

async function send(url: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(url, { credentials: 'same-origin', ...init })
  } catch {
    // fetch only rejects when no response arrived at all (offline, server down, DNS)
    throw new ApiError(0, 'Cannot reach the server. Check your connection and try again.')
  }
}

async function parse<T>(response: Response): Promise<T> {
  const data: unknown = response.status === 204 ? null : await response.json().catch(() => null)
  if (!response.ok) {
    const error = (data ?? {}) as ErrorBody
    throw new ApiError(response.status, error.message ?? 'Something went wrong. Please try again.', error.fieldErrors ?? [])
  }
  return data as T
}

export async function getJson<T>(url: string): Promise<T> {
  return parse<T>(await send(url, { method: 'GET', headers: { Accept: 'application/json' } }))
}

export async function postJson<T>(url: string, body?: unknown): Promise<T> {
  const headers = { 'Content-Type': 'application/json', Accept: 'application/json', ...(await csrfHeader()) }
  return parse<T>(await send(url, { method: 'POST', headers, body: body === undefined ? undefined : JSON.stringify(body) }))
}

export async function putJson<T>(url: string, body: unknown): Promise<T> {
  const headers = { 'Content-Type': 'application/json', Accept: 'application/json', ...(await csrfHeader()) }
  return parse<T>(await send(url, { method: 'PUT', headers, body: JSON.stringify(body) }))
}

// Spring Security's form login reads application/x-www-form-urlencoded parameters
export async function postForm<T>(url: string, fields: Record<string, string>): Promise<T> {
  const headers = {
    'Content-Type': 'application/x-www-form-urlencoded',
    Accept: 'application/json',
    ...(await csrfHeader()),
  }
  return parse<T>(await send(url, { method: 'POST', headers, body: new URLSearchParams(fields).toString() }))
}
