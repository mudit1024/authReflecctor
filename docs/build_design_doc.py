"""Generates AuthReflector-Design-Doc.docx. Usage: python build_design_doc.py <output_path>"""
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


# =====================================================================
# TITLE PAGE
# =====================================================================
title = doc.add_paragraph()
title.alignment = WD_ALIGN_PARAGRAPH.CENTER
title.paragraph_format.space_before = Pt(120)
r = title.add_run("AuthReflector")
r.bold = True
r.font.size = Pt(36)
r.font.color.rgb = ACCENT

sub = doc.add_paragraph()
sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = sub.add_run("Single Sign-On for all our projects: Design & Delivery Plan")
r.font.size = Pt(16)

tag = doc.add_paragraph()
tag.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = tag.add_run("One account. One login. Every app.")
r.italic = True
r.font.size = Pt(12)

doc.add_paragraph()
table(
    ["Field", "Value"],
    [
        ["Document version", "0.4 (14 Nov 2026: A1 complete: OIDC provider, admin apps, launcher, JS SDK, Spring starter, real-browser E2E)"],
        ["Date", "14 November 2026"],
        ["Owners", "Somil, Mudit (to confirm)"],
        ["Status", "Draft. Items marked PROPOSED or OPEN need a decision before the related work starts."],
        ["Name", "AuthReflector is the working name (taken from the project folder). See OPEN-1."],
        ["First client apps", "SplitMate (web, later mobile), CodeQuror (web)"],
        ["Reference material", "SplitMate Technical Design v1.6, section 7 and decisions D31-D42 (SPLIT-11); "
                               "SplitMate branch feature/SPLIT-12-login-jwt; CodeQuror Design Doc (11 Nov 2026), section 4"],
    ],
    widths=[4.5, 11],
)
doc.add_page_break()

# =====================================================================
# HOW TO READ
# =====================================================================
h("How to read this document")
p("This document turns the idea of 'one login for all our projects, like Google's SSO' into a concrete "
  "set of requirements, an architecture and a build plan. It uses the same status labels as the "
  "SplitMate design doc:")
table(
    ["Label", "Meaning"],
    [
        ["DECIDED", "Agreed. Includes the SplitMate SPLIT-11 decisions that carry over unchanged."],
        ["PROPOSED", "Recommended in this draft. Needs the owners' confirmation."],
        ["OPEN", "Needs a decision. Each OPEN item has a recommendation in section 17."],
        ["DONE", "Implemented, merged to main, and the Jira issue is closed."],
    ],
    widths=[3, 12.5],
)
callout("In one sentence:",
        "AuthReflector is our own OpenID Connect identity provider. Users sign in once at accounts.<our-domain>; "
        "SplitMate, CodeQuror and every future project send users there to log in and receive signed tokens back, "
        "instead of each app building its own login, sign-up, Google login and password reset.")

h("Contents", 2)
numbered([
    "Purpose and goals",
    "How SSO works for a user",
    "Requirements",
    "Key decision: how to build the identity provider",
    "Architecture",
    "Login, SSO, refresh and logout flows",
    "Tokens, claims and keys",
    "SplitMate decisions carried over",
    "Data model",
    "Endpoints and pages",
    "Security controls",
    "Integrating an app (SplitMate, CodeQuror)",
    "Impact on existing work",
    "Technology stack",
    "Environments and local development",
    "Testing and engineering workflow",
    "Open decisions",
    "Roadmap",
    "Risks",
    "Learning and interview map",
    "Decision log",
])
doc.add_page_break()

# =====================================================================
# 1. PURPOSE
# =====================================================================
h("1. Purpose and goals")
p("Today every project plans its own authentication. SplitMate designed login, JWTs, refresh tokens, "
  "Google sign-in, password reset and roles in SPLIT-11 and is half way through building them (SPLIT-12). "
  "CodeQuror needs the same thing plus GitHub login. Building it twice means double the work, double the "
  "security risk and two separate accounts for the same person.")
h("1.1 Goals", 2)
bullets([
    ("Single sign-on. ", "A user who is logged in to one of our apps is logged in to all of them, without typing a password again."),
    ("One account per person. ", "One identity (email, password, linked Google and GitHub accounts) shared by every project."),
    ("Standards, not custom glue. ", "Implement OAuth 2.1 and OpenID Connect so any app (Spring Boot, React, React Native, FastAPI) can integrate with standard libraries."),
    ("Reuse SplitMate's design. ", "Keep the SPLIT-11 security decisions (section 8) and move them to one place."),
    ("Learning and interviews. ", "Understand and be able to explain how Google/Auth0-style SSO actually works, end to end."),
])
h("1.2 Non-goals for now", 2)
bullets([
    "Being a public identity provider for third-party apps ('Sign in with AuthReflector' for others).",
    "SAML, LDAP or enterprise directory integration.",
    "Multi-tenant organisations (companies with their own users and admins).",
    "Fine-grained authorization inside apps. AuthReflector says who the user is and their per-app role; "
    "each app still decides whether that user may edit a given group or post.",
    "Storing app data. User profiles beyond basic identity (name, avatar) stay in each app.",
])

