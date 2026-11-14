import { useState, type ChangeEvent, type FormEvent } from 'react'
import { Link } from 'react-router'
import { AlertCircle, Mail, User } from 'lucide-react'
import { AuthLayout } from '@/components/AuthLayout'
import { PasswordChecklist } from '@/components/PasswordChecklist'
import { Alert, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Spinner } from '@/components/ui/spinner'
import { register } from '@/api/auth'
import { ApiError } from '@/api/client'
import { meetsPasswordPolicy } from '@/lib/passwordPolicy'
import { goTo } from '@/lib/navigation'

type Form = { name: string; email: string; password: string }
type Field = keyof Form

// Loose on purpose: just "something@something". The server's validation is the authority.
const LOOKS_LIKE_EMAIL = /^[^\s@]+@[^\s@]+$/

export function RegisterPage() {
  const [form, setForm] = useState<Form>({ name: '', email: '', password: '' })
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<Field, string>>>({})
  const [error, setError] = useState<{ message: string; emailTaken?: boolean } | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const isValid =
    form.name.trim() !== '' && LOOKS_LIKE_EMAIL.test(form.email.trim()) && meetsPasswordPolicy(form.password)

  function handleChange(event: ChangeEvent<HTMLInputElement>) {
    const field = event.target.name as Field
    setForm((current) => ({ ...current, [field]: event.target.value }))
    // The user is fixing this field, so its old server error no longer applies
    setFieldErrors((current) => ({ ...current, [field]: undefined }))
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!isValid || submitting) return

    setSubmitting(true)
    setError(null)
    setFieldErrors({})
    try {
      // The new account is already signed in: continue to the app that sent the user, or the account page
      const { redirectUrl } = await register(form)
      goTo(redirectUrl)
      return
    } catch (caught) {
      const apiError = caught instanceof ApiError ? caught : new ApiError(0, 'Something went wrong. Please try again.')
      if (apiError.fieldErrors.length > 0) {
        // One field can fail several rules (e.g. too short AND missing a digit): show them all
        const byField: Partial<Record<Field, string>> = {}
        for (const { field, message } of apiError.fieldErrors) {
          const key = field as Field
          byField[key] = byField[key] ? `${byField[key]} ${message}.` : `${message}.`
        }
        setFieldErrors(byField)
      } else if (apiError.status === 409) {
        setError({ message: 'This email is already registered.', emailTaken: true })
      } else {
        setError({ message: apiError.message })
      }
      // Only on failure: on success the button stays busy while the browser navigates away
      setSubmitting(false)
    }
  }

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
    >
      {error && (
        <Alert variant="destructive" role="alert" className="border-red-500/40 bg-transparent">
          <AlertCircle />
          <AlertTitle>
            {error.message}{' '}
            {error.emailTaken && (
              <Link to="/login" state={{ email: form.email.trim().toLowerCase() }} className="underline">
                Sign in instead
              </Link>
            )}
          </AlertTitle>
        </Alert>
      )}

      <form onSubmit={handleSubmit} noValidate className="space-y-5">
        <div className="space-y-2">
          <Label htmlFor="name">Name</Label>
          <div className="relative">
            <User className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
            <Input
              id="name"
              name="name"
              autoComplete="name"
              placeholder="Asha Sharma"
              maxLength={100}
              className="rounded-xl pl-10"
              value={form.name}
              onChange={handleChange}
              aria-invalid={Boolean(fieldErrors.name)}
              aria-describedby={fieldErrors.name ? 'name-error' : undefined}
            />
          </div>
          <FieldErrorText id="name-error" message={fieldErrors.name} />
        </div>

        <div className="space-y-2">
          <Label htmlFor="email">Email</Label>
          <div className="relative">
            <Mail className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
            <Input
              id="email"
              name="email"
              type="email"
              autoComplete="email"
              placeholder="you@example.com"
              maxLength={254}
              className="rounded-xl pl-10"
              value={form.email}
              onChange={handleChange}
              aria-invalid={Boolean(fieldErrors.email)}
              aria-describedby={fieldErrors.email ? 'email-error' : undefined}
            />
          </div>
          <FieldErrorText id="email-error" message={fieldErrors.email} />
        </div>

        <div className="space-y-2">
          <Label htmlFor="password">Password</Label>
          <Input
            id="password"
            name="password"
            type="password"
            autoComplete="new-password"
            placeholder="••••••••"
            maxLength={64}
            className="rounded-xl"
            value={form.password}
            onChange={handleChange}
            aria-invalid={Boolean(fieldErrors.password)}
            aria-describedby="password-rules"
          />
          <PasswordChecklist id="password-rules" password={form.password} />
          <FieldErrorText id="password-error" message={fieldErrors.password} />
        </div>

        <Button type="submit" disabled={!isValid || submitting} className="w-full rounded-xl">
          {submitting ? (
            <>
              <Spinner /> Creating account…
            </>
          ) : (
            'Create account'
          )}
        </Button>
      </form>
    </AuthLayout>
  )
}

function FieldErrorText({ id, message }: { id: string; message?: string }) {
  if (!message) return null
  return (
    <p id={id} className="text-sm text-red-600 dark:text-red-400">
      {message}
    </p>
  )
}
