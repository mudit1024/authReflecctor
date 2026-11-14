import { Link, useLocation } from 'react-router'
import { CheckCircle2 } from 'lucide-react'
import { AuthLayout } from '@/components/AuthLayout'
import { Alert, AlertTitle } from '@/components/ui/alert'

type LoginLocationState = { registeredEmail?: string } | null

// The sign-in form arrives in AUTH-12 (login, session and logout); this page already
// confirms a fresh registration.
export function LoginPage() {
  const state = useLocation().state as LoginLocationState

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
      {state?.registeredEmail && (
        <Alert role="status" className="border-emerald-500/40 bg-transparent text-emerald-700 dark:text-emerald-400">
          <CheckCircle2 />
          <AlertTitle>Account created for {state.registeredEmail}. You can sign in now.</AlertTitle>
        </Alert>
      )}
    </AuthLayout>
  )
}
