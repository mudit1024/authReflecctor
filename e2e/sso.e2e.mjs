// Real-browser single sign-on test across the three ways an app can integrate with AuthReflector:
//   - Pincode:        React app + JS SDK (frontend only)
//   - Embeddings POC: plain HTML + JS SDK (frontend only)
//   - Demo app:       Spring Boot + client starter (backend for frontend)
// Prerequisites and how to start everything: see README.md in this folder.
import { mkdirSync } from 'node:fs'
import { chromium } from 'playwright'

const AUTH = process.env.AUTH_URL ?? 'http://localhost:9000'
const PINCODE = process.env.PINCODE_URL ?? 'http://127.0.0.1:5175/'
const POC = process.env.POC_URL ?? 'http://127.0.0.1:8000/'
const DEMO = process.env.DEMO_URL ?? 'http://127.0.0.1:8081/'
const EMAIL = process.env.E2E_EMAIL ?? 'asha@example.com'
const PASSWORD = process.env.E2E_PASSWORD ?? 'Password@123'
const SHOTS = new URL('./screenshots/', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')

mkdirSync(SHOTS, { recursive: true })
const step = (message) => console.log('✓', message)
const browser = await chromium.launch()
const context = await browser.newContext()
let failed = false

try {
  // 1. Frontend-only app, nobody signed in: the silent check comes back, the app offers sign-in
  const pincode = await context.newPage()
  await pincode.goto(PINCODE)
  await pincode.getByRole('button', { name: 'Sign in with AuthReflector' }).waitFor({ timeout: 15000 })
  step('Pincode: silent check finds nobody signed in and offers "Sign in with AuthReflector"')

  // 2. Sign in once, on AuthReflector's page, which names the app
  await pincode.getByRole('button', { name: 'Sign in with AuthReflector' }).click()
  await pincode.waitForURL(`${AUTH}/login`)
  const continueTo = (await pincode.getByText(/to continue to/).textContent()).trim()
  step(`AuthReflector sign-in page says "${continueTo}"`)
  await pincode.getByLabel('Email').fill(EMAIL)
  await pincode.getByLabel('Password').fill(PASSWORD)
  await pincode.getByRole('button', { name: 'Sign in' }).click()
  await pincode.getByText(/Signed in as/).waitFor({ timeout: 15000 })
  step(`Pincode: ${(await pincode.getByText(/Signed in as/).textContent()).trim()}`)
  await pincode.screenshot({ path: `${SHOTS}1-pincode.png` })

  // 3. SSO into a second frontend-only app: no password
  const poc = await context.newPage()
  await poc.goto(POC)
  await poc.getByText(/Signed in as/).waitFor({ timeout: 15000 })
  step('Embeddings POC: signed in without a password (SSO)')
  await poc.screenshot({ path: `${SHOTS}2-poc.png` })

  // 4. SSO into a Spring Boot app (client starter, BFF): no password, and its own API knows the user
  const demo = await context.newPage()
  await demo.goto(DEMO)
  await demo.locator('#greeting').waitFor({ timeout: 20000 })
  step(`Demo app (Spring starter): ${await demo.locator('#greeting').textContent()} (no password)`)
  const api = await demo.locator('pre').textContent()
  if (!api.includes('this came from the demo app')) throw new Error('demo app API did not answer for the signed-in user')
  step("Demo app's own API answered for the signed-in user")
  await demo.screenshot({ path: `${SHOTS}3-demo-app.png` })

  // 5. The launcher on AuthReflector: every app used so far glows
  const account = await context.newPage()
  await account.goto(`${AUTH}/account`)
  const tiles = account.getByRole('link', { name: /^Open / })
  await tiles.first().waitFor()
  const states = []
  for (const tile of await tiles.all()) states.push(`${await tile.getAttribute('data-app')}=${await tile.getAttribute('data-used') === 'true' ? 'glowing' : 'faded'}`)
  step(`Launcher: ${states.join(', ')}`)
  await account.screenshot({ path: `${SHOTS}4-launcher.png`, fullPage: true })

  // 6. Single sign-out from the Spring app ends the AuthReflector session as well
  await demo.getByRole('button', { name: 'Sign out' }).click()
  await demo.waitForURL(DEMO, { timeout: 20000 })
  await demo.getByText(/You are not signed in/).waitFor({ timeout: 20000 })
  step('Demo app: signed out and back on its own page')
  await account.goto(`${AUTH}/account`)
  await account.waitForURL(`${AUTH}/login`, { timeout: 15000 })
  step('AuthReflector session ended too (single sign-out)')

  console.log('\nRESULT: PASS')
} catch (error) {
  failed = true
  console.log('\nRESULT: FAIL:', error.message)
  for (const [index, page] of context.pages().entries()) {
    await page.screenshot({ path: `${SHOTS}failure-${index}.png` }).catch(() => {})
  }
} finally {
  await browser.close()
  process.exitCode = failed ? 1 : 0
}