# =====================================================================
# 2. USER VIEW
# =====================================================================
h("2. How SSO works for a user")
numbered([
    "Asha opens SplitMate and clicks 'Log in'. SplitMate redirects her to accounts.<domain>.",
    "She signs in there once (email + password, or 'Continue with Google' / 'Continue with GitHub').",
    "AuthReflector sets its own session cookie on accounts.<domain> and sends her back to SplitMate with a one-time code. "
    "SplitMate exchanges the code for tokens. She is in.",
    "Later she opens CodeQuror and clicks 'Log in'. CodeQuror redirects to accounts.<domain>, which sees the session cookie "
    "and immediately sends her back with a code. No password prompt. This is the SSO moment.",
    "She logs out from SplitMate. She chooses whether to log out of SplitMate only, or of everything (ends the AuthReflector session; see 6.4).",
])
p("Everything account-related (change password, linked accounts, active devices, delete account) lives in one "
  "place: accounts.<domain>/account.")

# =====================================================================
# 3. REQUIREMENTS
# =====================================================================
h("3. Requirements")
h("3.1 Functional requirements", 2)
p("Phase refers to the roadmap in section 18.")
table(
    ["ID", "Requirement", "Phase", "Status"],
    [
        ["FR-1", "Register with name, email and password (SplitMate password policy, D8/D31)", "A1", "DECIDED"],
        ["FR-2", "Log in with email and password on the AuthReflector login page", "A1", "DECIDED"],
        ["FR-3", "OIDC Authorization Code flow with PKCE for every client; discovery document and JWKS published", "A1", "PROPOSED"],
        ["FR-4", "SSO: an existing AuthReflector session logs the user in to other apps without a prompt", "A1", "PROPOSED"],
        ["FR-5", "Access token (JWT, 15 min) + rotating refresh token (30 days) per app", "A1", "DECIDED (D34, D37)"],
        ["FR-6", "Logout: from one app, and from everywhere (ends the IdP session and revokes refresh tokens)", "A1 / A4", "DECIDED (D37)"],
        ["FR-7", "Email verification on sign-up; unverified accounts can log in but are flagged (see OPEN-7)", "A2", "OPEN"],
        ["FR-8", "Forgot password with single-use, 15-minute emailed link; generic response", "A2", "DECIDED (D32, D41)"],
        ["FR-9", "Continue with Google; link to an existing account only when email_verified = true", "A3", "DECIDED (D38)"],
        ["FR-10", "Continue with GitHub (needed by CodeQuror); same linking rule, using GitHub's verified primary email", "A3", "PROPOSED"],
        ["FR-11", "Per-app roles (for example SplitMate ADMIN, CodeQuror ADMIN) plus a platform admin role", "A4", "PROPOSED"],
        ["FR-12", "Account page: profile, change password, linked accounts, active sessions/devices, log out everywhere", "A4", "PROPOSED"],
        ["FR-13", "Admin: register and manage client apps (redirect URIs, scopes), view users, disable a user", "A4", "PROPOSED"],
        ["FR-14", "Delete account; every app is told so it can delete or anonymise its data", "A4", "PROPOSED"],
        ["FR-15", "Mobile login (Expo) through the system browser with PKCE", "A5", "PROPOSED"],
        ["FR-16", "Sign in with Apple (likely required on iOS when Google login is offered; SplitMate risk 7)", "A5", "OPEN"],
        ["FR-17", "Two-factor authentication (TOTP authenticator app), then passkeys", "A5", "PROPOSED"],
        ["FR-18", "Audit log of security events (logins, failures, password changes, links, admin actions)", "A2", "PROPOSED"],
    ],
    widths=[1.3, 10.2, 1.5, 2.5],
)
h("3.2 Non-functional requirements", 2)
table(
    ["Area", "Requirement"],
    [
        ["Security", "OWASP ASVS level 2 as the target for auth flows. No secrets in Git. HTTPS only in production. Security review before any app's public release."],
        ["Availability", "If AuthReflector is down, nobody can log in, but users with a valid access token keep working (apps verify tokens locally with cached public keys). Target 99.5% in the first year."],
        ["Latency", "Token verification in apps is local (no network call per request). Login page p95 under 500 ms excluding BCrypt."],
        ["Scale", "Thousands of users, a handful of client apps. One instance is enough at first; design so a second instance can be added (shared session store)."],
        ["Cost", "Runs on the same small infrastructure as the apps. No paid identity service."],
        ["Privacy", "Minimal data: identity only. Apps get only the claims their scopes allow. Account deletion supported."],
        ["Operability", "Health endpoint, structured logs, metrics; key rotation without downtime."],
    ],
    widths=[3, 12.5],
)

# =====================================================================
# 4. BUILD OPTIONS
# =====================================================================
h("4. Key decision: how to build the identity provider (PROPOSED)")
table(
    ["Option", "What it is", "For", "Against"],
    [
        ["A. Spring Authorization Server (recommended)",
         "Spring's official OAuth 2.1 / OIDC provider framework, built on Spring Security (in Spring Boot 4 / Spring Security 7)",
         "Same stack as SplitMate. Standard flows and endpoints done right; we write the parts that teach the most (login, linking, sessions, roles). Very strong interview story",
         "More work than a ready-made product. Some features (refresh reuse detection across a token family, admin UI) we build ourselves"],
        ["B. Keycloak (self-hosted)",
         "Complete open-source identity server",
         "Almost everything works out of the box: SSO, social login, MFA, admin console",
         "Little learning in the code; heavy to run (memory) and configure; customising pages and flows is awkward"],
        ["C. Custom 'auth API' issuing JWTs",
         "Extend SplitMate's current code into a shared login API that each app calls",
         "Fast start; reuses SPLIT-12 code directly",
         "Not real SSO: every app has its own login form and handles passwords. Non-standard, so every client needs custom code. Easy to get subtly wrong"],
        ["D. Hosted (Auth0, Cognito, Clerk, Firebase)",
         "Paid or free-tier identity service",
         "Least work, production-grade",
         "Cost grows with users; vendor lock-in; defeats the learning goal"],
    ],
    widths=[3.2, 3.8, 4.3, 4.2],
)
p("Recommendation: option A. It keeps the SplitMate stack, uses the real standards (so CodeQuror's FastAPI "
  "service or any future app can plug in with an off-the-shelf OIDC library), and keeps the learning value "
  "that is a stated goal of both projects.")

