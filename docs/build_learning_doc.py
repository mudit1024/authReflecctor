"""Generates the AuthReflector learning guide (concepts, code map, dry runs). Usage: python build_learning_doc.py <output_path>"""
import sys

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Pt, RGBColor, Cm

OUT = sys.argv[1]
doc = Document()

# ---------- base styles (same look as the SplitMate design doc) ----------
for section in doc.sections:
    section.left_margin = section.right_margin = Cm(2.2)
    section.top_margin = section.bottom_margin = Cm(2.0)

normal = doc.styles["Normal"]
normal.font.name = "Calibri"
normal.font.size = Pt(11)
normal.paragraph_format.space_after = Pt(6)

ACCENT = RGBColor(0x1F, 0x4E, 0x79)
for lvl, size in ((1, 18), (2, 14), (3, 12)):
    st = doc.styles[f"Heading {lvl}"]
    st.font.name = "Calibri"
    st.font.size = Pt(size)
    st.font.color.rgb = ACCENT
    st.font.bold = True


def shade(cell, hex_fill):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), hex_fill)
    tc_pr.append(shd)


def h(text, level=1):
    doc.add_heading(text, level=level)


def p(text, bold_prefix=None, italic=False):
    para = doc.add_paragraph()
    if bold_prefix:
        para.add_run(bold_prefix).bold = True
    run = para.add_run(text)
    run.italic = italic
    return para


def bullets(items, style="List Bullet"):
    for item in items:
        para = doc.add_paragraph(style=style)
        if isinstance(item, tuple):
            para.add_run(item[0]).bold = True
            para.add_run(item[1])
        else:
            para.add_run(item)


def numbered(items):
    bullets(items, style="List Number")


def table(headers, rows, widths=None):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    for i, head in enumerate(headers):
        cell = t.rows[0].cells[i]
        cell.text = ""
        run = cell.paragraphs[0].add_run(head)
        run.bold = True
        run.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
        shade(cell, "1F4E79")
    for r_idx, row in enumerate(rows):
        cells = t.add_row().cells
        for i, val in enumerate(row):
            cells[i].text = str(val)
            if r_idx % 2 == 1:
                shade(cells[i], "EAF1F8")
    for row in t.rows:
        for cell in row.cells:
            for para in cell.paragraphs:
                para.paragraph_format.space_after = Pt(2)
                for run in para.runs:
                    run.font.size = Pt(9.5)
    if widths:
        for row in t.rows:
            for i, w in enumerate(widths):
                row.cells[i].width = Cm(w)
    doc.add_paragraph()
    return t


def code(text):
    for line in text.split("\n"):
        para = doc.add_paragraph()
        para.paragraph_format.left_indent = Cm(0.5)
        para.paragraph_format.space_after = Pt(0)
        run = para.add_run(line if line else " ")
        run.font.name = "Consolas"
        run._element.rPr.rFonts.set(qn("w:eastAsia"), "Consolas")
        run.font.size = Pt(9)
        pPr = para._p.get_or_add_pPr()
        shd = OxmlElement("w:shd")
        shd.set(qn("w:val"), "clear")
        shd.set(qn("w:color"), "auto")
        shd.set(qn("w:fill"), "F2F2F2")
        pPr.append(shd)
    doc.add_paragraph()


def callout(label, text):
    t = doc.add_table(rows=1, cols=1)
    t.style = "Table Grid"
    cell = t.rows[0].cells[0]
    shade(cell, "FFF4D6")
    cell.text = ""
    para = cell.paragraphs[0]
    para.add_run(label + " ").bold = True
    para.add_run(text)
    doc.add_paragraph()



def step_table(rows):
    """Dry-run steps: (#, where in the code, what happens)."""
    table(["#", "Where", "What happens"], rows, widths=[0.8, 4.6, 10.1])


def fail_table(rows):
    """Failure cases: (case, what is sent / state, what happens, response)."""
    table(["Case", "You send / state", "What happens (code)", "Response"], rows, widths=[3.0, 3.6, 5.6, 3.3])


# =====================================================================
# TITLE
# =====================================================================
title = doc.add_paragraph()
title.alignment = WD_ALIGN_PARAGRAPH.CENTER
title.paragraph_format.space_before = Pt(110)
r = title.add_run("AuthReflector")
r.bold = True
r.font.size = Pt(36)
r.font.color.rgb = ACCENT
sub = doc.add_paragraph()
sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = sub.add_run("Learning Guide: how single sign-on works, and how we built it")
r.font.size = Pt(16)
tag = doc.add_paragraph()
tag.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = tag.add_run("Concepts, code map and step-by-step dry runs, including every failure case")
r.italic = True
r.font.size = Pt(12)
doc.add_paragraph()
table(["Field", "Value"], [
    ["Covers", "AuthReflector main branch after A1 (PRs #1-#17), client starter 0.1.1, JS SDK, demo app, SplitMate PR #5, Pincode PR #1"],
    ["Date", "14 November 2026"],
    ["Companion", "AuthReflector-Design-Doc-v0.4.docx (what and why); this guide is how it actually runs"],
    ["How to use it", "Read section 1 once. Then pick a flow in section 4 and follow it in the code (file names are given) "
                      "or replay it with the curl commands in section 8."],
], widths=[3.5, 12])
doc.add_page_break()

h("Contents")
numbered([
    "Concepts in plain words",
    "The big picture: components, addresses, who talks to whom",
    "Code map: where everything lives",
    "Dry runs (happy path + failure cases for each)",
    "Attacks and the defence that stops each one",
    "Bugs we hit while building, and the lesson from each",
    "Known gaps and to-dos",
    "Hands-on: replay the flows yourself",
    "Interview questions with short answers",
])
doc.add_page_break()

# =====================================================================
# 1. CONCEPTS
# =====================================================================
h("1. Concepts in plain words")
p("Every concept below appears in the dry runs. Each entry says what it is and why it exists, because the "
  "'why' is what interviews and debugging both need.")
