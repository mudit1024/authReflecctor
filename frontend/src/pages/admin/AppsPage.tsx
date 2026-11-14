import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { AlertCircle, Plus } from 'lucide-react'
import { AdminLayout } from '@/components/AdminLayout'
import { Alert, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { listApps, type App } from '@/api/admin'
import { ApiError } from '@/api/client'

export function AppsPage() {
  const navigate = useNavigate()
  const [apps, setApps] = useState<App[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    listApps()
      .then(setApps)
      .catch((caught) => {
        if (caught instanceof ApiError && caught.status === 401) navigate('/login', { replace: true })
        else if (caught instanceof ApiError && caught.status === 403) setError('Only admins can manage apps.')
        else setError(caught instanceof ApiError ? caught.message : 'Could not load the apps.')
      })
  }, [navigate])

  return (
    <AdminLayout
      title="Apps"
      actions={
        <Button asChild className="rounded-xl">
          <Link to="/admin/apps/new">
            <Plus className="h-4 w-4" aria-hidden="true" /> New app
          </Link>
        </Button>
      }
    >
      <p className="text-sm text-slate-600 dark:text-slate-400">
        Apps that sign users in with AuthReflector. Changes apply immediately, without a restart.
      </p>

      {error && (
        <Alert variant="destructive" role="alert" className="border-red-500/40 bg-transparent">
          <AlertCircle />
          <AlertTitle>{error}</AlertTitle>
        </Alert>
      )}

      {!apps && !error && <Spinner className="mx-auto h-6 w-6" />}

      {apps && apps.length === 0 && <p>No apps registered yet.</p>}

      {apps && apps.length > 0 && (
        <ul className="divide-y divide-slate-200 rounded-2xl border border-slate-200 bg-white/80 dark:divide-slate-800 dark:border-slate-800 dark:bg-slate-900/70">
          {apps.map((app) => (
            <li key={app.clientId}>
              <Link to={`/admin/apps/${app.clientId}`} className="flex items-center gap-3 p-4 hover:bg-slate-50 dark:hover:bg-slate-800/50">
                <span className="h-8 w-8 shrink-0 rounded-lg" style={{ backgroundColor: app.color ?? '#94a3b8' }} aria-hidden="true" />
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-medium">{app.name}</span>
                  <span className="block truncate font-mono text-xs text-slate-500">{app.clientId}</span>
                </span>
                <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs dark:bg-slate-800">
                  {app.type === 'WEB' ? 'With backend' : 'Frontend only'}
                </span>
                {!app.enabled && (
                  <span className="rounded-full bg-red-100 px-2 py-0.5 text-xs text-red-700 dark:bg-red-950 dark:text-red-300">Disabled</span>
                )}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </AdminLayout>
  )
}
