import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { AlertCircle, LayoutGrid, LogOut, ShieldCheck } from 'lucide-react'
import { AuthLayout } from '@/components/AuthLayout'
import { AppLauncher } from '@/components/AppLauncher'
import { Alert, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { logout, me, type Me } from '@/api/auth'
import { launcherApps, type LauncherApp } from '@/api/launcher'
import { ApiError } from '@/api/client'

// Signed-in home: who you are, and every app you can open with this account (the launcher)
export function AccountPage() {
  const navigate = useNavigate()
  const [user, setUser] = useState<Me | null>(null)
  const [apps, setApps] = useState<LauncherApp[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [signingOut, setSigningOut] = useState(false)

  useEffect(() => {
    let active = true
    me()
      .then((current) => {
        if (!active) return
        setUser(current)
        // The launcher is a bonus: if it fails, the page still works without it
        launcherApps()
          .then((list) => active && setApps(list))
          .catch(() => active && setApps([]))
      })
      .catch((caught) => {
        if (!active) return
        if (caught instanceof ApiError && caught.status === 401) {
          navigate('/login', { replace: true })
        } else {
          setError(caught instanceof ApiError ? caught.message : 'Something went wrong. Please try again.')
        }
      })
    return () => {
      active = false
    }
  }, [navigate])

  async function handleSignOut() {
    setSigningOut(true)
    try {
      await logout()
      navigate('/login', { replace: true, state: { signedOut: true } })
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Could not sign out. Please try again.')
      setSigningOut(false)
    }
  }

  if (error) {
    return (
      <AuthLayout title="Your account">
        <Alert variant="destructive" role="alert" className="border-red-500/40 bg-transparent">
          <AlertCircle />
          <AlertTitle>{error}</AlertTitle>
        </Alert>
      </AuthLayout>
    )
  }

  if (!user) {
    return (
      <AuthLayout title="Your account">
        <div className="flex justify-center py-6" aria-label="Loading">
          <Spinner className="h-6 w-6" />
        </div>
      </AuthLayout>
    )
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 text-slate-900 dark:from-black dark:via-slate-900 dark:to-black dark:text-white">
      <div className="mx-auto max-w-3xl space-y-8 px-4 py-10">
        <header className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <ShieldCheck className="h-9 w-9 shrink-0 text-cyan-500" aria-hidden="true" />
            <div className="min-w-0">
              <h1 className="bg-gradient-to-r from-cyan-500 to-purple-600 bg-clip-text text-2xl font-bold text-transparent dark:from-cyan-400 dark:to-purple-500">
                Hi, {user.name}
              </h1>
              <p className="truncate text-sm text-slate-600 dark:text-slate-400">{user.email}</p>
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            {user.role === 'ADMIN' && (
              <Button asChild variant="outline" className="rounded-xl">
                <Link to="/admin/apps">
                  <LayoutGrid className="h-4 w-4" aria-hidden="true" /> Manage apps
                </Link>
              </Button>
            )}
            <Button variant="outline" className="rounded-xl" onClick={handleSignOut} disabled={signingOut}>
              {signingOut ? <Spinner /> : <LogOut className="h-4 w-4" aria-hidden="true" />} Sign out
            </Button>
          </div>
        </header>

        <section className="space-y-3" aria-labelledby="apps-heading">
          <div>
            <h2 id="apps-heading" className="text-lg font-semibold">
              Your apps
            </h2>
            <p className="text-sm text-slate-600 dark:text-slate-400">
              One account for all of them: open any app and you're signed in, no password needed.
            </p>
          </div>
          {apps === null ? <Spinner className="h-5 w-5" /> : <AppLauncher apps={apps} />}
        </section>
      </div>
    </div>
  )
}
