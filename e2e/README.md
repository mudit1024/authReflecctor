# End-to-end SSO test (real browser)

`sso.e2e.mjs` drives a real Chromium through single sign-on across the three ways an app can use AuthReflector:

| App | Integration | Address |
|---|---|---|
| What is my Pincode | React + JS SDK (frontend only) | http://127.0.0.1:5175 |
| Embeddings POC | plain HTML + JS SDK (frontend only) | http://127.0.0.1:8000 |
| Demo app (`../demo-app`) | Spring Boot + client starter (BFF) | http://127.0.0.1:8081 |

What it checks:
1. Pincode silently finds nobody signed in and offers "Sign in with AuthReflector".
2. AuthReflector's sign-in page names the app ("to continue to What is my Pincode"); sign in once.
3. The embeddings POC signs in **without a password**.
4. The demo app signs in **without a password**, and its own API answers for the user.
5. The launcher on `/account` shows the used apps glowing.
6. Signing out in the demo app also ends the AuthReflector session (single sign-out).

Screenshots of each step are saved in `screenshots/` (git-ignored).

## Running it

It needs the apps running; it doesn't start them itself (they live in different repositories).

```bash
# 1. AuthReflector with its UI and SDK
docker compose up -d                                   # from the repo root
(cd backend && ./mvnw -Pui package -DskipTests && java -jar target/authreflector-0.0.1-SNAPSHOT.jar)

# 2. Once: an account, admin rights, and the three apps registered on /admin/apps
#    pincode   (Frontend only)  redirect + after-sign-out + home: http://127.0.0.1:5175/
#    embed-poc (Frontend only)  redirect: http://127.0.0.1:8000/  home: http://127.0.0.1:8000/
#    demo-app  (With backend)   redirect: http://127.0.0.1:8081/login/oauth2/code/authreflector
#                               after-sign-out + home: http://127.0.0.1:8081/   audience: demo-api

# 3. The apps
(cd ../demo-app && DEMO_APP_CLIENT_SECRET=<secret from the admin page> ./mvnw spring-boot:run)
(cd <pincode repo> && npx vite)                       # http://127.0.0.1:5175
(cd <poc folder> && python -m http.server 8000 --bind 127.0.0.1)

# 4. The test
cd e2e
npm install
npx playwright install chromium                       # once
E2E_EMAIL=you@example.com E2E_PASSWORD=... npm test
```

Addresses and the account can be overridden with `AUTH_URL`, `PINCODE_URL`, `POC_URL`, `DEMO_URL`, `E2E_EMAIL` and `E2E_PASSWORD`.

## Why it isn't in CI (yet)

It spans three repositories and needs a browser plus a database. Every ticket's own tests run in CI; this suite is the
release check across apps. It already found three bugs no unit or integration test had a case for: `prompt=none`
showing a login page, single sign-out failing on `sid`, and the starter returning to an API call after sign-in.
