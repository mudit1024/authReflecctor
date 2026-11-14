import { useState } from 'react'
import { Copy, KeyRound } from 'lucide-react'
import { Button } from '@/components/ui/button'

// Shows a freshly generated client secret. It is stored only as a hash, so this is the one chance to copy it.
export function SecretPanel({ clientId, secret }: { clientId: string; secret: string }) {
  const [copied, setCopied] = useState(false)

  async function copy() {
    await navigator.clipboard?.writeText(secret)
    setCopied(true)
  }

  return (
    <div role="status" className="space-y-3 rounded-xl border border-amber-500/50 bg-amber-50 p-4 text-amber-900 dark:bg-amber-950/40 dark:text-amber-200">
      <p className="flex items-center gap-2 font-semibold">
        <KeyRound className="h-4 w-4" aria-hidden="true" /> Client secret for {clientId}
      </p>
      <code className="block break-all rounded-lg bg-white/80 px-3 py-2 font-mono text-sm text-slate-900 dark:bg-black/40 dark:text-white">
        {secret}
      </code>
      <p className="text-sm">
        Copy it into the app's configuration now (e.g. an environment variable). It won't be shown again; if it is
        lost, generate a new one.
      </p>
      <Button type="button" variant="outline" size="sm" onClick={copy}>
        <Copy className="h-4 w-4" aria-hidden="true" /> {copied ? 'Copied' : 'Copy'}
      </Button>
    </div>
  )
}
