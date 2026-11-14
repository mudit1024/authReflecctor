import { ApiError, getJson, postForm, postJson } from './client'
import { clearCookies, mockFetch, setCsrfCookie } from '@/test/fetchMock'

afterEach(() => {
  vi.unstubAllGlobals()
  clearCookies()
})

describe('API client', () => {
  it('sends the CSRF token from the XSRF-TOKEN cookie as a header on POST', async () => {
    setCsrfCookie('abc123')
    const fetchMock = mockFetch({ '/api/v1/x': { status: 200, body: { ok: true } } })

    await postJson('/api/v1/x', { a: 1 })

    const headers = fetchMock.mock.calls[0][1]?.headers as Record<string, string>
    expect(headers['X-XSRF-TOKEN']).toBe('abc123')
  })

  it('fetches a CSRF cookie first when there is none (e.g. right after login rotated it)', async () => {
    const fetchMock = mockFetch({
      '/api/v1/auth/csrf': { status: 204 },
      '/api/v1/x': { status: 200, body: {} },
    })
    fetchMock.mockImplementationOnce(async () => {
      setCsrfCookie('fresh') // what the browser does with the server's Set-Cookie
      return new Response(null, { status: 204 })
    })

    await postJson('/api/v1/x', {})

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/auth/csrf')
    const headers = fetchMock.mock.calls[1][1]?.headers as Record<string, string>
    expect(headers['X-XSRF-TOKEN']).toBe('fresh')
  })

  it('posts forms url-encoded (what Spring Security login expects)', async () => {
    setCsrfCookie()
    const fetchMock = mockFetch({ '/login': { status: 200, body: { redirectUrl: '/account' } } })

    await postForm('/login', { email: 'a@b.c', password: 'P@ss word' })

    const init = fetchMock.mock.calls[0][1]!
    expect((init.headers as Record<string, string>)['Content-Type']).toBe('application/x-www-form-urlencoded')
    expect(String(init.body)).toBe('email=a%40b.c&password=P%40ss+word')
  })

  it('turns error responses into ApiError with status and message', async () => {
    mockFetch({ '/api/v1/me': { status: 401, body: { status: 401, message: 'Sign in required' } } })

    await expect(getJson('/api/v1/me')).rejects.toMatchObject(new ApiError(401, 'Sign in required'))
  })
})
