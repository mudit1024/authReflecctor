import { meetsPasswordPolicy, PASSWORD_RULES } from './passwordPolicy'

// Same cases as the backend's RegistrationRequestValidationTest, so the live checklist and the server agree
describe('password policy (mirrors backend RegistrationRequest)', () => {
  it.each([
    ['Password@123', true],
    ['Pa@1234', false], // 7 characters
    ['PASSWORD@123', false], // no lowercase
    ['password@123', false], // no uppercase
    ['Password@', false], // no number
    ['Password123', false], // no special character
    ['        ', false], // blank
    ['Password@1' + 'a'.repeat(54), true], // exactly 64
    ['Password@1' + 'a'.repeat(55), false], // 65
  ])('%s -> %s', (password, expected) => {
    expect(meetsPasswordPolicy(password)).toBe(expected)
  })

  it('does not count a space as a special character', () => {
    expect(meetsPasswordPolicy('Password 123')).toBe(false)
  })

  it('reports each rule separately for the checklist', () => {
    const met = PASSWORD_RULES.filter((rule) => rule.test('pass')).map((rule) => rule.id)

    expect(met).toEqual(['lowercase'])
  })
})