# =====================================================================
# 5. ARCHITECTURE
# =====================================================================
h("5. Architecture")
code(
    "   Browser / mobile app\n"
    "        |                       (1) redirect to log in            +-------------------+\n"
    "        |-------------------------------------------------------->|   AuthReflector   |\n"
    "        |<--------------------------------------------------------|  accounts.<domain>    |\n"
    "        |      (2) code back to the app's redirect URI            |                   |\n"
    "        v                                                         | login & account   |\n"
    "  +--------------+   (3) code -> tokens (back channel)            | pages (Thymeleaf) |\n"
    "  | SplitMate    |----------------------------------------------->| OIDC endpoints    |\n"
    "  | web BFF/API  |   (4) GET /oauth2/jwks (cached public keys)    | user & client mgmt|\n"
    "  +--------------+----------------------------------------------->|                   |\n"
    "  +--------------+                                                +---------+---------+\n"
    "  | CodeQuror    |---- same as SplitMate --------------------------------->  |\n"
    "  +--------------+                                                          |\n"
    "                                            PostgreSQL (authreflector DB) --+-- Google / GitHub (OIDC/OAuth)\n"
    "                                            Mail: Mailpit locally, AWS SES  --+-- Redis (sessions, rate limits; later)"
)
h("5.1 Components", 2)
table(
    ["Component", "Responsibility"],
    [
        ["Authorization server", "Standard OIDC endpoints: authorize, token, JWKS, userinfo, revoke, logout, discovery."],
        ["Login and account UI", "React (Vite + TypeScript) single-page app built into and served by AuthReflector on the auth origin: login, register, forgot/reset password, verify email, account. It talks to Spring Security with a same-origin session cookie and a CSRF token, never with JWTs (A-D9)."],
        ["Identity module", "Users, password hashing (BCrypt), federated identities (Google, GitHub), account linking."],
        ["Session module", "The SSO session (cookie on accounts.<domain>). Spring Session with JDBC first, Redis when there are two instances."],
        ["Client registry", "Which apps exist, their redirect URIs, allowed scopes, token settings. Seeded by migration at first, admin UI later."],
        ["Roles module", "Platform roles and per-app roles; added to tokens for the matching app only."],
        ["Notifications", "Emails (verification, reset, 'your password was changed'). Back-channel events to apps later (logout, account deleted)."],
        ["Audit", "Append-only security event log."],
    ],
    widths=[4, 11.5],
)
h("5.2 Design principles", 2)
bullets([
    "AuthReflector is the only place that sees passwords. Apps never show a password field.",
    "Apps stay stateless where SplitMate already decided so (D39): their APIs verify JWTs locally with the public keys.",
    "AuthReflector itself is stateful by design: SSO needs a session at the identity provider. That is the one deliberate difference from SplitMate's stateless rule, and it is limited to the auth origin.",
    "Modular monolith, same as SplitMate: one Spring Boot app, packages per module.",
    "Each app has its own token audience, so a SplitMate token can't be replayed against CodeQuror.",
])

# =====================================================================
# 6. FLOWS
# =====================================================================
h("6. Login, SSO, refresh and logout flows")
h("6.1 First login (Authorization Code + PKCE)", 2)
numbered([
    "The app creates a random code_verifier, a state and a nonce, and redirects to "
    "/oauth2/authorize?response_type=code&client_id=splitmate-web&redirect_uri=...&scope=openid profile email"
    "&code_challenge=<S256 hash>&state=...&nonce=...",
    "AuthReflector has no session yet, so it shows the login page.",
    "The user logs in (password or Google/GitHub). AuthReflector creates the SSO session and sets an httpOnly, Secure cookie on accounts.<domain>.",
    "AuthReflector redirects to the app's registered redirect_uri with a one-time code (valid about 1 minute) and the state.",
    "The app checks state and posts the code with the code_verifier (and client secret if it is a confidential client) to /oauth2/token.",
    "It receives an ID token (who logged in), an access token for its API, and a refresh token. It checks the ID token's signature, issuer, audience and nonce.",
])
h("6.2 SSO into a second app", 2)
p("Same as 6.1, but at step 2 the session cookie is present, so AuthReflector skips the login page and "
  "returns a code immediately. Apps can also send prompt=none for a silent check ('is the user already "
  "logged in?') without ever showing a page.")