table(["Concept", "What it is", "Why it exists"], [
    ["Authentication vs authorization", "Authentication = who are you (sign-in). Authorization = what may you do (roles, rules).",
     "AuthReflector does authentication for every app and supplies roles; each app still decides what a user may do with its own data."],
    ["Identity provider (IdP)", "The one service that knows accounts and passwords: AuthReflector.",
     "Apps never see passwords. One place to secure, one account per person."],
    ["Single sign-on (SSO)", "Sign in once at the IdP; every app can sign you in without asking again.",
     "Works because the IdP keeps its own session (cookie AR_SESSION). Apps just ask the IdP 'who is this?'."],
    ["OAuth 2.0", "A protocol for an app to obtain tokens from an authorization server, with the user's approval.",
     "Standard flows and endpoints, so any app can integrate with off-the-shelf libraries."],
    ["OpenID Connect (OIDC)", "Identity layer on top of OAuth 2.0: adds the ID token, /userinfo, discovery, logout.",
     "Plain OAuth says 'this app may access X'; OIDC adds 'and this is who signed in'."],
    ["Client (app) / client id / secret", "An app registered with the IdP. Web apps with a backend get a secret; browser-only apps can't keep one.",
     "The IdP only hands codes and tokens to known apps, at their registered addresses."],
    ["Authorization Code flow", "Browser goes to the IdP, comes back with a one-time code; the app exchanges the code for tokens.",
     "Tokens never travel in the browser address bar; the code is useless without the next step."],
    ["PKCE", "App invents a secret 'verifier', sends only its SHA-256 hash ('challenge') at the start, and the verifier at code exchange.",
     "A stolen code can't be exchanged without the verifier. Required for every app in AuthReflector."],
    ["state / nonce", "Random values the app sends and later checks: state on the redirect back, nonce inside the ID token.",
     "state stops forged sign-in responses (CSRF on login); nonce stops replayed ID tokens."],
    ["Redirect URI", "Exact address the IdP sends the code to. Must be pre-registered.",
     "Otherwise an attacker could ask the IdP to send your code to their server."],
    ["JWT", "A signed JSON token: header.payload.signature (Base64URL). Anyone can read it; only the key holder can create it.",
     "Apps verify tokens locally, without calling AuthReflector on every request."],
    ["RS256, key pair, kid, JWKS", "AuthReflector signs with its private RSA key; apps verify with the public key, published at /oauth2/jwks. 'kid' names which key signed.",
     "Apps can verify but never forge tokens. kid makes key rotation possible."],
    ["Access token / ID token / refresh token", "Access: for calling the app's API (15 min). ID: tells the app who signed in. Refresh: gets new tokens without the user (30 days, rotates).",
     "Short-lived access tokens limit damage if leaked; refresh avoids constant sign-ins."],
    ["Claims: sub, aud, iss, exp, roles", "sub = stable user id (UUID). aud = who the token is for. iss = who issued it. exp = expiry. roles = roles in THIS app.",
     "sub is every app's user key. aud stops a SplitMate token working at CodeQuror."],
    ["Session + cookie", "Server-side state found via a cookie (AR_SESSION at AuthReflector, <APP>_SESSION in apps using the starter).",
     "HttpOnly cookies can't be read by JavaScript, unlike tokens in localStorage."],
    ["CSRF", "Another site making your browser send a request with your cookies. Defence: a token the attacker can't read, sent in a header.",
     "Needed wherever authentication is a cookie (AuthReflector pages, BFF apps). Not needed for bearer tokens."],
    ["BFF (backend for frontend)", "The app's own backend does the OIDC sign-in and keeps the tokens; the browser only has a session cookie.",
     "Tokens never reach JavaScript (SplitMate decision D35). Used by the Spring starter."],
    ["Public client / SPA", "Browser-only app (no backend). No secret; PKCE protects it. Uses our JS SDK.",
     "Pincode and the POC have no server; they can still use SSO."],
    ["Silent check (prompt=none)", "Ask the IdP 'is someone signed in?' without ever showing a page.",
     "Opening an app from the launcher signs you in quietly; if not signed in, the app decides what to show."],
    ["RP-initiated logout", "The app (relying party) sends the browser to /connect/logout with its ID token.",
     "Signing out of one app ends the AuthReflector session too (single sign-out)."],
    ["CORS", "Browser rule: a page may only read responses from another origin if that origin allows it.",
     "SDK apps call AuthReflector's token endpoint from their own origin; we allow exactly the registered ones."],
    ["Flyway migration", "Versioned SQL files (V1..V7) applied in order at startup.",
     "The schema only changes through reviewed files; Hibernate is set to validate, never modify."],
], widths=[3.2, 6.3, 6])

# =====================================================================
# 2. BIG PICTURE
# =====================================================================
h("2. The big picture")
code(
    "                                   +--------------------------------------------+\n"
    "  Browser                          |  AuthReflector  http://localhost:9000      |\n"
    "  (one AR_SESSION cookie   <-----> |  React pages: /login /register /account    |\n"
    "   = the SSO session)              |            /admin/apps                     |\n"
    "                                   |  Chain 1 (OIDC): /oauth2/* /.well-known/*  |\n"
    "                                   |            /userinfo /connect/logout       |\n"
    "                                   |  Chain 2 (own API): /login /logout /api/v1 |\n"
    "                                   |  /sdk/authreflector-web.js                 |\n"
    "                                   +----------------------+---------------------+\n"
    "                                                          |\n"
    "                                         PostgreSQL :5434 (users, apps, authorizations, usage)\n"
    "\n"
    "  Apps (each registered on /admin/apps):\n"
    "   Spring + starter (BFF)    SplitMate 127.0.0.1:5173 -> :8080,  demo app 127.0.0.1:8081\n"
    "   Browser + JS SDK          Pincode 127.0.0.1:5175,  embeddings POC 127.0.0.1:8000"
)
table(["Who", "Talks to", "How"], [
    ["Browser → AuthReflector", "sign-in pages, /oauth2/authorize, /connect/logout", "top-level navigation (cookies sent)"],
    ["Spring app (server) → AuthReflector", "/oauth2/token (code → tokens), /oauth2/jwks (public keys)", "server-to-server HTTPS, client secret"],
    ["SDK app (browser JS) → AuthReflector", "/oauth2/token", "fetch() cross-origin: needs CORS"],
    ["Mobile / API client → Spring app", "/api/** with Authorization: Bearer <access token>", "token verified locally in the app"],
], widths=[4.5, 6, 5])

