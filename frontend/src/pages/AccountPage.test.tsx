import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { AccountPage } from './AccountPage'
import { clearCookies, mockFetch, setCsrfCookie } from '@/test/fetchMock'

function LoginProbe() {
  const state = useLocation().state as { signedOut?: boolean } | null
  return <h1>login page{state?.signedOut ? ' (signed out)' : ''}</h1>
}

function renderAccountPage() {
  return render(
    <MemoryRouter initialEntries={['/account']}>
      <Routes>
        <Route path="/account" element={<AccountPage />} />
        <Route path="/login" element={<LoginProbe />} />
      </Routes>
    </MemoryRouter>,
  )
}

const ASHA = { id: '0fbdeb20-e6c6-4160-94f1-13b14c8cee54', name: 'Asha', email: 'asha@example.com', role: 'USER' }

const LAUNCHER = [
  { clientId: 'pincode', name: 'Pincode', description: 'Find any pincode', homeUrl: 'http://127.0.0.1:5175/', color: '#F97316', used: true, signInsLast30Days: 7, lastUsedAt: '2026-11-14T08:00:00Z' },
  { clientId: 'splitmate-web', name: 'SplitMate', description: 'Split expenses', homeUrl: 'http://127.0.0.1:5173/', color: '#10B981', used: true, signInsLast30Days: 0, lastUsedAt: '2026-09-01T08:00:00Z' },
  { clientId: 'codequror', name: 'CodeQuror', description: 'Strava for coders', homeUrl: 'https://codequror.example.com/', color: '#6366F1', used: false, signInsLast30Days: 0, lastUsedAt: null },
]

beforeEach(() => setCsrfCookie())
afterEach(() => {
  vi.unstubAllGlobals()
  clearCookies()
})

describe('AccountPage', () => {
  it('shows who is signed in', async () => {
    mockFetch({ '/api/v1/me': { status: 200, body: ASHA } })
    renderAccountPage()

    expect(await screen.findByText('asha@example.com')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /hi, asha/i })).toBeInTheDocument()
  })

  // Glowing = used, faded = never used; order comes from the server (most used first)
  it('shows the app launcher with used apps glowing and unused apps faded', async () => {
    mockFetch({
      '/api/v1/me': { status: 200, body: ASHA },
      '/api/v1/launcher': { status: 200, body: LAUNCHER },
    })
    renderAccountPage()

    const tiles = await screen.findAllByRole('link', { name: /open/i })
    expect(tiles.map((tile) => tile.getAttribute('data-app'))).toEqual(['pincode', 'splitmate-web', 'codequror'])
    expect(tiles[0]).toHaveAttribute('data-used', 'true')
    expect(tiles[2]).toHaveAttribute('data-used', 'false')
    expect(tiles[0]).toHaveAttribute('href', 'http://127.0.0.1:5175/')
    expect(tiles[0]).toHaveAttribute('target', '_blank')
    expect(tiles[0]).toHaveTextContent(/7 sign-ins this month/i)
    expect(tiles[2]).toHaveTextContent(/not used yet/i)
  })

  it('says so when no apps are available yet', async () => {
    mockFetch({
      '/api/v1/me': { status: 200, body: ASHA },
      '/api/v1/launcher': { status: 200, body: [] },
    })
    renderAccountPage()

    expect(await screen.findByText(/no apps are connected yet/i)).toBeInTheDocument()
  })

  it('sends visitors who are not signed in to the sign-in page', async () => {
    mockFetch({ '/api/v1/me': { status: 401, body: { status: 401, message: 'Sign in required' } } })
    renderAccountPage()

    expect(await screen.findByRole('heading', { name: 'login page' })).toBeInTheDocument()
  })

  it('signs out and confirms it on the sign-in page', async () => {
    const fetchMock = mockFetch({
      '/api/v1/me': { status: 200, body: ASHA },
      '/logout': { status: 204 },
    })
    renderAccountPage()

    await userEvent.setup().click(await screen.findByRole('button', { name: /sign out/i }))

    expect(fetchMock).toHaveBeenCalledWith('/logout', expect.objectContaining({ method: 'POST' }))
    expect(await screen.findByRole('heading', { name: 'login page (signed out)' })).toBeInTheDocument()
  })
})
