/**
 * AuthReflector for frontend-only apps.
 *
 *   <script src="http://localhost:9000/sdk/authreflector-web.js"></script>
 *   <script>
 *     const auth = AuthReflector.create({ issuer: 'http://localhost:9000', clientId: 'pincode' })
 *     auth.init().then((user) => { ... user ? show(user.name) : showSignInButton() })
 *   </script>
 *
 * Standard OpenID Connect (Authorization Code + PKCE) through oidc-client-ts. The app is a public client:
 * no secret, no refresh token. When the session ends, a quiet round trip to AuthReflector signs the user
 * in again if they are still signed in there (single sign-on).
 */
import { UserManager, WebStorageStateStore, type User } from 'oidc-client-ts'

export type AuthReflectorUser = {
  /** Stable user id (the token's "sub"): use this as the user key in your app. */
  id: string
  name?: string
  email?: string
  /** Roles in this app (USER for everyone, more if granted). */
  roles: string[]
}

export type AuthReflectorOptions = {
  /** AuthReflector's base URL, e.g. http://localhost:9000 */
  issuer: string
  /** The client id registered on AuthReflector's admin page */
  clientId: string
  /** Must be registered for the app. Default: the current page without query string. */
  redirectUri?: string
  /** Where to land after signing out. Default: the app's origin + '/'. Must be registered too. */
  postLogoutRedirectUri?: string
  /** Quietly sign in if the user is already signed in at AuthReflector. Default: true. */
  silentSignIn?: boolean
}

export type AuthReflectorClient = {
  readonly user: AuthReflectorUser | null
  /** Call once on page load. Resolves with the signed-in user, or null. */
  init(): Promise<AuthReflectorUser | null>
  /** Sends the browser to AuthReflector's sign-in page (instant if already signed in there). */
  signIn(): Promise<void>
  /** Signs out here and at AuthReflector (single sign-out). */
  signOut(): Promise<void>
  /** A valid access token for this app's API, or null. */
  getAccessToken(): Promise<string | null>
  /** Called with the user whenever it changes; returns an unsubscribe function. */
  onChange(listener: (user: AuthReflectorUser | null) => void): () => void
}

type ReturnState = { returnTo?: string }

const SILENT_TRIED = 'authreflector:silent-tried'
// Errors that mean "not signed in at AuthReflector" after a prompt=none round trip
const NOT_SIGNED_IN = new Set(['login_required', 'interaction_required', 'consent_required', 'account_selection_required'])

export function create(options: AuthReflectorOptions): AuthReflectorClient {
  const issuer = options.issuer.replace(/\/+$/, '')
  const storage = window.sessionStorage

  const manager = new UserManager({
    authority: issuer,
    client_id: options.clientId,
    redirect_uri: options.redirectUri ?? window.location.origin + window.location.pathname,
    post_logout_redirect_uri: options.postLogoutRedirectUri ?? window.location.origin + '/',
    response_type: 'code',
    scope: 'openid profile email',
    // Known endpoints: no discovery request needed
    metadata: {
      issuer,
      authorization_endpoint: `${issuer}/oauth2/authorize`,
      token_endpoint: `${issuer}/oauth2/token`,
      end_session_endpoint: `${issuer}/connect/logout`,
      jwks_uri: `${issuer}/oauth2/jwks`,
      userinfo_endpoint: `${issuer}/userinfo`,
    },
    // Name, email and roles are already in the ID token
    loadUserInfo: false,
    // No iframes: browsers block third-party cookies; we re-check with a top-level redirect instead
    automaticSilentRenew: false,
    monitorSession: false,
    // One tab only, cleared when it closes
    userStore: new WebStorageStateStore({ store: storage }),
    stateStore: new WebStorageStateStore({ store: storage }),
  })

  let current: AuthReflectorUser | null = null
  const listeners = new Set<(user: AuthReflectorUser | null) => void>()

  function setUser(user: AuthReflectorUser | null) {
    current = user
    listeners.forEach((listener) => listener(user))
  }

  function currentPage() {
    return window.location.pathname + window.location.search + window.location.hash
  }

  function isCallback(params: URLSearchParams) {
    return params.has('state') && (params.has('code') || params.has('error'))
  }

  async function handleCallback(): Promise<AuthReflectorUser | null> {
    try {
      const user = await manager.signinRedirectCallback()
      storage.removeItem(SILENT_TRIED)
      const returnTo = (user.state as ReturnState | undefined)?.returnTo
      window.history.replaceState(null, '', returnTo ?? window.location.pathname)
      return toUser(user)
    } catch (error) {
      window.history.replaceState(null, '', window.location.pathname)
      if (NOT_SIGNED_IN.has((error as { error?: string }).error ?? '')) {
        storage.setItem(SILENT_TRIED, '1') // stay signed out; don't redirect again on reload
        return null
      }
      throw error
    }
  }

  return {
    get user() {
      return current
    },

    async init() {
      const params = new URLSearchParams(window.location.search)
      if (isCallback(params)) {
        const user = await handleCallback()
        setUser(user)
        return user
      }

      const stored = await manager.getUser()
      if (stored && !stored.expired) {
        const user = toUser(stored)
        setUser(user)
        return user
      }

      if (options.silentSignIn !== false && !storage.getItem(SILENT_TRIED)) {
        storage.setItem(SILENT_TRIED, '1')
        await manager.signinRedirect({ prompt: 'none', state: { returnTo: currentPage() } satisfies ReturnState })
      }
      setUser(null)
      return null
    },

    async signIn() {
      storage.removeItem(SILENT_TRIED)
      await manager.signinRedirect({ state: { returnTo: currentPage() } satisfies ReturnState })
    },

    async signOut() {
      storage.setItem(SILENT_TRIED, '1') // signed out on purpose: no silent sign-in on the next page load
      setUser(null)
      await manager.signoutRedirect()
      await manager.removeUser()
    },

    async getAccessToken() {
      const stored = await manager.getUser()
      return stored && !stored.expired ? stored.access_token : null
    },

    onChange(listener) {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
  }
}

function toUser(user: User): AuthReflectorUser {
  const profile = user.profile as Record<string, unknown>
  return {
    id: String(profile.sub),
    name: typeof profile.name === 'string' ? profile.name : undefined,
    email: typeof profile.email === 'string' ? profile.email : undefined,
    roles: Array.isArray(profile.roles) ? profile.roles.map(String) : [],
  }
}
