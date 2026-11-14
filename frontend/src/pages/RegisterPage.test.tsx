import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { RegisterPage } from './RegisterPage'
import { LoginPage } from './LoginPage'

function renderRegisterPage() {
  return render(
    <MemoryRouter initialEntries={['/register']}>
      <Routes>
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/login" element={<LoginPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

function mockFetchResponse(status: number, body: unknown) {
  const fetchMock = vi.fn().mockResolvedValue(
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

async function fillForm(name: string, email: string, password: string) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText(/name/i), name)
  await user.type(screen.getByLabelText(/email/i), email)
  await user.type(screen.getByLabelText(/^password$/i), password)
  return user
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('RegisterPage', () => {
  it('ticks off password rules while the user types', async () => {
    renderRegisterPage()
    const user = userEvent.setup()

    expect(screen.getByText(/a lowercase letter/i)).toHaveAttribute('data-met', 'false')

    await user.type(screen.getByLabelText(/^password$/i), 'pass')

    expect(screen.getByText(/a lowercase letter/i)).toHaveAttribute('data-met', 'true')
    expect(screen.getByText(/an uppercase letter/i)).toHaveAttribute('data-met', 'false')
  })

  it('keeps the button disabled until the form is valid', async () => {
    renderRegisterPage()
    const button = screen.getByRole('button', { name: /create account/i })

    expect(button).toBeDisabled()

    await fillForm('Rahul', 'rahul@example.com', 'Password@123')

    expect(button).toBeEnabled()
  })

  it('creates the account and goes to sign-in with a confirmation', async () => {
    const fetchMock = mockFetchResponse(201, { name: 'Rahul', email: 'rahul@example.com' })
    renderRegisterPage()

    const user = await fillForm('Rahul', 'Rahul@Example.com', 'Password@123')
    await user.click(screen.getByRole('button', { name: /create account/i }))

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/auth/register',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ name: 'Rahul', email: 'Rahul@Example.com', password: 'Password@123' }),
      }),
    )
    expect(await screen.findByRole('heading', { name: /welcome back/i })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('rahul@example.com')
  })

  it('shows field errors returned by the server next to the fields', async () => {
    mockFetchResponse(400, {
      status: 400,
      message: 'Validation failed',
      fieldErrors: [{ field: 'email', message: 'Email must be valid' }],
    })
    renderRegisterPage()

    const user = await fillForm('Rahul', 'rahul@example', 'Password@123')
    await user.click(screen.getByRole('button', { name: /create account/i }))

    expect(await screen.findByText('Email must be valid.')).toBeInTheDocument()
  })

  it('shows every server message when one field breaks several rules', async () => {
    mockFetchResponse(400, {
      status: 400,
      message: 'Validation failed',
      fieldErrors: [
        { field: 'password', message: 'Password must contain at least one lowercase letter' },
        { field: 'password', message: 'Password must be between 8 and 64 characters' },
      ],
    })
    renderRegisterPage()

    const user = await fillForm('Rahul', 'rahul@example.com', 'Password@123')
    await user.click(screen.getByRole('button', { name: /create account/i }))

    expect(await screen.findByText(/at least one lowercase letter\. .*between 8 and 64 characters\./i)).toBeInTheDocument()
  })

  it('explains when the email already has an account and offers sign-in', async () => {
    mockFetchResponse(409, { status: 409, message: 'Email is already registered', fieldErrors: [] })
    renderRegisterPage()

    const user = await fillForm('Rahul', 'rahul@example.com', 'Password@123')
    await user.click(screen.getByRole('button', { name: /create account/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/already registered/i)
    expect(screen.getAllByRole('link', { name: /sign in/i }).length).toBeGreaterThan(0)
  })

  it('shows a friendly message when the server cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    renderRegisterPage()

    const user = await fillForm('Rahul', 'rahul@example.com', 'Password@123')
    await user.click(screen.getByRole('button', { name: /create account/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/cannot reach the server/i)
  })
})
