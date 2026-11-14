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
