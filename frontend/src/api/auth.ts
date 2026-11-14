import { postJson } from './client'

export type RegistrationRequest = { name: string; email: string; password: string }
export type RegistrationResponse = { name: string; email: string }

export function register(request: RegistrationRequest): Promise<RegistrationResponse> {
  return postJson<RegistrationResponse>('/api/v1/auth/register', request)
}
