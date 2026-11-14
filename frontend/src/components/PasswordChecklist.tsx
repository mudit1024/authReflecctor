import { CheckCircle2, Circle } from 'lucide-react'
import { PASSWORD_RULES } from '@/lib/passwordPolicy'
import { cn } from '@/lib/utils'

// Live feedback while typing a new password, one line per rule
export function PasswordChecklist({ password, id }: { password: string; id?: string }) {
  return (
    <ul id={id} className="grid grid-cols-1 gap-1 text-xs sm:grid-cols-2" aria-label="Password requirements">
      {PASSWORD_RULES.map((rule) => {
        const met = rule.test(password)
        return (
          <li
            key={rule.id}
            data-met={met}
            className={cn(
              'flex items-center gap-1.5 transition-colors',
              met ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-500 dark:text-slate-400',
            )}
          >
            {met ? <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" /> : <Circle className="h-3.5 w-3.5" aria-hidden="true" />}
            {rule.label}
          </li>
        )
      })}
    </ul>
  )
}