# =====================================================================
# 3. CODE MAP
# =====================================================================
h("3. Code map")
h("3.1 AuthReflector backend (backend/src/main/java/com/authreflector)", 2)
table(["Package / file", "Responsibility"], [
    ["AuthreflectorApplication", "Entry point. Sets the JVM to UTC, then starts Spring Boot."],
    ["config/SigningKeyProperties, SigningKeyConfig, security/SigningKeys",
     "Loads the RSA key pair (PEM), checks the pair matches, exposes JWKSource + JwtEncoder."],
    ["config/ClockConfig, PasswordEncoderConfig", "Injectable UTC clock; DelegatingPasswordEncoder ({bcrypt} hashes)."],
    ["identity/User, Role, UserRepository", "Accounts (table users). id = the 'sub' every app stores."],
    ["identity/RegistrationController, RegistrationService, RegistrationRequest",
     "Sign-up: validation, normalising, duplicate check, hashing, then auto sign-in."],
    ["security/SecurityConfig", "Filter chain 2 (order 2): our pages + API. Session, CSRF cookie, form login, logout, headers, session registry."],
    ["security/IdentityUserDetailsService, AuthenticatedUser", "How Spring finds an account by email; the signed-in principal kept in the session."],
    ["security/JsonAuthenticationHandlers", "JSON answers instead of HTML redirects: login ok/failed, 401, 403."],
    ["security/SessionLogin, PostLoginRedirect", "Sign-in from code (after sign-up); where to go next (saved authorize request or /account)."],
    ["security/AccountController", "GET /api/v1/me, GET /api/v1/auth/csrf."],
    ["oidc/AuthorizationServerConfig", "Filter chain 1 (order 1): Spring Authorization Server endpoints; JDBC stores; issuer; CORS bean."],
    ["oidc/TokenClaimsCustomizer, ClientRoles, ClientDefaults", "What tokens contain (sub, email, name, roles, aud); safe defaults for new apps."],
    ["oidc/ReuseDetectingAuthorizationService", "Refresh token reuse detection around the JDBC store."],
    ["oidc/AuthorizationJson, LoginContextController", "Allowlisted JSON for stored authorizations; 'to continue to <app>'."],
    ["apps/AppRegistry, AdminAppsController, ClientApp, UrlRules, EnabledAppsClientRepository, SpaOriginsCorsConfigurationSource",
     "Admin: register/edit/disable apps at runtime; URL rules; disabled apps invisible; CORS for SDK apps."],
    ["launcher/UsageRecordingAuthorizationService, LauncherController", "Counts sign-ins per app per day; serves the launcher list."],
    ["web/GlobalExceptionHandler, ErrorResponse, JsonErrorWriter, ApiException, SpaController",
     "One JSON error format everywhere; React routes served as index.html."],
    ["resources/db/migration V1..V7", "V2 users, V3 Spring Authorization Server tables, V4 client_roles, V5 rotated_refresh_tokens, V6 client_apps, V7 user_app_daily_usage."],
], widths=[6.5, 9])
h("3.2 Everything else", 2)
table(["Folder", "What it is"], [
    ["frontend/", "React + TypeScript login UI served by AuthReflector: pages Login, Register, Account (launcher), admin Apps; api/client.ts adds CSRF automatically."],
    ["sdk/web/", "JS SDK for browser-only apps (create, init, signIn, signOut, getAccessToken), built on oidc-client-ts."],
    ["client/", "Spring Boot client starter (JitPack com.github.mudit1024:authReflector): AuthReflectorClientAutoConfiguration does everything."],
    ["demo-app/", "Template app using the starter: one controller, one page, four config lines."],
    ["e2e/", "Real-browser Playwright test across SDK and starter apps."],
    ["docs/", "Design doc and this guide (both generated by Python scripts)."],
], widths=[3, 12.5])
doc.add_page_break()

# =====================================================================
# 4. DRY RUNS
# =====================================================================
h("4. Dry runs")
p("Notation: → means 'calls' or 'passes to'. File paths are under backend/src/main/java/com/authreflector unless stated. "
  "'Chain 1' is the OIDC filter chain (AuthorizationServerConfig), 'chain 2' is ours (SecurityConfig). Spring picks the "
  "first chain whose matcher fits the request; chain 1 only matches the OIDC endpoints.")

