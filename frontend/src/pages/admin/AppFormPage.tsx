import { useEffect, useState, type ChangeEvent, type FormEvent, type ReactNode } from 'react'
import { useNavigate, useParams } from 'react-router'
import { AlertCircle, CheckCircle2, KeyRound } from 'lucide-react'
import { AdminLayout } from '@/components/AdminLayout'
import { SecretPanel } from '@/components/SecretPanel'
import { Alert, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Spinner } from '@/components/ui/spinner'
import { createApp, getApp, regenerateSecret, updateApp, type App, type AppRequest, type AppType } from '@/api/admin'
import { ApiError } from '@/api/client'

type Form = {
  type: AppType
  clientId: string
  name: string
  audience: string
  redirectUris: string
  postLogoutRedirectUris: string
  homeUrl: string
  description: string
  color: string
  showInLauncher: boolean
  enabled: boolean
}

const EMPTY: Form = {
  type: 'WEB',
  clientId: '',
  name: '',
  audience: '',
  redirectUris: '',
  postLogoutRedirectUris: '',
  homeUrl: '',
  description: '',
  color: '#0EA5E9',
  showInLauncher: true,
  enabled: true,
}

const lines = (text: string) => text.split('\n').map((line) => line.trim()).filter(Boolean)

function toForm(app: App): Form {
  return {
    ...app,
    redirectUris: app.redirectUris.join('\n'),
    postLogoutRedirectUris: app.postLogoutRedirectUris.join('\n'),
    homeUrl: app.homeUrl ?? '',
    description: app.description ?? '',
    color: app.color ?? '#0EA5E9',
  }
}

function toRequest(form: Form): AppRequest {
  return {
    ...form,
    audience: form.type === 'WEB' && form.audience.trim() ? form.audience.trim() : undefined,
    redirectUris: lines(form.redirectUris),
    postLogoutRedirectUris: lines(form.postLogoutRedirectUris),
    homeUrl: form.homeUrl.trim() || undefined,
    description: form.description.trim() || undefined,
  }
}

