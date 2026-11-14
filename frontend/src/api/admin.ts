import { getJson, postJson, putJson } from './client'

// WEB = has a backend (secret, Spring starter); SPA = frontend only (no secret, JS SDK)
export type AppType = 'WEB' | 'SPA'

export type App = {
  type: AppType
  clientId: string
  name: string
  audience: string
  redirectUris: string[]
  postLogoutRedirectUris: string[]
  homeUrl: string | null
  description: string | null
  color: string | null
  showInLauncher: boolean
  enabled: boolean
}

export type AppRequest = Omit<App, 'audience' | 'homeUrl' | 'description' | 'color'> & {
  audience?: string
  homeUrl?: string
  description?: string
  color?: string
}

const APPS = '/api/v1/admin/apps'

export const listApps = () => getJson<App[]>(APPS)
export const getApp = (clientId: string) => getJson<App>(`${APPS}/${encodeURIComponent(clientId)}`)
export const createApp = (request: AppRequest) => postJson<{ app: App; clientSecret?: string }>(APPS, request)
export const updateApp = (clientId: string, request: AppRequest) =>
  putJson<App>(`${APPS}/${encodeURIComponent(clientId)}`, request)
export const regenerateSecret = (clientId: string) =>
  postJson<{ clientSecret: string }>(`${APPS}/${encodeURIComponent(clientId)}/secret`)