h("6.3 Token refresh", 2)
bullets([
    "Access tokens live 15 minutes (D34). The app uses the refresh token at /oauth2/token (grant_type=refresh_token).",
    "Every refresh rotates the token: the old one stops working and a new one is issued (D37).",
    "Reusing an already-rotated refresh token revokes the whole token family for that app and device (D37). "
    "Spring Authorization Server rotates out of the box; family revocation on reuse is custom work (A1).",
    "Refresh tokens are stored only as hashes (D37).",
])
h("6.4 Logout", 2)
table(
    ["Action", "What happens", "Phase"],
    [
        ["Log out of this app", "The app drops its tokens and calls /oauth2/revoke for its refresh token. SSO session stays, so other apps stay logged in.", "A1"],
        ["Log out everywhere (from an app)", "RP-initiated logout: the app redirects to /connect/logout. AuthReflector ends its session and revokes this user's refresh tokens for the device.", "A1"],
        ["Other apps notice the logout", "OIDC back-channel logout: AuthReflector POSTs a signed logout token to each app's logout endpoint. Until then, other apps keep working until their access token expires (at most 15 minutes) and the next refresh fails.", "A4"],
        ["Log out all devices", "From the account page: ends all sessions and revokes every refresh token of the user.", "A4"],
    ],
    widths=[3.6, 10.4, 1.5],
)
h("6.5 Web apps: where do the tokens live? (OPEN-2)", 2)
p("SplitMate decided (D35) that on the web the refresh token must not be readable by JavaScript. With SSO, "
  "the app's single-page frontend no longer talks to its own login endpoint, so there are two ways to keep that rule:")
table(
    ["Option", "How", "Trade-off"],
    [
        ["Backend-for-frontend (BFF), recommended",
         "A small server side for each app (Spring Cloud Gateway with TokenRelay, or the app's Spring Boot backend) is a confidential OIDC client. "
         "The browser only gets an httpOnly session cookie from the app; the BFF keeps the tokens and adds the Bearer header when it forwards API calls.",
         "Most secure (tokens never reach the browser), matches D35, and the API stays a pure stateless resource server for mobile too. One more component to run; needs CSRF protection on the BFF."],
        ["Public SPA client",
         "The React app does PKCE itself, keeps the access token in memory and the refresh token in memory or sessionStorage.",
         "Simplest. But JavaScript can read the refresh token, which D35 rejected. Spring Authorization Server doesn't issue refresh tokens to public clients by default."],
    ],
    widths=[3.4, 6.6, 5.5],
)
h("6.6 Mobile apps", 2)
p("Expo apps are public clients: they open the AuthReflector login page in the system browser (expo-auth-session), "
  "use PKCE, receive the code on a custom-scheme or universal-link redirect, and keep the refresh token in SecureStore (D35). "
  "Refresh tokens for public mobile clients need a custom token setting in Spring Authorization Server (A5).")

# =====================================================================
# 7. TOKENS
# =====================================================================
h("7. Tokens, claims and keys")
table(
    ["Token", "Format and lifetime", "Used for"],
    [
        ["ID token", "Signed JWT, 15 minutes", "Tells the app who logged in. Never sent to APIs."],
        ["Access token", "Signed JWT, 15 minutes (D34), aud = the app's API", "Sent as Bearer to the app's API; verified locally."],
        ["Refresh token", "Opaque random value, 30 days, rotating, stored hashed (D34, D37)", "Getting new access tokens without a login prompt."],
        ["SSO session cookie", "httpOnly, Secure, SameSite=Lax on accounts.<domain>; idle 7 days, absolute 30 days (PROPOSED)", "Skipping the login page for other apps."],
        ["Authorization code", "Opaque, single use, about 1 minute", "Exchanged for tokens in 6.1."],
    ],
    widths=[3.2, 6.3, 6],
)
h("7.1 Access token claims (PROPOSED)", 2)
code(
    '{\n'
    '  "iss": "https://accounts.<domain>",\n'
    '  "sub": "8f1c...-uuid",            // stable user id, the key every app stores\n'
    '  "aud": "splitmate-api",           // checked by the SplitMate API\n'
    '  "azp": "splitmate-web",           // the client that asked for the token\n'
    '  "scope": "openid profile email",\n'
    '  "email": "asha@example.com",\n'
    '  "email_verified": true,\n'
    '  "roles": ["ADMIN"],               // roles for THIS app only (D42, extended)\n'
    '  "iat": 1763100000, "exp": 1763100900, "jti": "..."\n'
    '}'
)
p("Claims are readable by anyone who holds the token (as noted in SplitMate's AccessTokenService), so nothing secret goes in them.")
h("7.2 Signing keys", 2)
bullets([
    ("Algorithm: ", "RS256, matching SplitMate's SPLIT-12 implementation (D36). ES256 is possible later; clients follow the JWKS automatically."),
    ("Key storage: ", "private key from environment / AWS Secrets Manager, never in Git (D36). Test keys only on the test classpath, same pattern as SplitMate."),
    ("Rotation: ", "every key has a kid. Publish the new public key in JWKS first, start signing with it after apps have cached it, remove the old key after the longest token lifetime. Runbook in A5."),
])

# =====================================================================
# 8. CARRY-OVER
# =====================================================================
h("8. SplitMate decisions carried over")
table(
    ["SplitMate decision", "In AuthReflector"],
    [
        ["D8/D31 password policy 8-64 chars, lower/upper/digit/special; BCrypt", "Kept as is. Registration code from SPLIT-10 moves here."],
        ["D32/D41 forgot password (hashed single-use token, 15 min, generic 202, revoke sessions)", "Kept as is; also ends SSO sessions."],
        ["D34 access 15 min, refresh 30 days", "Kept, per app."],
        ["D35 refresh token not readable by JS (web); SecureStore (mobile)", "Kept, via the BFF (OPEN-2) on the web."],
        ["D36 asymmetric signing, kid, keys outside Git", "Kept; JWKS endpoint added so every app verifies with the public key."],
        ["D37 rotation, reuse detection, logout one/all devices", "Kept; logout extended with SSO session and back-channel logout."],
        ["D38 Google linking only when email_verified", "Kept; same rule for GitHub."],
        ["D39 stateless Spring Security JWT filter chain", "Kept for app APIs (resource servers). AuthReflector itself is session-based on purpose (5.2)."],
        ["D42 USER/ADMIN role in the JWT, enforced on the server", "Extended: roles per app, only in that app's token; platform admin for AuthReflector."],
        ["SPLIT-14 rate limiting (5 failures / 15 min per IP and per email, Bucket4j)", "Kept; moves here, since only AuthReflector sees login attempts."],
    ],
    widths=[7.5, 8],
)