// Register a new app (/admin/apps/new) or edit one (/admin/apps/:clientId)
export function AppFormPage() {
  const { clientId: routeClientId } = useParams()
  const navigate = useNavigate()
  // After creating, this page switches to editing the new app in place: navigating to its URL would remount
  // the page and lose the one-time secret before the admin could copy it
  const [createdClientId, setCreatedClientId] = useState<string | null>(null)
  const editing = routeClientId ?? createdClientId
  const [form, setForm] = useState<Form>(EMPTY)
  const [loading, setLoading] = useState(Boolean(routeClientId))
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [secret, setSecret] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!routeClientId) return
    getApp(routeClientId)
      .then((app) => setForm(toForm(app)))
      .catch((caught) => {
        if (caught instanceof ApiError && caught.status === 401) navigate('/login', { replace: true })
        else setError(caught instanceof ApiError ? caught.message : 'Could not load the app.')
      })
      .finally(() => setLoading(false))
  }, [routeClientId, navigate])

  function set<K extends keyof Form>(key: K, value: Form[K]) {
    setForm((current) => ({ ...current, [key]: value }))
    setFieldErrors((current) => ({ ...current, [key]: '' }))
    setSaved(false)
  }

  const text = (key: keyof Form) => (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    set(key, event.target.value as never)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    setFieldErrors({})
    try {
      if (editing) {
        setForm(toForm(await updateApp(editing, toRequest(form))))
        setSaved(true)
      } else {
        const created = await createApp(toRequest(form))
        setForm(toForm(created.app))
        setSecret(created.clientSecret ?? null)
        setSaved(true)
        setCreatedClientId(created.app.clientId)
      }
    } catch (caught) {
      handleError(caught)
    } finally {
      setBusy(false)
    }
  }

  async function handleNewSecret() {
    if (!editing || !window.confirm('Replace the secret? The current one stops working immediately.')) return
    setBusy(true)
    try {
      setSecret((await regenerateSecret(editing)).clientSecret)
    } catch (caught) {
      handleError(caught)
    } finally {
      setBusy(false)
    }
  }

  function handleError(caught: unknown) {
    if (caught instanceof ApiError && caught.fieldErrors.length > 0) {
      setFieldErrors(Object.fromEntries(caught.fieldErrors.map((e) => [e.field, e.message])))
    } else {
      setError(caught instanceof ApiError ? caught.message : 'Something went wrong. Please try again.')
    }
  }

  if (loading) {
    return (
      <AdminLayout title="App">
        <Spinner className="mx-auto h-6 w-6" />
      </AdminLayout>
    )
  }

  const isWeb = form.type === 'WEB'

  return (
    <AdminLayout title={editing ? form.name || editing : 'New app'}>
      {secret && <SecretPanel clientId={form.clientId} secret={secret} />}

      {saved && !secret && (
        <Alert className="border-emerald-500/40 bg-transparent text-emerald-700 dark:text-emerald-400">
          <CheckCircle2 />
          <AlertTitle>Saved. Changes apply immediately.</AlertTitle>
        </Alert>
      )}

      {error && (
        <Alert variant="destructive" role="alert" className="border-red-500/40 bg-transparent">
          <AlertCircle />
          <AlertTitle>{error}</AlertTitle>
        </Alert>
      )}

      <form
        onSubmit={handleSubmit}
        noValidate
        className="space-y-5 rounded-2xl border border-slate-200 bg-white/80 p-6 dark:border-slate-800 dark:bg-slate-900/70"
      >
        <fieldset className="space-y-2" disabled={Boolean(editing)}>
          <legend className="text-sm font-medium">Type</legend>
          <div className="flex flex-wrap gap-4 text-sm">
            <label className="flex items-center gap-2">
              <input type="radio" name="type" checked={isWeb} onChange={() => set('type', 'WEB')} />
              With backend (Spring starter, has a secret)
            </label>
            <label className="flex items-center gap-2">
              <input type="radio" name="type" checked={!isWeb} onChange={() => set('type', 'SPA')} />
              Frontend only (JS SDK, no secret)
            </label>
          </div>
        </fieldset>

        <Field id="name" label="Name" hint="Shown on the sign-in page and in the launcher" error={fieldErrors.name}>
          <Input id="name" value={form.name} onChange={text('name')} maxLength={100} />
        </Field>

        <Field id="clientId" label="Client id" hint="Lowercase letters, digits and dashes, e.g. splitmate-web. Can't be changed later." error={fieldErrors.clientId}>
          <Input id="clientId" value={form.clientId} onChange={text('clientId')} disabled={Boolean(editing)} className="font-mono" />
        </Field>

        {isWeb && (
          <Field id="audience" label="Audience" hint="The app's API name in its access tokens, e.g. splitmate-api (defaults to the client id)" error={fieldErrors.audience}>
            <Input id="audience" value={form.audience} onChange={text('audience')} className="font-mono" />
          </Field>
        )}

        <Field id="redirectUris" label="Redirect URLs" hint="One per line. https, or http://127.0.0.1 for local development." error={fieldErrors.redirectUris}>
          <textarea id="redirectUris" value={form.redirectUris} onChange={text('redirectUris')} rows={3} className={textareaClass} />
        </Field>

        <Field id="postLogoutRedirectUris" label="After sign-out URLs" hint="One per line (optional)" error={fieldErrors.postLogoutRedirectUris}>
          <textarea id="postLogoutRedirectUris" value={form.postLogoutRedirectUris} onChange={text('postLogoutRedirectUris')} rows={2} className={textareaClass} />
        </Field>

        <Field id="homeUrl" label="Home URL" hint="Where the launcher opens the app" error={fieldErrors.homeUrl}>
          <Input id="homeUrl" value={form.homeUrl} onChange={text('homeUrl')} />
        </Field>

        <Field id="description" label="Description" hint="One line for the launcher" error={fieldErrors.description}>
          <Input id="description" value={form.description} onChange={text('description')} maxLength={300} />
        </Field>

        <div className="flex flex-wrap gap-6">
          <Field id="color" label="Color" error={fieldErrors.color}>
            <input id="color" type="color" value={form.color} onChange={text('color')} className="h-9 w-16 cursor-pointer rounded border" />
          </Field>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={form.showInLauncher} onChange={(e) => set('showInLauncher', e.target.checked)} />
            Show in launcher
          </label>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={form.enabled} onChange={(e) => set('enabled', e.target.checked)} />
            Enabled (disabling signs everyone out of this app)
          </label>
        </div>

        <div className="flex flex-wrap gap-3">
          <Button type="submit" disabled={busy} className="rounded-xl">
            {busy && <Spinner />} {editing ? 'Save changes' : 'Register app'}
          </Button>
          {editing && isWeb && (
            <Button type="button" variant="outline" disabled={busy} onClick={handleNewSecret} className="rounded-xl">
              <KeyRound className="h-4 w-4" aria-hidden="true" /> New secret
            </Button>
          )}
        </div>
      </form>
    </AdminLayout>
  )
}

const textareaClass =
  'w-full rounded-md border border-input bg-transparent px-3 py-2 font-mono text-sm shadow-xs outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50'

function Field({ id, label, hint, error, children }: { id: string; label: string; hint?: string; error?: string; children: ReactNode }) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {hint && <p className="text-xs text-slate-500">{hint}</p>}
      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
    </div>
  )
}
