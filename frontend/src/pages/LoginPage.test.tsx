import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { LoginPage } from './LoginPage'
import { goTo } from '@/lib/navigation'
import { clearCookies, mockFetch, setCsrfCookie } from '@/test/fetchMock'

vi.mock('@/lib/navigation', () => ({ goTo: vi.fn() }))

function renderLoginPage(state?: unknown) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/login', state }]}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

async function signIn(email: string, password: string) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText(/email/i), email)
  await user.type(screen.getByLabelText(/password/i), password)
  await user.click(screen.getByRole('button', { name: /sign in/i }))
}

beforeEach(() => setCsrfCookie())
afterEach(() => {
  vi.unstubAllGlobals()
  vi.mocked(goTo).mockClear()
  clearCookies()
})

describe('LoginPage', () => {
  it('signs in and continues to where the server says (the app that sent the user, or the account page)', async () => {
    const fetchMock = mockFetch({ '/login': { status: 200, body: { redirectUrl: '/account' } } })
    renderLoginPage()

    await signIn('asha@example.com', 'Password@123')

    expect(fetchMock).toHaveBeenCalledWith('/login', expect.objectContaining({ method: 'POST' }))
    await vi.waitFor(() => expect(goTo).toHaveBeenCalledWith('/account'))
  })

  it('shows the server message on wrong credentials and keeps the user on the page', async () => {
    mockFetch({ '/login': { status: 401, body: { status: 401, message: 'Invalid email or password' } } })
    renderLoginPage()

    await signIn('asha@example.com', 'Wrong@1234')

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password')
    expect(goTo).not.toHaveBeenCalled()
  })

  it('pre-fills the email after "already registered, sign in instead"', () => {
    renderLoginPage({ email: 'asha@example.com' })

    expect(screen.getByLabelText(/email/i)).toHaveValue('asha@example.com')
  })

  it('confirms a sign-out', () => {
    renderLoginPage({ signedOut: true })

    expect(screen.getByRole('status')).toHaveTextContent(/signed out/i)
  })

  // The app that sent the user here (saved authorize request) is named on the page
  it('names the app the user is signing in to', async () => {
    mockFetch({ '/api/v1/auth/login-context': { status: 200, body: { clientName: 'SplitMate' } } })
    renderLoginPage()

    expect(await screen.findByText(/to continue to/i)).toHaveTextContent('to continue to SplitMate')
  })

  it('needs both fields before it can submit', async () => {
    renderLoginPage()
    const button = screen.getByRole('button', { name: /sign in/i })

    expect(button).toBeDisabled()
    await userEvent.setup().type(screen.getByLabelText(/email/i), 'asha@example.com')
    expect(button).toBeDisabled()
  })
})