# =====================================================================
# 9. DATA MODEL
# =====================================================================
h("9. Data model (PROPOSED)")
p("PostgreSQL, schema managed by Flyway from day one (unlike SplitMate's ddl-auto=update, auth tables must never drift).")
table(
    ["Table", "Key columns", "Notes"],
    [
        ["users", "id UUID (the sub), email (unique, lowercased), email_verified, name, avatar_url, password_hash NULL, status (ACTIVE/DISABLED/DELETED), created_at, updated_at", "password_hash is NULL for Google/GitHub-only accounts."],
        ["federated_identities", "id, user_id FK, provider (GOOGLE/GITHUB/APPLE), provider_subject, email_at_link, linked_at", "Unique (provider, provider_subject). Linking is by the provider's stable id (D38)."],
        ["roles / user_roles", "user_id, client_app (NULL = platform), role", "Example rows: (u1, splitmate, ADMIN), (u2, NULL, PLATFORM_ADMIN)."],
        ["oauth2_registered_client", "Spring Authorization Server's standard table", "Client ids, hashed secrets, redirect URIs, scopes, token settings."],
        ["oauth2_authorization", "Spring Authorization Server's standard table", "Codes and tokens in flight. Token values stored hashed (custom; D37)."],
        ["oauth2_authorization_consent", "Standard table", "Mostly unused: our own apps skip the consent screen."],
        ["spring_session / spring_session_attributes", "Spring Session JDBC", "The SSO session. Moves to Redis with a second instance."],
        ["email_verification_tokens / password_reset_tokens", "id, user_id, token_hash, expires_at, used_at, requested_ip", "Same pattern as SplitMate's password_reset_tokens."],
        ["audit_events", "id, user_id NULL, type, ip, user_agent, client_app, details JSON, created_at", "Append-only."],
    ],
    widths=[3.8, 7.2, 4.5],
)
h("9.1 Who owns which user data", 2)
p("AuthReflector owns identity: id, email, name, avatar, credentials, linked accounts, roles. Each app owns its "
  "own data and stores only the sub (user UUID) as its user key, plus a cached copy of name and email. "
  "Apps create their local user row on first login (just-in-time provisioning) and refresh the cached fields from "
  "the token on every login.")

# =====================================================================
# 10. ENDPOINTS
# =====================================================================
h("10. Endpoints and pages")
table(
    ["Method / path", "Purpose", "Phase"],
    [
        ["GET /.well-known/openid-configuration", "Discovery: tells clients where everything is", "A1"],
        ["GET /oauth2/authorize", "Start login (code + PKCE)", "A1"],
        ["POST /oauth2/token", "Code or refresh token -> tokens", "A1"],
        ["GET /oauth2/jwks", "Public signing keys", "A1"],
        ["GET /userinfo", "User claims for the access token's scopes", "A1"],
        ["POST /oauth2/revoke", "Revoke a refresh token", "A1"],
        ["GET /connect/logout", "RP-initiated logout (end SSO session)", "A1"],
        ["GET/POST /login, /register", "Login and sign-up pages", "A1"],
        ["GET /oauth2/authorization/{google|github}", "Start social login", "A3"],
        ["GET/POST /forgot-password, /reset-password, /verify-email", "Recovery and verification pages", "A2"],
        ["GET /account, /account/sessions, /account/linked", "Account self-service", "A4"],
        ["/api/v1/admin/** (clients, users)", "Admin, PLATFORM_ADMIN only", "A4"],
        ["GET /actuator/health", "Health check", "A0"],
    ],
    widths=[6, 8, 1.5],
)

# =====================================================================
# 11. SECURITY
# =====================================================================
h("11. Security controls")
bullets([
    "Only the Authorization Code grant with PKCE (S256), required for every client including confidential ones. No implicit or password grant.",
    "Exact redirect URI matching; no wildcards. Different URIs per environment.",
    "state and nonce checked by clients; one-time codes with short expiry.",
    "CSRF protection on all AuthReflector forms (session-based, so CSRF applies) and on BFF endpoints.",
    "Cookies: httpOnly, Secure, SameSite=Lax; __Host- prefix where possible.",
    "Rate limiting on login, register, reset and token endpoints (SPLIT-14 values). Generic 'Invalid email or password' message; no account lockout (D7).",
    "No user enumeration: register, login and reset give the same responses and similar timing whether or not the email exists.",
    "Client secrets stored hashed; private keys from the secrets manager.",
    "Audience checks in every app API (aud), so tokens can't be used across apps.",
    "Security headers on auth pages: Content-Security-Policy, frame-ancestors 'none' (anti-clickjacking), HSTS.",
    "Audit log for all security events; 'new login' and 'password changed' emails.",
    "Dependency and image scanning in CI; security review before any app goes public.",
])

