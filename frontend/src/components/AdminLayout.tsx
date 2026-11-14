import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { ArrowLeft, ShieldCheck } from 'lucide-react'

// Wider frame for admin screens (the sign-in card is too narrow for tables and forms)
export function AdminLayout({ title, actions, children }: { title: string; actions?: ReactNode; children: ReactNode }) {
  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 text-slate-900 dark:from-black dark:via-slate-900 dark:to-black dark:text-white">
      <div className="mx-auto max-w-3xl px-4 py-8">
        <header className="mb-6 flex flex-wrap items-center justify-between gap-3">
          <div className="space-y-1">
            <Link to="/account" className="inline-flex items-center gap-1 text-sm text-slate-600 hover:underline dark:text-slate-400">
              <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Account
            </Link>
            <h1 className="flex items-center gap-2 text-2xl font-bold">
              <ShieldCheck className="h-6 w-6 text-cyan-500" aria-hidden="true" />
              {title}
            </h1>
          </div>
          {actions}
        </header>
        <main className="space-y-6">{children}</main>
      </div>
    </div>
  )
}
