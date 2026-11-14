// Small fetch wrapper for AuthReflector's own API (same origin, so the session cookie is sent automatically).
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

export async function postJson<T>(url: string, body: unknown): Promise<T> {
  let response: Response
  try {
    response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify(body),
    })
  } catch {
    // fetch only rejects when no response arrived at all (offline, server down, DNS)
    throw new ApiError(0, 'Cannot reach the server. Check your connection and try again.')
  }

  const data: unknown = await response.json().catch(() => null)

  if (!response.ok) {
    const error = (data ?? {}) as ErrorBody
    throw new ApiError(response.status, error.message ?? 'Something went wrong. Please try again.', error.fieldErrors ?? [])
  }
  return data as T
}