# =====================================================================
# 12. INTEGRATING
# =====================================================================
h("12. Integrating an app")
h("12.1 Onboarding checklist for any app", 2)
numbered([
    "Register a client in AuthReflector: client_id, type (confidential BFF or public mobile), redirect URIs per environment, post-logout redirect URIs, scopes, audience.",
    "Web: add the BFF (Spring OAuth2 client) pointing at the issuer URL. Mobile: add expo-auth-session with PKCE.",
    "API: configure it as a resource server with the issuer URL and its audience. Remove any local login code.",
    "Create the local user row on first request for an unknown sub (just-in-time provisioning).",
    "Map the roles claim to the app's authorities.",
    "Optional (A4): implement the back-channel logout and account-deleted endpoints.",
])
h("12.2 SplitMate API configuration (sketch)", 2)
code(
    "spring:\n"
    "  security:\n"
    "    oauth2:\n"
    "      resourceserver:\n"
    "        jwt:\n"
    "          issuer-uri: ${AUTH_ISSUER_URI:http://accounts.localtest.me:9000}\n"
    "          audiences: splitmate-api"
)
p("SplitMate already depends on spring-boot-starter-security-oauth2-resource-server, so its SecurityConfig "
  "changes little: replace the local key decoder with the issuer URI, and read the role from the roles claim.")
h("12.3 CodeQuror", 2)
p("CodeQuror's planned 'OAuth2 (GitHub, Google) + email, JWT access + refresh' (CodeQuror doc, section 4) "
  "becomes 'OIDC client of AuthReflector'. Its FastAPI AI service verifies the same JWTs with any JWKS-aware library, or "
  "receives calls only from the Spring Boot core.")

# =====================================================================
# 13. IMPACT
# =====================================================================
h("13. Impact on existing work (PROPOSED)")
table(
    ["Item", "Proposal"],
    [
        ["SplitMate SPLIT-10 registration (merged)", "Move the registration logic and its 23 tests to AuthReflector (A1). SplitMate's /api/v1/auth/register is removed once integration lands. There are no real users yet, only local test rows, so there is nothing to migrate."],
        ["SplitMate branch feature/SPLIT-12-login-jwt (uncommitted)", "Don't merge as is. AccessTokenService, JwtConfig, JwtProperties, Role and their tests move to AuthReflector (they become the token customiser and key config). SecurityConfig stays in SplitMate and is pointed at the issuer. Commit the work on the branch first so nothing is lost."],
        ["SplitMate SPLIT-13 to SPLIT-16", "Built once, in AuthReflector (refresh/logout, rate limiting, Google, forgot password)."],
        ["SplitMate roadmap v0.2 'Authentication'", "Becomes 'v0.2 Integrate with AuthReflector': resource server config, BFF login, just-in-time users, role mapping. SplitMate v0.3+ continue in parallel against a stub issuer if needed."],
        ["SplitMate SPLIT-17 frontend strategy", "Still needed; the login screen itself is no longer part of SplitMate."],
        ["CodeQuror section 4 'Auth'", "Updated to 'AuthReflector (OIDC)'."],
        ["Old auth-app (Spring Boot 3.5, MySQL, HS512 shared secret)", "Use as a reference for UI ideas only. Its shared-secret signing conflicts with D36. Note: its folder contains a Google OAuth client secret file. Keep it out of any Git repo and rotate that secret in Google Cloud Console if it was ever committed or shared."],
    ],
    widths=[5, 10.5],
)

# =====================================================================
# 14. STACK
# =====================================================================
h("14. Technology stack (PROPOSED)")
table(
    ["Layer", "Choice", "Why"],
    [
        ["Language / framework", "Java 21, Spring Boot 4.1.1", "Same as SplitMate (D2)"],
        ["Identity", "Spring Authorization Server (Spring Security 7), Spring Security OAuth2 Client (for Google/GitHub)", "Section 4, option A. Exact starter names to be confirmed at project setup."],
        ["Pages", "React + TypeScript + Vite, built into the Spring Boot jar (served from the auth origin)", "A-D9: owner's familiar stack; same-origin so session + CSRF still apply"],
        ["Database", "PostgreSQL 17 + Flyway", "Same engine as SplitMate; versioned schema"],
        ["Sessions", "Spring Session JDBC, then Redis", "Start with one dependency less"],
        ["Rate limiting", "Bucket4j (in memory, then Redis)", "SPLIT-14"],
        ["Email", "Mailpit locally, AWS SES in production", "Same as SplitMate"],
        ["Tests", "JUnit 5, Spring Security Test, Testcontainers, Playwright for login flows", "Real browser redirects need an end-to-end test"],
        ["Build / CI", "Maven, GitHub Actions, Docker", "Same as SplitMate"],
        ["Hosting", "Docker Compose locally; AWS later, same account as the apps", "SplitMate section 14"],
    ],
    widths=[3.5, 6.5, 5.5],
)

# =====================================================================
# 15. ENVIRONMENTS
# =====================================================================
h("15. Environments and local development")
table(
    ["Service", "Local address"],
    [
        ["AuthReflector", "http://accounts.localtest.me:9000"],
        ["SplitMate web / API", "http://splitmate.localtest.me:5173 / :8080"],
        ["CodeQuror web / API", "http://codequror.localtest.me:5174 / :8081"],
        ["PostgreSQL, Mailpit", "Docker Compose (separate authreflector database)"],
    ],
    widths=[5, 10.5],
)
callout("Gotcha:",
        "Browsers share cookies across ports on the same host. If AuthReflector and SplitMate both run on 'localhost', "
        "their session cookies overwrite each other and login loops appear. Use separate host names "
        "(*.localtest.me resolves to 127.0.0.1) or at least different cookie names.")
p("Production: accounts.<domain>, with the apps on sibling subdomains (splitmate.<domain>, codequror.<domain>), "
  "so SameSite=Lax cookies and silent SSO behave predictably. See OPEN-4.")

