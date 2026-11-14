import { getJson } from './client'

export type LauncherApp = {
  clientId: string
  name: string
  description: string | null
  homeUrl: string
  color: string | null
  used: boolean
  signInsLast30Days: number
  lastUsedAt: string | null
}

// Apps this user can open, most used first (see LauncherController)
export const launcherApps = () => getJson<LauncherApp[]>('/api/v1/launcher')
