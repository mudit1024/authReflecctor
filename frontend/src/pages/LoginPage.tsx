import { Link } from 'react-router'
import { AuthLayout } from '@/components/AuthLayout'

// The sign-in form arrives in the login ticket (A1); this is the routed shell.
export function LoginPage() {
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
    />
  )
}
