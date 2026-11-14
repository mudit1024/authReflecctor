import { beforeEach, describe, expect, it, vi } from 'vitest'

// A fake oidc-client-ts: we test OUR behaviour (settings, callback handling, silent SSO), not the library
const fake = vi.hoisted(() => ({
  settings: undefined as Record<string, unknown> | undefined,
  storedUser: null as unknown,
  signinRedirect: vi.fn(),
  signinRedirectCallback: vi.fn(),
  signoutRedirect: vi.fn(),
  removeUser: vi.fn(),
}))

vi.mock('oidc-client-ts', () => ({
  UserManager: class {
    constructor(settings: Record<string, unknown>) {
      fake.settings = settings
    }
    getUser = async () => fake.storedUser
    signinRedirect = fake.signinRedirect
    signinRedirectCallback = fake.signinRedirectCallback
    signoutRedirect = fake.signoutRedirect
    removeUser = fake.removeUser
  },
  WebStorageStateStore: class {
    constructor(public options: unknown) {}
  },
}))

import { create } from './index'

const ISSUER = 'http://localhost:9000'

function oidcUser(overrides: Record<string, unknown> = {}) {
  return {
    expired: false,
    access_token: 'access-123',
    state: undefined,
    profile: { sub: '8f14e45f-uuid', name: 'Asha', email: 'asha@example.com', roles: ['USER'] },
    ...overrides,
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  fake.storedUser = null
  sessionStorage.clear()
  window.history.replaceState(null, '', '/app/page')
})

describe('create', () => {
  it('configures a public client with PKCE against AuthReflector, without a discovery call', () => {
    create({ issuer: ISSUER, clientId: 'pincode' })

    expect(fake.settings).toMatchObject({
      authority: ISSUER,
      client_id: 'pincode',
      redirect_uri: 'http://localhost:3000/app/page',
      response_type: 'code',
      scope: 'openid profile email',
      loadUserInfo: false,
      automaticSilentRenew: false,
      metadata: {
        issuer: ISSUER,
        authorization_endpoint: `${ISSUER}/oauth2/authorize`,
        token_endpoint: `${ISSUER}/oauth2/token`,
        end_session_endpoint: `${ISSUER}/connect/logout`,
      },
    })
  })
})

describe('init', () => {
  it('restores a signed-in user from this tab', async () => {
    fake.storedUser = oidcUser()
    const auth = create({ issuer: ISSUER, clientId: 'pincode' })

    const user = await auth.init()

    expect(user).toEqual({ id: '8f14e45f-uuid', name: 'Asha', email: 'asha@example.com', roles: ['USER'] })
    expect(auth.user).toEqual(user)
    expect(fake.signinRedirect).not.toHaveBeenCalled()
  })

  it('finishes the sign-in when coming back from AuthReflector and cleans the URL', async () => {
    window.history.replaceState(null, '', '/app/page?code=abc&state=xyz')
    fake.signinRedirectCallback.mockResolvedValue(oidcUser({ state: { returnTo: '/app/page?tab=2' } }))
    const auth = create({ issuer: ISSUER, clientId: 'pincode' })

    const user = await auth.init()

    expect(user?.name).toBe('Asha')
    expect(window.location.pathname + window.location.search).toBe('/app/page?tab=2')
  })

  // Silent SSO: one quiet round trip; already signed in at AuthReflector = signed in here, no password
  it('tries a silent sign-in once when nobody is signed in yet', async () => {
    const auth = create({ issuer: ISSUER, clientId: 'pincode' })

    await auth.init()

    expect(fake.signinRedirect).toHaveBeenCalledWith(expect.objectContaining({ prompt: 'none' }))
  })

  it('does not loop: after login_required it stays signed out', async () => {
    window.history.replaceState(null, '', '/app/page?error=login_required&state=xyz')
    fake.signinRedirectCallback.mockRejectedValue(Object.assign(new Error('login_required'), { error: 'login_required' }))
    const auth = create({ issuer: ISSUER, clientId: 'pincode' })

    const user = await auth.init()
    await create({ issuer: ISSUER, clientId: 'pincode' }).init() // e.g. the page reloads

    expect(user).toBeNull()
    expect(fake.signinRedirect).not.toHaveBeenCalled()
    expect(window.location.search).toBe('')
  })

  it('can skip the silent sign-in', async () => {
    const auth = create({ issuer: ISSUER, clientId: 'pincode', silentSignIn: false })

    expect(await auth.init()).toBeNull()
    expect(fake.signinRedirect).not.toHaveBeenCalled()
  })

  it('treats an expired session as signed out', async () => {
    fake.storedUser = oidcUser({ expired: true })
    const auth = create({ issuer: ISSUER, clientId: 'pincode', silentSignIn: false })

    expect(await auth.init()).toBeNull()
    expect(await auth.getAccessToken()).toBeNull()
  })
})

describe('sign in, tokens and sign out', () => {
  it('signs in interactively and comes back to the current page', async () => {
    const auth = create({ issuer: ISSUER, clientId: 'pincode' })

    await auth.signIn()

    expect(fake.signinRedirect).toHaveBeenCalledWith({ state: { returnTo: '/app/page' } })
  })

  it('returns the access token while it is valid', async () => {
    fake.storedUser = oidcUser()
    const auth = create({ issuer: ISSUER, clientId: 'pincode' })
    await auth.init()

    expect(await auth.getAccessToken()).toBe('access-123')
  })

  it('notifies listeners and signs out at AuthReflector (single sign-out)', async () => {
    fake.storedUser = oidcUser()
    const auth = create({ issuer: ISSUER, clientId: 'pincode' })
    const listener = vi.fn()
    auth.onChange(listener)
    await auth.init()

    await auth.signOut()

    expect(listener).toHaveBeenNthCalledWith(1, expect.objectContaining({ name: 'Asha' }))
    expect(listener).toHaveBeenLastCalledWith(null)
    expect(fake.signoutRedirect).toHaveBeenCalled()
  })
})