# ---------- 4.1 startup
h("4.1 Application startup (entry point: AuthreflectorApplication.main)", 2)
step_table([
    ["1", "AuthreflectorApplication.main", "useUtc(): TimeZone.setDefault(UTC). All expiry and audit times are UTC regardless of the machine."],
    ["2", "SpringApplication.run", "Reads application.yaml, then application-local.yaml (profile 'local' is the default when none is set)."],
    ["3", "DataSource + Flyway", "Connects to PostgreSQL (local: localhost:5434). Flyway applies V1..V7 if not yet applied; records them in flyway_schema_history."],
    ["4", "Hibernate (ddl-auto: validate)", "Checks every @Entity matches the tables Flyway created. Never alters tables."],
    ["5", "SigningKeyConfig.signingKey → SigningKeys.load", "Reads public + private PEM, checks they belong together, builds an RSAKey with kid (dev-key-1) and RS256."],
    ["6", "Beans", "JWKSource, JwtEncoder, RegisteredClientRepository (EnabledApps → JDBC), OAuth2AuthorizationService (UsageRecording → ReuseDetecting → JDBC), SessionRegistry, CORS source, ..."],
    ["7", "Filter chains", "Chain 1 (order 1) for OIDC endpoints; chain 2 (order 2) for everything else."],
    ["8", "Tomcat on :9000", "/actuator/health answers {\"status\":\"UP\"}."],
])
fail_table([
    ["Key file missing", "AUTH_SIGNING_PRIVATE_KEY points nowhere", "SigningKeys.read: location doesn't exist → IllegalStateException naming the path and 'generate-dev-keys'", "App refuses to start"],
    ["Public/private mismatch", "public key of another pair", "SigningKeys.requireMatchingPair: moduli differ", "App refuses to start ('do not belong together')"],
    ["Not a PEM", "a YAML file as key", "RsaKeyConverters fails → IllegalStateException with the file name", "App refuses to start"],
    ["Entity ≠ schema", "e.g. char(7) column for a String", "Hibernate validate: 'wrong column type'", "App refuses to start (bug we hit, V6)"],
    ["Database time zone", "JVM zone 'Asia/Calcutta'", "PostgreSQL 17 rejects the session time zone", "Prevented by step 1 (UTC)"],
    ["Port clash", "Docker Postgres on 5433", "Native Windows Postgres already listens: app connects to the wrong DB", "'password authentication failed' (why we use 5434)"],
])

# ---------- 4.2 registration
h("4.2 Sign-up (entry point: React /register → POST /api/v1/auth/register)", 2)
step_table([
    ["1", "frontend RegisterPage.tsx", "Live checklist (lib/passwordPolicy.ts mirrors the server rules). Button enabled only when name, email and password look valid."],
    ["2", "frontend api/client.ts postJson", "Reads cookie XSRF-TOKEN; if missing, GET /api/v1/auth/csrf first. Sends JSON with header X-XSRF-TOKEN."],
    ["3", "Chain 2: CsrfFilter", "Compares header with cookie (CookieCsrfTokenRepository). Equal → continue."],
    ["4", "Chain 2: AuthorizationFilter", "POST /api/v1/auth/register is permitAll."],
    ["5", "identity/RegistrationController.register", "@Valid RegistrationRequest: name ≤100, email valid ≤254, password 8-64 with lower/upper/digit/special."],
    ["6", "RegistrationService.register", "Trim name; trim + lowercase email (Locale.ROOT); existsByEmailIgnoreCase → if taken: EmailAlreadyRegisteredException."],
    ["7", "RegistrationService", "passwordEncoder.encode → '{bcrypt}$2a$10$...'; saveAndFlush(new User). Unique index on lower(email) is the final guard."],
    ["8", "security/SessionLogin.signIn", "AuthenticationManager re-checks email+password (proves the account works) → changeSessionId (fixation) → save SecurityContext in session → register session in SessionRegistry → new CSRF token."],
    ["9", "PostLoginRedirect.resolve", "If an app's authorize request was saved: its URL. Else '/account'."],
    ["10", "Response", "201 {name, email, redirectUrl}. React calls goTo(redirectUrl) (full page navigation)."],
])
fail_table([
    ["No CSRF token", "POST without X-XSRF-TOKEN", "CsrfFilter → JsonAuthenticationHandlers.handle (CsrfException)", "403 'Invalid or missing CSRF token'"],
    ["Weak password", "password 'weak'", "Bean Validation → GlobalExceptionHandler.handleValidation", "400 + fieldErrors (React shows them per field)"],
    ["Email taken (any case)", "Rahul@Example.com when rahul@example.com exists", "existsByEmailIgnoreCase → EmailAlreadyRegisteredException → handler", "409 'Email is already registered' (email not echoed)"],
    ["Two sign-ups at the same instant", "same email twice in parallel", "Both pass the check; DB unique index rejects one → DataIntegrityViolationException → 409", "One 201, one 409, never 500"],
    ["Broken JSON", "{\"name\": \"x\",", "HttpMessageNotReadableException", "400 'Malformed JSON request'"],
    ["Known gap", "an attacker probing emails", "409 reveals the email has an account", "Fix planned in A2 (email verification)"],
])

# ---------- 4.3 sign-in
h("4.3 Sign-in (entry point: React /login → POST /login)", 2)
step_table([
    ["1", "frontend LoginPage.tsx", "useLoginContext() → GET /api/v1/auth/login-context → shows 'to continue to <app>' if an app is waiting."],
    ["2", "api/client.ts postForm", "Form-encoded email + password, header X-XSRF-TOKEN."],
    ["3", "Chain 2: CsrfFilter", "Token check, as in 4.2."],
    ["4", "UsernamePasswordAuthenticationFilter", "Configured by formLogin(): processing URL /login, parameter names email/password."],
    ["5", "ProviderManager → DaoAuthenticationProvider", "IdentityUserDetailsService.loadUserByUsername(email) → findByEmailIgnoreCase → AuthenticatedUser. PasswordEncoder.matches(typed, {bcrypt} hash)."],
    ["6", "Session strategies", "changeSessionId (fixation protection), register in SessionRegistry (needed for logout 'sid'), CSRF token rotated, credentials erased from the principal."],
    ["7", "JsonAuthenticationHandlers.onAuthenticationSuccess", "200 {redirectUrl} from PostLoginRedirect (saved authorize URL or /account)."],
    ["8", "React", "goTo(redirectUrl). Cookie AR_SESSION (HttpOnly, SameSite=Lax) now identifies the SSO session."],
])
fail_table([
    ["Wrong password", "valid email, wrong password", "BadCredentialsException → onAuthenticationFailure", "401 'Invalid email or password'"],
    ["Unknown email", "nobody@example.com", "UsernameNotFoundException is hidden as bad credentials; a dummy hash check keeps timing similar", "Same 401, same message (no account probing)"],
    ["Missing / forged CSRF", "no header, or header ≠ cookie", "CsrfFilter", "403"],
    ["Session fixation attempt", "attacker plants a session id before login", "changeSessionId gives a new id after login", "Attacker's id is not signed in"],
    ["Not yet built", "thousands of guesses", "no rate limit yet", "A2 adds 5 failures / 15 min per IP and email → 429"],
])

