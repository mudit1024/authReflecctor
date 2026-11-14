import { Link } from 'react-router'
import { AuthLayout } from '@/components/AuthLayout'

export function NotFoundPage() {
  return (
    <AuthLayout title="Page not found" subtitle="The link may be old or mistyped.">
      <p className="text-center">
        <Link to="/login" className="font-medium text-cyan-600 hover:underline dark:text-cyan-400">
          Go to sign in
        </Link>
      </p>
    </AuthLayout>
  )
}
