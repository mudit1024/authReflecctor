import { Link } from 'react-router'
import { AuthLayout } from '@/components/AuthLayout'

// The sign-up form arrives in the registration ticket (A1); this is the routed shell.
export function RegisterPage() {
  return (
    <AuthLayout
      title="Create your account"
      subtitle="One account for every app"
      footer={
        <>
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-cyan-600 hover:underline dark:text-cyan-400">
            Sign in
          </Link>
        </>
      }
    />
  )
}