# =====================================================================
# 16. TESTING / WORKFLOW
# =====================================================================
h("16. Testing and engineering workflow")
bullets([
    ("Same workflow as SplitMate (DECIDED there): ", "Jira issue -> branch -> TDD -> PR -> CI -> merge -> Done. Jira project key: OPEN-5."),
    ("Unit: ", "password policy, token customiser (claims, audience, roles), linking rules, reset token handling."),
    ("Integration (Testcontainers): ", "full authorize -> token -> refresh -> revoke against a real database; refresh reuse detection; JWKS."),
    ("End-to-end (Playwright): ", "log in to app 1, open app 2 without a password prompt; logout everywhere; reset password via Mailpit."),
    ("Conformance: ", "run the OpenID Foundation conformance suite (basic OP profile) before production. It catches spec mistakes cheaply."),
    ("Coverage: ", "same targets as SplitMate; 90%+ on security-critical classes."),
])

# =====================================================================
# 17. OPEN DECISIONS
# =====================================================================
h("17. Open decisions (to settle before A0 starts)")
table(
    ["#", "Question", "Recommendation"],
    [
        ["OPEN-1", "Name of the project", "Keep AuthReflector, or a shorter user-facing name for the login page (users see it)."],
        ["OPEN-2", "Web token handling: BFF or public SPA client", "DECIDED (A-D10): BFF."],
        ["OPEN-3", "Login/account pages: server-rendered (Thymeleaf) or React", "DECIDED (A-D9): React app served from the auth origin."],
        ["OPEN-4", "Production domain(s); all apps under one parent domain?", "Yes: accounts.<domain> (suggested earlier), apps on sibling subdomains."],
        ["OPEN-5", "Repository and Jira", "DECIDED (A-D7, A-D8): new Jira project AUTH; separate GitHub repo authreflector."],
        ["OPEN-6", "Build option (section 4)", "Spring Authorization Server."],
        ["OPEN-7", "Must email be verified before first login?", "Allow login, but require a verified email before linking social accounts, and let apps check email_verified for sensitive actions."],
        ["OPEN-8", "Which social providers at launch", "Google and GitHub in A3; Apple when iOS ships."],
        ["OPEN-9", "Two-factor authentication timing", "After both apps are integrated (A5), TOTP first."],
        ["OPEN-10", "Does SplitMate pause v0.2 until AuthReflector A1 lands?", "Yes for auth; continue v0.3 domain work in parallel."],
        ["OPEN-11", "Who builds what (if two people)", "Decide per phase in Jira."],
    ],
    widths=[1.6, 6.2, 7.7],
)

# =====================================================================
# 18. ROADMAP
# =====================================================================
h("18. Roadmap")
table(
    ["Phase", "Scope", "Done when"],
    [
        ["A0 Foundation", "Repo, Spring Boot 4.1.1 skeleton, Postgres + Flyway, Docker Compose, CI, key loading from env (test keys on classpath), health endpoint",
         "CI green; app starts with Docker Compose"],
        ["A1 Core SSO (MVP)", "Registration (from SPLIT-10), login page, Authorization Server with code + PKCE, JWKS, discovery, token customiser (aud, roles), refresh rotation with reuse detection, revoke, RP-initiated logout. Two registered clients: SplitMate and a tiny demo app",
         "Log in to SplitMate, open the demo app, no password prompt"],
        ["A2 Recovery and safety", "Email sending, verification, forgot password, rate limiting, audit log, security emails",
         "Reset password end to end via Mailpit; brute force returns 429"],
        ["A3 Social login", "Google and GitHub, account linking rules, accounts without passwords",
         "Continue with Google/GitHub works and links safely"],
        ["A4 Multi-app", "CodeQuror integration, per-app roles, account page (sessions, linked accounts, log out everywhere), admin for clients/users, back-channel logout, account deletion events",
         "Real SSO between SplitMate and CodeQuror"],
        ["A5 Mobile and hardening", "Expo PKCE clients, Sign in with Apple, TOTP 2FA, Redis sessions, key rotation runbook, conformance suite, AWS deployment, security review",
         "Ready for SplitMate Friend Beta"],
    ],
    widths=[3, 8.5, 4],
)

# =====================================================================
# 19. RISKS
# =====================================================================
h("19. Risks")
table(
    ["Risk", "Mitigation"],
    [
        ["Single point of failure: AuthReflector down means no new logins anywhere", "Local token verification with cached keys; health checks; later two instances with shared sessions"],
        ["A security bug affects every app at once", "Standard framework, conformance suite, security review, small surface (identity only)"],
        ["Delays SplitMate's v0.2", "A1 is deliberately small; SplitMate continues domain work in parallel"],
        ["Spring Authorization Server features we need are missing (public-client refresh, family revocation)", "Spike both in A1 before committing; both are known customisation points"],
        ["Cookie / redirect problems in local dev", "Separate host names (section 15); Playwright tests"],
        ["Leaked secrets (for example the Google client secret file in auth-app)", "Secrets only in env / Secrets Manager; Git pre-commit secret scanning; rotate anything that was exposed"],
    ],
    widths=[7, 8.5],
)