# ---------- 4.4 BFF SSO
h("4.4 SSO into a Spring app (entry point: browser opens SplitMate or the demo app)", 2)
p("The longest flow, and the heart of SSO. Two servers take part: the app (with the client starter) and AuthReflector. "
  "Example: demo app at http://127.0.0.1:8081, not signed in anywhere yet.")
step_table([
    ["1", "demo-app index.html", "fetch('/auth/me') → 401 (starter entry point: /auth/* gets JSON). Page navigates to /auth/login."],
    ["2", "client: AuthEndpoints.login", "302 to /oauth2/authorization/authreflector."],
    ["3", "client: OAuth2AuthorizationRequestRedirectFilter + PKCE resolver", "Creates state, nonce, code_verifier; stores them in the APP's session (DEMO_APP_SESSION); 302 to http://localhost:9000/oauth2/authorize?client_id=demo-app&redirect_uri=http://127.0.0.1:8081/login/oauth2/code/authreflector&scope=openid profile email&code_challenge=...&code_challenge_method=S256&state=...&nonce=..."],
    ["4", "AuthReflector chain 1", "isSilentAuthorizationRequest? No (no prompt=none). AuthorizationFilter: anonymous → AccessDenied."],
    ["5", "ExceptionTranslationFilter", "RequestCache (matcher: GET /oauth2/authorize) saves the URL in AR's session; LoginUrlAuthenticationEntryPoint → 302 /login."],
    ["6", "React /login", "login-context reads the saved request's client_id → 'to continue to Demo App'. User signs in (flow 4.3)."],
    ["7", "JsonAuthenticationHandlers", "redirectUrl = the saved authorize URL (PostLoginRedirect removes it from the session). React goTo → browser repeats step 3's URL."],
    ["8", "Chain 1: OAuth2AuthorizationEndpointFilter", "EnabledAppsClientRepository.findByClientId('demo-app') (null if disabled). Validates: redirect_uri exactly registered, response_type=code, PKCE present (requireProofKey), scopes allowed. Consent skipped (our app)."],
    ["9", "Authorization code", "Random code saved in oauth2_authorization (with principal JSON via AuthorizationJson). 302 to http://127.0.0.1:8081/login/oauth2/code/authreflector?code=...&state=..."],
    ["10", "client: OAuth2LoginAuthenticationFilter", "Checks state matches the one in the app session. Server-to-server POST /oauth2/token: grant_type=authorization_code, code, redirect_uri, code_verifier, Basic auth demo-app:<secret>."],
    ["11", "Chain 1: OAuth2ClientAuthenticationFilter", "Client secret checked against the {bcrypt} hash in oauth2_registered_client."],
    ["12", "OAuth2AuthorizationCodeAuthenticationProvider", "Finds the code, checks not used / not expired / same client / same redirect_uri; verifies SHA-256(code_verifier) == code_challenge (PKCE)."],
    ["13", "JwtGenerator → oidc/TokenClaimsCustomizer", "Access token: sub = user UUID, email, name, roles (ClientRoles: USER + client_roles), aud = demo-api, exp = +15 min. ID token: same claims, aud = demo-app, nonce, sid. Signed RS256 with kid. Refresh token: random, 30 days."],
    ["14", "Store: UsageRecording → ReuseDetecting → JDBC", "Authorization saved with tokens. First tokens for an authorization-code sign-in → user_app_daily_usage +1 (launcher)."],
    ["15", "client: OidcAuthorizationCodeAuthenticationProvider", "Validates the ID token: signature via /oauth2/jwks (public key, kid), iss, aud contains demo-app, exp, nonce. Builds OidcUser; roles → ROLE_USER etc."],
    ["16", "client: session + events", "Authentication stored in DEMO_APP_SESSION; InteractiveAuthenticationSuccessEvent → AuthReflectorSignInEvent (SplitMate: UserProvisioning upserts its users row)."],
    ["17", "client: redirect", "pagesOnlyRequestCache: back to the page originally requested, else '/'. Page fetches /auth/me → 200 user JSON."],
])
fail_table([
    ["Unregistered redirect_uri", "redirect_uri=https://attacker.example/cb", "Endpoint validation: redirect URI not registered; never redirects there", "400 at AuthReflector"],
    ["localhost instead of 127.0.0.1", "app opened at http://localhost:8081", "{baseUrl} becomes localhost → not the registered URI", "400 (open apps at 127.0.0.1)"],
    ["Proxy rewrote Host", "Vite proxy with changeOrigin", "redirect_uri built with :8080 instead of :5173 → not registered", "400 / lands on backend (bug we hit; changeOrigin: false)"],
    ["Unknown or disabled app", "client_id=no-such-app / disabled app", "EnabledAppsClientRepository returns null", "400"],
    ["No PKCE", "authorize without code_challenge", "requireProofKey", "302 back to the app with error=invalid_request"],
    ["Wrong code_verifier", "attacker has the code but not the verifier", "PKCE check in step 12", "400 invalid_grant"],
    ["Code used twice", "replaying a code", "Code already invalidated", "400 invalid_grant"],
    ["Wrong client secret", "Basic demo-app:wrong", "Client authentication fails (step 11)", "401 invalid_client"],
    ["state mismatch", "forged callback", "OAuth2LoginAuthenticationFilter in the app", "App's /login?error (no session created)"],
    ["Tampered / foreign ID token", "signature or aud doesn't match", "Step 15 validation", "Sign-in fails in the app"],
])

