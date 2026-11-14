import { useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router'
import { AlertCircle, CheckCircle2, Mail } from 'lucide-react'
import { AuthLayout } from '@/components/AuthLayout'
import { Alert, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Spinner } from '@/components/ui/spinner'
import { login } from '@/api/auth'
import { ApiError } from '@/api/client'
import { goTo } from '@/lib/navigation'

type LoginLocationState = { email?: string; signedOut?: boolean } | null

export function LoginPage() {
  const state = useLocation().state as LoginLocationState
  const [email, setEmail] = useState(state?.email ?? '')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const canSubmit = email.trim() !== '' && password !== '' && !submitting

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!canSubmit) return

    setSubmitting(true)
    setError(null)
    try {
      const { redirectUrl } = await login(email.trim(), password)
      goTo(redirectUrl)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Something went wrong. Please try again.')
      setPassword('')
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout
      title="Welcome back"
      subtitle="Sign in with your AuthReflector account"
      footer={
        <>
          New here?{' '}
          <Link to="/register" className="font-medium text-cyan-600 hover:underline dark:text-cyan-400">
            Create an account
          </Link>
        </>
      }
    >
      {state?.signedOut && !error && (
        <Alert role="status" className="border-emerald-500/40 bg-transparent text-emerald-700 dark:text-emerald-400">
          <CheckCircle2 />
          <AlertTitle>You have signed out.</AlertTitle>
        </Alert>
      )}

      {error && (
        <Alert variant="destructive" role="alert" className="border-red-500/40 bg-transparent">
          <AlertCircle />
          <AlertTitle>{error}</AlertTitle>
        </Alert>
      )}

      <form onSubmit={handleSubmit} noValidate className="space-y-5">
        <div className="space-y-2">
          <Label htmlFor="email">Email</Label>
          <div className="relative">
            <Mail className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
            <Input
              id="email"
              name="email"
              type="email"
              autoComplete="username"
              placeholder="you@example.com"
              className="rounded-xl pl-10"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoFocus={!state?.email}
            />
          </div>
        </div>

        <div className="space-y-2">
          <Label htmlFor="password">Password</Label>
          <Input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            placeholder="••••••••"
            className="rounded-xl"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoFocus={Boolean(state?.email)}
          />
        </div>

        <Button type="submit" disabled={!canSubmit} className="w-full rounded-xl">
          {submitting ? (
            <>
              <Spinner /> Signing in…
            </>
          ) : (
            'Sign in'
          )}
        </Button>
      </form>
    </AuthLayout>
  )
}
