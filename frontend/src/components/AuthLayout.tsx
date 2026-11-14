import type { ReactNode } from 'react'
import { motion } from 'framer-motion'
import { ShieldCheck } from 'lucide-react'
import { Card, CardContent, CardHeader } from '@/components/ui/card'

type AuthLayoutProps = {
  title: string
  subtitle?: string
  /** The app that sent the user here (e.g. "SplitMate"); filled from the OAuth request in A1. */
  clientName?: string
  children?: ReactNode
  footer?: ReactNode
}

// Shared frame for every AuthReflector page: the auth-app card design plus the "continue to <app>" slot,
// so users always see which app they are signing in to.
export function AuthLayout({ title, subtitle, clientName, children, footer }: AuthLayoutProps) {
  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-50 via-white to-slate-100 dark:from-black dark:via-slate-900 dark:to-black text-slate-900 dark:text-white transition-colors">
      <motion.main
        initial={{ opacity: 0, y: 30 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, ease: 'easeOut' }}
        className="w-full max-w-md px-4 py-10"
      >
        <div className="mb-6 flex items-center justify-center gap-2 text-sm font-medium text-slate-600 dark:text-slate-400">
          <ShieldCheck className="h-5 w-5 text-cyan-500" aria-hidden="true" />
          AuthReflector
        </div>

        <Card className="rounded-2xl border-slate-200 dark:border-slate-800 bg-white/80 dark:bg-slate-900/70 backdrop-blur">
          <CardHeader className="space-y-2 text-center">
            <h1 className="text-3xl font-bold bg-gradient-to-r from-cyan-500 to-purple-600 dark:from-cyan-400 dark:to-purple-500 bg-clip-text text-transparent">
              {title}
            </h1>
            {subtitle && <p className="text-sm text-slate-600 dark:text-slate-400">{subtitle}</p>}
            {clientName && (
              <p className="text-sm text-slate-600 dark:text-slate-400">
                to continue to <span className="font-semibold text-slate-900 dark:text-white">{clientName}</span>
              </p>
            )}
          </CardHeader>
          <CardContent className="space-y-6">{children}</CardContent>
        </Card>

        {footer && <div className="mt-6 text-center text-sm text-slate-600 dark:text-slate-400">{footer}</div>}
      </motion.main>
    </div>
  )
}
