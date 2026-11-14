import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { AlertCircle, LayoutGrid, LogOut, ShieldCheck } from 'lucide-react'
import { AuthLayout } from '@/components/AuthLayout'
import { Alert, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { logout, me, type Me } from '@/api/auth'
import { ApiError } from '@/api/client'

// The signed-in landing page. Grows into the full account page (sessions, linked accounts) in A4.
export function AccountPage() {
  const navigate = useNavigate()
  const [user, setUser] = useState<Me | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [signingOut, setSigningOut] = useState(false)

  useEffect(() => {
    let active = true
    me()
      .then((current) => active && setUser(current))
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
    <AuthLayout title={`Hi, ${user.name}`} subtitle="You are signed in to AuthReflector">
      <div className="flex items-center gap-3 rounded-xl border border-slate-200 p-4 dark:border-slate-800">
        <ShieldCheck className="h-8 w-8 shrink-0 text-cyan-500" aria-hidden="true" />
        <div className="min-w-0">
          <p className="truncate font-medium">{user.name}</p>
          <p className="truncate text-sm text-slate-600 dark:text-slate-400">{user.email}</p>
        </div>
      </div>
      <p className="text-sm text-slate-600 dark:text-slate-400">
        Apps that use AuthReflector will sign you in with this account without asking for your password again.
      </p>
      {user.role === 'ADMIN' && (
        <Button asChild variant="outline" className="w-full rounded-xl">
          <Link to="/admin/apps">
            <LayoutGrid className="h-4 w-4" aria-hidden="true" /> Manage apps
          </Link>
        </Button>
      )}
      <Button variant="outline" className="w-full rounded-xl" onClick={handleSignOut} disabled={signingOut}>
        {signingOut ? <Spinner /> : <LogOut className="h-4 w-4" aria-hidden="true" />} Sign out
      </Button>
    </AuthLayout>
  )
}