# ---------- 4.5 SSO second app
h("4.5 SSO into a second app (entry point: open another app after 4.4)", 2)
step_table([
    ["1", "Second app", "Same as 4.4 steps 1-3, with its own client_id and redirect_uri."],
    ["2", "AuthReflector chain 1", "AR_SESSION cookie is present: the request is authenticated. No login page."],
    ["3", "Authorization endpoint", "Code issued immediately (steps 8-9 of 4.4); the rest is identical."],
    ["Result", "User", "Signed in to the second app without typing anything: this is single sign-on."],
])

# ---------- 4.6 SDK
h("4.6 Silent check and sign-in for a browser-only app (entry point: Pincode loads, JS SDK)", 2)
step_table([
    ["1", "pincode src/auth/authReflector.js", "Loads http://localhost:9000/sdk/authreflector-web.js; AuthReflector.create({issuer, clientId: 'pincode'}); auth.init() once (singleton: StrictMode)."],
    ["2", "sdk/web/src/index.ts init", "URL has no code/state; sessionStorage has no valid user; SILENT_TRIED not set → set it → signinRedirect({prompt: 'none'})."],
    ["3", "AuthReflector chain 1", "isSilentAuthorizationRequest → permitAll so the endpoint itself can answer (Spring Security 7 runs the authorization rule first)."],
    ["4a", "Not signed in", "Endpoint: principal not authenticated + prompt=none → 302 to http://127.0.0.1:5175/?error=login_required&state=..."],
    ["5a", "SDK handleCallback", "signinRedirectCallback throws login_required → clean URL, keep SILENT_TRIED → user null → app shows 'Sign in with AuthReflector'."],
    ["4b", "Signed in", "Code issued as in 4.4 → 302 back with ?code=..."],
    ["5b", "SDK → POST /oauth2/token (cross-origin)", "Browser preflight OPTIONS → SpaOriginsCorsConfigurationSource: origin 127.0.0.1:5175 is a registered SPA redirect origin → allowed. Token request with client_id + code_verifier, no secret (public client)."],
    ["6b", "Tokens", "Access + ID token, no refresh token. SDK stores the user in sessionStorage (this tab), cleans the URL, user = {id, name, email, roles}."],
])
fail_table([
    ["Origin not registered", "page served from another origin", "CORS source returns null → no Access-Control-Allow-Origin", "Browser blocks the token response"],
    ["Endless redirects", "silent check keeps failing", "SILENT_TRIED in sessionStorage stops a second attempt", "Signed-out UI instead of a loop"],
    ["Signed out on purpose", "user clicked Sign out", "signOut sets SILENT_TRIED", "No automatic re-sign-in on the next load"],
    ["Bug we hit", "prompt=none, not signed in", "Before the fix: AuthorizationFilter → login page", "Fixed: login_required"],
    ["OPEN (AUTH-22)", "use Pincode while signed out", "Sign-in is optional in the UI", "Must become required; client-side only for apps without a backend"],
])

# ---------- 4.7 bearer
h("4.7 Bearer API call (entry point: mobile app calls GET /api/hello with Authorization: Bearer)", 2)
step_table([
    ["1", "client starter: BearerTokenAuthenticationFilter", "Sees 'Bearer ...'. (CSRF skipped: bearer requests carry no cookies.)"],
    ["2", "JwtDecoder (NimbusJwtDecoder.withJwkSetUri)", "Reads kid from the header; downloads /oauth2/jwks once and caches it; verifies the RS256 signature."],
    ["3", "AuthReflectorTokenValidators", "exp/nbf with 60 s clock skew; iss == issuer-uri; aud contains this app's audience (e.g. demo-api)."],
    ["4", "JwtAuthenticationConverter", "roles claim → ROLE_USER, ROLE_ADMIN."],
    ["5", "Controller", "Parameter AuthReflectorUser resolved from the JWT claims → 200."],
])
fail_table([
    ["Token for another app", "aud = codequror-api", "Audience validator fails", "401"],
    ["Expired", "exp more than 60 s ago", "Timestamp validator", "401"],
    ["Other issuer", "iss = https://evil.example", "Issuer validator", "401"],
    ["Tampered payload", "roles changed to ADMIN", "Signature no longer matches", "401"],
    ["No token", "plain request to /api/**", "Starter entry point", "401 JSON 'Sign in required'"],
])

# ---------- 4.8 refresh
h("4.8 Refresh and reuse detection (entry point: app POSTs grant_type=refresh_token)", 2)
step_table([
    ["1", "Chain 1 token endpoint", "Client authenticated; ReuseDetecting.findByToken(R1, REFRESH_TOKEN) → JDBC finds the authorization."],
    ["2", "Refresh provider", "R1 active, not expired → new access token, ID token and refresh token R2 (reuseRefreshTokens = false)."],
    ["3", "ReuseDetecting.save", "Previous refresh value R1 ≠ R2 → insert SHA-256(R1) into rotated_refresh_tokens; save authorization with R2."],
    ["4", "Usage recording", "Authorization already had an access token → not counted as a new sign-in."],
])
fail_table([
    ["Stolen R1 replayed", "R1 after rotation", "JDBC finds nothing → hash of R1 found in rotated_refresh_tokens → whole authorization removed (warning logged)", "400 invalid_grant"],
    ["Real user after theft", "R2 (current)", "Authorization is gone", "400 invalid_grant → user signs in again; thief is locked out"],
    ["Unknown token", "never issued", "No hash match → nothing revoked", "400 invalid_grant"],
])

