// Test helpers for the browser's fetch and cookies

type Reply = { status: number; body?: unknown }

/** Replies per URL; each call gets a fresh Response (a body can only be read once). */
export function mockFetch(replies: Record<string, Reply | Error>) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input.toString()
    const reply = replies[url]
    void _init // inspected by tests through fetchMock.mock.calls
    if (reply === undefined) throw new Error(`Unexpected fetch to ${url}`)
    if (reply instanceof Error) throw reply
    const body = reply.body === undefined ? null : JSON.stringify(reply.body)
    return new Response(body, { status: reply.status, headers: { 'Content-Type': 'application/json' } })
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

export function setCsrfCookie(value = 'test-csrf-token') {
  document.cookie = `XSRF-TOKEN=${value}; path=/`
}

export function clearCookies() {
  for (const cookie of document.cookie.split(';')) {
    const name = cookie.split('=')[0].trim()
    if (name) document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`
  }
}
