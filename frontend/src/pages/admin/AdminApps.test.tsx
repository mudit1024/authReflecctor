import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { AppsPage } from './AppsPage'
import { AppFormPage } from './AppFormPage'
import { clearCookies, mockFetch, setCsrfCookie } from '@/test/fetchMock'

const SPLITMATE = {
  type: 'WEB',
  clientId: 'splitmate-web',
  name: 'SplitMate',
  audience: 'splitmate-api',
  redirectUris: ['http://127.0.0.1:5173/login/oauth2/code/authreflector'],
  postLogoutRedirectUris: ['http://127.0.0.1:5173/'],
  homeUrl: 'http://127.0.0.1:5173/',
  description: 'Split expenses',
  color: '#10B981',
  showInLauncher: true,
  enabled: true,
}
const PINCODE = { ...SPLITMATE, type: 'SPA', clientId: 'pincode', name: 'Pincode', enabled: false }

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/admin/apps" element={<AppsPage />} />
        <Route path="/admin/apps/new" element={<AppFormPage />} />
        <Route path="/admin/apps/:clientId" element={<AppFormPage />} />
        <Route path="/login" element={<h1>login page</h1>} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => setCsrfCookie())
afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  clearCookies()
})

describe('Apps list', () => {
  it('lists every registered app with its type and status', async () => {
    mockFetch({ '/api/v1/admin/apps': { status: 200, body: [PINCODE, SPLITMATE] } })
    renderAt('/admin/apps')

    const splitmate = (await screen.findByText('SplitMate')).closest('li')!
    expect(within(splitmate).getByText('splitmate-web')).toBeInTheDocument()
    expect(within(splitmate).getByText(/with backend/i)).toBeInTheDocument()
    const pincode = screen.getByText('Pincode').closest('li')!
    expect(within(pincode).getByText(/frontend only/i)).toBeInTheDocument()
    expect(within(pincode).getByText(/disabled/i)).toBeInTheDocument()
  })

  it('explains when the signed-in user is not an admin', async () => {
    mockFetch({ '/api/v1/admin/apps': { status: 403, body: { status: 403, message: "You don't have permission to do this" } } })
    renderAt('/admin/apps')

    expect(await screen.findByRole('alert')).toHaveTextContent(/only admins/i)
  })

  it('sends visitors who are not signed in to the sign-in page', async () => {
    mockFetch({ '/api/v1/admin/apps': { status: 401, body: { status: 401, message: 'Sign in required' } } })
    renderAt('/admin/apps')

    expect(await screen.findByRole('heading', { name: 'login page' })).toBeInTheDocument()
  })
})

describe('New app', () => {
  it('registers a web app and shows its secret exactly once', async () => {
    const fetchMock = mockFetch({
      '/api/v1/admin/apps': { status: 201, body: { app: SPLITMATE, clientSecret: 's3cr3t-value' } },
    })
    renderAt('/admin/apps/new')
    const user = userEvent.setup()

    await user.type(screen.getByLabelText(/^name/i), 'SplitMate')
    await user.type(screen.getByLabelText(/client id/i), 'splitmate-web')
    await user.type(screen.getByLabelText(/audience/i), 'splitmate-api')
    await user.type(
      screen.getByLabelText(/^redirect urls/i),
      'http://127.0.0.1:5173/login/oauth2/code/authreflector{enter}http://127.0.0.1:5173/other',
    )
    await user.type(screen.getByLabelText(/home url/i), 'http://127.0.0.1:5173/')
    await user.click(screen.getByRole('button', { name: /register app/i }))

    const sent = JSON.parse(String(fetchMock.mock.calls.at(-1)![1]!.body))
    expect(sent).toMatchObject({
      type: 'WEB',
      clientId: 'splitmate-web',
      name: 'SplitMate',
      audience: 'splitmate-api',
      redirectUris: ['http://127.0.0.1:5173/login/oauth2/code/authreflector', 'http://127.0.0.1:5173/other'],
      homeUrl: 'http://127.0.0.1:5173/',
    })
    const secretPanel = await screen.findByRole('status')
    expect(secretPanel).toHaveTextContent('s3cr3t-value')
    expect(secretPanel).toHaveTextContent(/won't be shown again/i)

    // Stays visible (no navigation/remount) and the page is now editing the new app
    expect(await screen.findByRole('button', { name: /save changes/i })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('s3cr3t-value')
    expect(fetchMock.mock.calls.filter(([url]) => url === '/api/v1/admin/apps/splitmate-web')).toHaveLength(0)
  })

  it('hides secret-related fields for frontend-only apps', async () => {
    renderAt('/admin/apps/new')
    const user = userEvent.setup()

    await user.click(screen.getByLabelText(/frontend only/i))

    expect(screen.queryByLabelText(/audience/i)).not.toBeInTheDocument()
  })

  it('shows field errors from the server next to the fields', async () => {
    mockFetch({
      '/api/v1/admin/apps': {
        status: 400,
        body: { status: 400, message: 'Validation failed', fieldErrors: [{ field: 'redirectUris', message: 'Each URL must be https' }] },
      },
    })
    renderAt('/admin/apps/new')
    const user = userEvent.setup()

    await user.type(screen.getByLabelText(/^name/i), 'X')
    await user.type(screen.getByLabelText(/client id/i), 'xxx')
    await user.type(screen.getByLabelText(/^redirect urls/i), 'http://evil.example/cb')
    await user.click(screen.getByRole('button', { name: /register app/i }))

    expect(await screen.findByText('Each URL must be https')).toBeInTheDocument()
  })
})

describe('Edit app', () => {
  it('loads the app, saves changes and can replace the secret', async () => {
    const fetchMock = mockFetch({
      '/api/v1/admin/apps/splitmate-web': { status: 200, body: SPLITMATE },
      '/api/v1/admin/apps/splitmate-web/secret': { status: 200, body: { clientSecret: 'brand-new-secret' } },
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderAt('/admin/apps/splitmate-web')
    const user = userEvent.setup()

    const name = await screen.findByDisplayValue('SplitMate')
    expect(screen.getByLabelText(/client id/i)).toBeDisabled()
    await user.clear(name)
    await user.type(name, 'SplitMate 2')
    await user.click(screen.getByRole('button', { name: /save changes/i }))

    const put = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT')!
    expect(JSON.parse(String(put[1]!.body))).toMatchObject({ clientId: 'splitmate-web', name: 'SplitMate 2' })

    await user.click(screen.getByRole('button', { name: /new secret/i }))
    expect(await screen.findByRole('status')).toHaveTextContent('brand-new-secret')
  })
})