# ---------- 4.9 logout
h("4.9 Single sign-out (entry point: Sign out button in the demo app)", 2)
step_table([
    ["1", "demo-app index.html", "GET /auth/csrf → POST /auth/logout with X-CSRF-TOKEN."],
    ["2", "client: LogoutFilter", "Ends DEMO_APP_SESSION; JsonOidcLogoutSuccessHandler builds http://localhost:9000/connect/logout?id_token_hint=<ID token>&post_logout_redirect_uri=http://127.0.0.1:8081/ and answers {logoutUrl}."],
    ["3", "Browser", "location.href = logoutUrl."],
    ["4", "Chain 1: OidcLogoutEndpointFilter", "Finds the authorization by the ID token; checks the post-logout URI is registered; finds the AR session by the token's 'sid' in the SessionRegistry."],
    ["5", "Logout", "AR session invalidated (AR_SESSION no longer valid) → 302 to http://127.0.0.1:8081/."],
])
fail_table([
    ["Unregistered post_logout_redirect_uri", "any other address", "Validation in the logout provider", "400"],
    ["Bug we hit", "no SessionRegistry", "'sid' couldn't be matched", "400 invalid_token (fixed: registry + session events)"],
    ["Known limit", "other apps' sessions", "Not told about the logout yet", "They stay signed in until their session or tokens expire; back-channel logout in A4"],
])

# ---------- 4.10 admin
h("4.10 Admin registers an app (entry point: /admin/apps/new → POST /api/v1/admin/apps)", 2)
step_table([
    ["1", "Chain 2", "/api/v1/admin/** requires ROLE_ADMIN (users.role = ADMIN, set only by database script)."],
    ["2", "AdminAppsController.create", "@Valid AppRequest: client id pattern, redirect URIs (UrlRules: https, or http on 127.0.0.1/[::1]), home URL, colour."],
    ["3", "AppRegistry.create", "Duplicate client id → 409. WEB: 32 random bytes → secret, stored {bcrypt}. SPA: no secret, auth method none."],
    ["4", "Save", "JdbcRegisteredClientRepository.save (Spring's table) + client_apps saveAndFlush (flush: the auth server reads it with plain JDBC)."],
    ["5", "Response", "201 {app, clientSecret}. The secret is never shown again. The app can sign users in immediately: no restart."],
])
fail_table([
    ["Not signed in / not admin", "no session / USER role", "Chain 2", "401 / 403"],
    ["Bad redirect URI", "http://evil.example/cb or localhost", "UrlRules", "400 fieldErrors.redirectUris"],
    ["Duplicate client id", "same id twice", "AppRegistry", "409"],
    ["Disable an app", "enabled=false", "client_apps.enabled=false; EnabledAppsClientRepository returns null; its authorizations deleted", "New sign-ins refused; refresh tokens dead"],
    ["Lost secret", "POST /{id}/secret", "New secret hashed; old one stops working", "200 {clientSecret}"],
])

# ---------- 4.11 launcher
h("4.11 Launcher (entry point: /account → GET /api/v1/launcher)", 2)
step_table([
    ["1", "AccountPage.tsx", "me() then launcherApps()."],
    ["2", "LauncherController", "Apps enabled + show_in_launcher + home_url, left-joined with this user's user_app_daily_usage."],
    ["3", "Order", "used first → sign-ins in the last 30 days → most recent → name."],
    ["4", "AppLauncher.tsx", "used = glowing tile in the app's colour; unused = faded and greyscale. Opens home URL in a new tab, where SSO signs the user in."],
])
doc.add_page_break()

# =====================================================================
# 5. ATTACKS
# =====================================================================
h("5. Attacks and the defence that stops each one")
table(["Attack", "Defence in AuthReflector", "Where"], [
    ["Steal passwords from the database", "Only {bcrypt} hashes (salted, slow)", "PasswordEncoderConfig"],
    ["Guess whether an email has an account (login)", "Same message and similar timing for unknown email and wrong password", "JsonAuthenticationHandlers, DaoAuthenticationProvider"],
    ["Cross-site request with the victim's cookies", "CSRF token in header, checked against cookie/session", "SecurityConfig, starter csrf()"],
    ["Session fixation", "New session id at sign-in", "formLogin + SessionLogin.changeSessionId"],
    ["Script reads the session", "HttpOnly cookies; tokens stay on servers (BFF)", "AR_SESSION, starter"],
    ["Clickjacking the login page", "frame-ancestors 'none', X-Frame-Options DENY", "SecurityConfig headers"],
    ["XSS loading foreign scripts", "Content-Security-Policy: 'self' only", "SecurityConfig headers"],
    ["Code sent to attacker's server", "Exact redirect URI match; strict URL rules", "Authorization endpoint, UrlRules"],
    ["Stolen authorization code", "PKCE; codes single-use and short-lived", "ClientDefaults (requireProofKey)"],
    ["Forged token", "RS256 signature with private key only AuthReflector has", "SigningKeys, JWKS"],
    ["Token from app A used at app B", "aud = app's API; validated by every API", "TokenClaimsCustomizer, AuthReflectorTokenValidators"],
    ["Admin in one app acting in another", "Roles per app (client_roles)", "ClientRoles"],
    ["Stolen refresh token", "Rotation + reuse detection revokes the family", "ReuseDetectingAuthorizationService"],
    ["Deserialisation gadget via stored JSON", "Jackson allowlist; only our principal added", "AuthorizationJson"],
    ["Foreign website calling the token endpoint", "CORS only for registered SPA origins", "SpaOriginsCorsConfigurationSource"],
    ["Brute force (not yet)", "Rate limiting planned", "A2"],
], widths=[5, 7, 3.5])