# =====================================================================
# 20. LEARNING MAP
# =====================================================================
h("20. Learning and interview map")
table(
    ["Concept", "Where", "Interview question"],
    [
        ["OAuth 2.0 vs OpenID Connect", "Whole project", "Why is OAuth alone not an authentication protocol?"],
        ["Authorization code + PKCE", "6.1", "What attack does PKCE stop?"],
        ["SSO sessions", "6.2", "How does Google log you in to YouTube without a password?"],
        ["JWT, JWKS, kid, key rotation", "7.2", "How do you rotate a signing key without logging everyone out?"],
        ["Refresh rotation and reuse detection", "6.3", "How do you detect a stolen refresh token?"],
        ["BFF pattern", "6.5", "Where should a SPA keep its tokens?"],
        ["Audience restriction", "7.1, 11", "Why must an API check aud?"],
        ["Back-channel logout", "6.4", "Why is logout the hardest part of SSO?"],
        ["Federated identity and account linking", "FR-9, FR-10", "How can account linking lead to account takeover?"],
    ],
    widths=[4.5, 2.5, 8.5],
)

# =====================================================================
# 21. DECISION LOG
# =====================================================================
h("21. Decision log")
table(
    ["#", "Decision", "Status"],
    [
        ["A-D1", "Build one shared identity provider for all projects instead of per-app auth", "PROPOSED (recommended in the 11-14 Nov 2026 discussion)"],
        ["A-D2", "SplitMate SPLIT-11 decisions D8, D31-D42 carry over (section 8)", "DECIDED (made in SPLIT-11)"],
        ["A-D3", "Spring Authorization Server, OIDC, Authorization Code + PKCE only", "PROPOSED"],
        ["A-D4", "Per-app audience and per-app roles in access tokens", "PROPOSED"],
        ["A-D5", "AuthReflector is session-based; app APIs stay stateless", "PROPOSED"],
        ["A-D6", "Flyway from day one", "PROPOSED"],
        ["A-D7", "Jira: dedicated project, key AUTH (future shared services get their own projects)", "DECIDED (14 Nov 2026)"],
        ["A-D8", "Git: separate repository authreflector", "DECIDED (14 Nov 2026)"],
        ["A-D9", "Login and account pages: React SPA served by AuthReflector on the auth origin (session cookie + CSRF, no JWTs in the browser)", "DECIDED (14 Nov 2026)"],
        ["A-D10", "Web apps use a backend-for-frontend; tokens never reach the browser", "DECIDED (14 Nov 2026)"],
        ["A-D11", "Pull requests through GitHub CLI; main protected (PR + green Backend and Frontend CI required)", "DECIDED (14 Nov 2026)"],
        ["A-D12", "Login: Spring Security form login at POST /login with JSON handlers (not a custom login endpoint)", "DECIDED (14 Nov 2026)"],
        ["A-D13", "Signed in automatically after registration", "DECIDED (14 Nov 2026)"],
        ["A-D14", "Login UI: auth-app design plus 'continue to <app>' slot", "DECIDED (14 Nov 2026)"],
        ["A-D15", "Password hashes stored with algorithm prefix ({bcrypt}) via DelegatingPasswordEncoder, so the algorithm can be upgraded later", "DONE (AUTH-11)"],
        ["A-D16", "Packages by module (identity, security, web, config) instead of by layer", "DONE (AUTH-11)"],
        ["A-D17", "CSRF: XSRF-TOKEN cookie (readable, SameSite=Lax) echoed in X-XSRF-TOKEN header; session cookie AR_SESSION (HttpOnly, SameSite=Lax, Secure outside local), idle timeout 7 days", "DONE (AUTH-12)"],
        ["A-D18", "Server and tests run in UTC; local Postgres on 5434 (5432 SplitMate, 5433 native Windows service)", "DONE (AUTH-2)"],
        ["A-D19", "Known gap: registration 409 reveals existing emails; fixed in A2 with email verification + rate limiting", "OPEN (A2)"],
        ["A-D21", "Client apps stored in the database and managed on an admin page; changes apply without a restart (replaces config-as-code)", "DONE (AUTH-13, AUTH-18)"],
        ["A-D22", "Client secrets generated by AuthReflector (32 random bytes), shown once, stored {bcrypt}; regenerate replaces", "DONE (AUTH-18)"],
        ["A-D23", "Redirect URIs: https, or http only on 127.0.0.1/[::1] (OAuth 2.1); local apps are opened at 127.0.0.1", "DONE (AUTH-18)"],
        ["A-D24", "Per-app roles (client_roles) and per-app audience; sub = user UUID", "DONE (AUTH-14)"],
        ["A-D25", "Refresh token reuse detection: replaced tokens remembered as SHA-256; replay revokes the authorization", "DONE (AUTH-15)"],
        ["A-D26", "App launcher on /account: used apps glow and sort by sign-ins in the last 30 days; unused faded", "DONE (AUTH-19)"],
        ["A-D27", "Frontend-only apps use a JS SDK (oidc-client-ts, public client, PKCE, no refresh token); user and token in sessionStorage (tab only) instead of pure memory; CORS only for registered SPA origins", "DONE (AUTH-20)"],
        ["A-D28", "Apps with a backend use the Spring client starter (JitPack com.github.mudit1024:authReflector), four config lines, no login code", "DONE (AUTH-16, 0.1.1)"],
        ["A-D29", "Real-browser E2E suite (Playwright) across SDK and starter apps; run before releases (not in CI: spans repos)", "DONE (AUTH-17)"],
        ["A-D30", "Deferred: token values hashed at rest (A5), back-channel logout (A4), email verification + rate limiting (A2)", "OPEN"],
        ["A-D31+", "Remaining OPEN items (1, 4, 6-11), asked when their phase starts", "OPEN"],
    ],
    widths=[2.5, 9, 4],
)

doc.save(OUT)
print("saved", OUT)
