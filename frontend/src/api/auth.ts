import { getJson, postForm, postJson } from './client'

export type RegistrationRequest = { name: string; email: string; password: string }
export type SignedIn = { redirectUrl: string }
export type SignUpResponse = { name: string; email: string } & SignedIn
export type Me = { id: string; name: string; email: string; role: 'USER' | 'ADMIN' }

// Creates the account and signs it in (decision: logged in automatically)
export function register(request: RegistrationRequest): Promise<SignUpResponse> {
  return postJson<SignUpResponse>('/api/v1/auth/register', request)
}

// Spring Security form login; redirectUrl = the app that sent the user here, or the account page
export function login(email: string, password: string): Promise<SignedIn> {
  return postForm<SignedIn>('/login', { email, password })
}

export function logout(): Promise<void> {
  return postJson<void>('/logout')
}

export function me(): Promise<Me> {
  return getJson<Me>('/api/v1/me')
}