# =====================================================================
# 6. BUGS
# =====================================================================
h("6. Bugs we hit while building, and the lesson from each")
table(["Bug", "Cause", "Lesson"], [
    ["Postgres rejected the connection", "JVM time zone Asia/Calcutta (old alias)", "Servers run in UTC; set it in main()"],
    ["'password authentication failed'", "Native Windows Postgres already on 5433", "When a password 'suddenly' fails, check which server owns the port"],
    ["CSRF cookie missing in tests", "spring-security-test csrf() swaps the shared filter's repository", "Test the real protocol (cookie + header)"],
    ["/userinfo 400", "List.of(...) not on the JSON allowlist", "Allowlists are strict; use plain ArrayList in claims"],
    ["Stored principal unreadable", "Custom principal not allowlisted", "Extend Spring's allowlist by exactly one class"],
    ["Disabled app still signed in (test)", "JPA change not flushed before a JDBC read", "saveAndFlush when JPA and JDBC mix"],
    ["Hibernate validate failed", "char(7) vs String", "validate mode catches schema drift before production"],
    ["Silent check showed a login page", "Spring Security 7 authorizes before the OIDC endpoint", "Permit prompt=none explicitly; the endpoint answers"],
    ["Single sign-out 400", "No SessionRegistry for 'sid'", "OIDC logout needs session tracking"],
    ["Returned to raw JSON after sign-in", "Background fetch saved as the request to return to", "Request cache for pages only"],
    ["Wrong redirect port", "Vite proxy changeOrigin", "Keep the browser's Host behind a proxy"],
    ["CI 'Permission denied'", "mvnw not executable in Git (Windows)", "git update-index --chmod=+x for scripts"],
], widths=[4.3, 5.4, 5.8])
p("Four of these were found only by the real-browser E2E test: unit and integration tests had no case for them. "
  "That is why the e2e suite exists.", italic=True)

# =====================================================================
# 7. GAPS
# =====================================================================
h("7. Known gaps and to-dos")
table(["Item", "Ticket / phase"], [
    ["Pincode and POC usable without signing in: make sign-in required", "AUTH-22 (Bug)"],
    ["Registration 409 reveals existing emails", "A2: email verification"],
    ["No rate limiting on sign-in", "A2"],
    ["Forgot password, audit log, security emails", "A2"],
    ["Google and GitHub sign-in", "A3"],
    ["Other apps not told about sign-out (back-channel logout); account page with sessions", "A4"],
    ["Token values stored in clear in oauth2_authorization (hash at rest)", "A5"],
    ["Sessions in memory (lost on restart); Redis for two instances", "A5"],
], widths=[11, 4.5])

# =====================================================================
# 8. HANDS-ON
# =====================================================================
h("8. Hands-on: replay the flows yourself")
p("With AuthReflector running locally (Git Bash). A cookie jar file plays the browser.")
code(
    "J=jar.txt; rm -f $J\n"
    "# CSRF cookie (flow 4.2 step 2)\n"
    "curl -s -c $J -o /dev/null localhost:9000/api/v1/auth/csrf\n"
    "T=$(grep XSRF-TOKEN $J | awk '{print $7}')\n"
    "\n"
    "# Sign-in (4.3); then try a wrong password and no CSRF header to see the failure cases\n"
    "curl -s -b $J -c $J -H \"X-XSRF-TOKEN: $T\" -d 'email=you@example.com&password=...' localhost:9000/login\n"
    "curl -s -b $J localhost:9000/api/v1/me\n"
    "\n"
    "# Discovery and public keys\n"
    "curl -s localhost:9000/.well-known/openid-configuration\n"
    "curl -s localhost:9000/oauth2/jwks\n"
    "\n"
    "# Silent check while signed out (4.6 step 4a): look at the Location header\n"
    "curl -s -o /dev/null -w '%{redirect_url}\\n' 'localhost:9000/oauth2/authorize?response_type=code&client_id=pincode&redirect_uri=http://127.0.0.1:5175/&scope=openid&state=s&prompt=none&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256'\n"
    "\n"
    "# Decode any JWT payload to read its claims (sub, aud, roles, exp)\n"
    "echo '<token>' | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null; echo"
)
p("In the browser: open DevTools → Network, tick 'Preserve log', and sign in to an app. You will see each step of "
  "flow 4.4: /auth/login, /oauth2/authorization/..., /oauth2/authorize, /login, /login/oauth2/code/... with ?code=.")

# =====================================================================
# 9. INTERVIEW
# =====================================================================
h("9. Interview questions with short answers")
table(["Question", "Short answer (point at our code)"], [
    ["OAuth 2.0 vs OpenID Connect?", "OAuth grants access; OIDC adds who signed in (ID token, userinfo, logout). AuthReflector is an OIDC provider."],
    ["Why the authorization code flow?", "Tokens never appear in the browser URL; the code is single-use and needs PKCE (and a secret for web apps)."],
    ["What does PKCE protect against?", "Code interception: without the verifier the code can't be exchanged. 4.4 steps 3 and 12."],
    ["How does SSO work technically?", "The IdP keeps a session cookie; every app's authorize request is answered immediately when that session exists (4.5)."],
    ["Stateless JWT vs sessions?", "Apps verify JWTs locally (no lookup); AuthReflector itself is session-based because SSO needs a session."],
    ["How do you revoke a JWT?", "You don't: keep access tokens short (15 min) and revoke refresh tokens (4.8)."],
    ["Why check aud?", "So a token issued for one app can't be replayed at another (4.7)."],
    ["How do you detect a stolen refresh token?", "Rotation + remembering replaced tokens; a replay revokes the whole family (4.8)."],
    ["Where should a SPA keep tokens?", "Ideally nowhere (BFF). Without a backend: memory/sessionStorage, short-lived, no refresh token (4.6)."],
    ["Why is logout hard in SSO?", "Every app has its own session; RP-initiated logout ends the IdP session; other apps need back-channel logout (4.9)."],
    ["How do you rotate signing keys?", "Publish the new public key in JWKS first, sign with it later, remove the old after the longest token lifetime; kid tells apps which key."],
    ["Why add an app without a restart?", "Registered clients live in the database and are read per request (4.10)."],
], widths=[5, 10.5])

doc.save(OUT)
print("saved", OUT)
