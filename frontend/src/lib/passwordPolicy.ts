// Mirrors the backend password policy (RegistrationRequest, SplitMate decisions D8/D31).
// Only for instant feedback while typing: the server check is the one that counts.
export type PasswordRule = {
  id: 'length' | 'lowercase' | 'uppercase' | 'digit' | 'special'
  label: string
  test: (password: string) => boolean
}

export const PASSWORD_RULES: PasswordRule[] = [
  { id: 'length', label: '8–64 characters', test: (p) => p.length >= 8 && p.length <= 64 },
  { id: 'lowercase', label: 'A lowercase letter', test: (p) => /[a-z]/.test(p) },
  { id: 'uppercase', label: 'An uppercase letter', test: (p) => /[A-Z]/.test(p) },
  { id: 'digit', label: 'A number', test: (p) => /\d/.test(p) },
  // Anything that is not a letter, digit or whitespace (same as the backend regex [^A-Za-z0-9\s])
  { id: 'special', label: 'A special character', test: (p) => /[^A-Za-z0-9\s]/.test(p) },
]

export function meetsPasswordPolicy(password: string): boolean {
  return PASSWORD_RULES.every((rule) => rule.test(password))
}
