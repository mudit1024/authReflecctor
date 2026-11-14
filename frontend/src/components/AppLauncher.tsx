import type { CSSProperties } from 'react'
import { ExternalLink } from 'lucide-react'
import type { LauncherApp } from '@/api/launcher'
import { cn } from '@/lib/utils'

// Google-style app grid. Used apps glow in their own colour; never-used apps are faded and grey.
// The order comes from the server: most used in the last 30 days first, unused apps last.
export function AppLauncher({ apps }: { apps: LauncherApp[] }) {
  if (apps.length === 0) {
    return <p className="text-sm text-slate-600 dark:text-slate-400">No apps are connected yet.</p>
  }

  return (
    <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3">
      {apps.map((app) => {
        const color = app.color ?? '#64748b'
        const glow: CSSProperties | undefined = app.used
          ? { borderColor: color, boxShadow: `0 0 0 1px ${color}55, 0 8px 28px -6px ${color}88` }
          : undefined
        return (
          <li key={app.clientId}>
            <a
              href={app.homeUrl}
              target="_blank"
              rel="noopener noreferrer"
              data-app={app.clientId}
              data-used={app.used}
              aria-label={`Open ${app.name}`}
              style={glow}
              className={cn(
                'group flex h-full flex-col gap-2 rounded-2xl border bg-white/80 p-4 transition hover:-translate-y-0.5 dark:bg-slate-900/70',
                app.used
                  ? 'border-slate-200 dark:border-slate-800'
                  : 'border-dashed border-slate-300 opacity-50 grayscale hover:opacity-90 hover:grayscale-0 dark:border-slate-700',
              )}
            >
              <span className="flex items-center justify-between">
                <span
                  className="flex h-10 w-10 items-center justify-center rounded-xl text-lg font-bold text-white"
                  style={{ backgroundColor: color }}
                  aria-hidden="true"
                >
                  {app.name.charAt(0).toUpperCase()}
                </span>
                <ExternalLink className="h-4 w-4 text-slate-400 opacity-0 transition group-hover:opacity-100" aria-hidden="true" />
              </span>
              <span className="font-semibold leading-tight">{app.name}</span>
              {app.description && (
                <span className="line-clamp-2 text-xs text-slate-600 dark:text-slate-400">{app.description}</span>
              )}
              <span className="mt-auto text-xs text-slate-500">
                {!app.used
                  ? 'Not used yet'
                  : app.signInsLast30Days > 0
                    ? `${app.signInsLast30Days} sign-in${app.signInsLast30Days === 1 ? '' : 's'} this month`
                    : 'Used before'}
              </span>
            </a>
          </li>
        )
      })}
    </ul>
  )
}
