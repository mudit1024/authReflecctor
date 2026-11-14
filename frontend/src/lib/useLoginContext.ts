import { useEffect, useState } from 'react'
import { loginContext } from '@/api/auth'

// Which app is waiting for this sign-in ("to continue to SplitMate"). Purely informational:
// if the call fails, the page simply doesn't name an app.
export function useLoginContext(): string | undefined {
  const [clientName, setClientName] = useState<string>()

  useEffect(() => {
    let active = true
    loginContext()
      .then((context) => active && setClientName(context.clientName))
      .catch(() => undefined)
    return () => {
      active = false
    }
  }, [])

  return clientName
}
