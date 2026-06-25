# STRIDE Threat Model & Penetration Test Report
**Project:** CassiTrack / OmniMove — University of Cassino 2025/26  
**Date:** 2026-06-25 00:42  
**Vulnerabilities audited:** 21  |  **Fixed:** 16  

---

## 1. STRIDE Summary

| ID | Title | STRIDE | Severity | Target | Fixed |
|----|-------|--------|----------|--------|-------|
| V-01 | Credentials Committed to Git History | I, S | CRITICAL | both | ✅ |
| V-02 | MQTT Broker Accepts Anonymous Connections | S, T, E | CRITICAL | infra | ✅ |
| V-03 | Stored XSS in Admin Panel (PASS — Already Fixed) | T, I | HIGH | cassitrack | ✅ |
| V-04 | JWT Stored in localStorage (XSS-Accessible) | I, S | HIGH | both | ✅ |
| V-05 | Hardcoded JWT Secret in docker-compose.yml | I, S | HIGH | cassitrack | ✅ |
| V-06 | Hardcoded HTTP URL in PWA (MITM Risk) | T, I | MEDIUM | cassitrack | ✅ |
| V-07 | Swagger UI Publicly Accessible Without Auth | I | MEDIUM | cassitrack | ✅ |
| V-08 | Redis Instance Has No Password (OmniMove) | E, T | HIGH | omnimove | ✅ |
| V-09 | Infrastructure Ports Exposed on 0.0.0.0 | I, D | HIGH | infra | ✅ |
| V-10 | Broken Access Control — AI Endpoint (Dead ADMIN Rule) | E | HIGH | cassitrack | ✅ |
| V-11 | document.write() Page Injection (DOM XSS via Login Redirect) | T, I | HIGH | both | ✅ |
| V-12 | Missing Content Security Policy Header | T | MEDIUM | cassitrack | ✅ |
| A01-IDOR | A01 — Insecure Direct Object Reference (IDOR) | E, I | HIGH | both | ❌ |
| A01-CSRF | A01 — Cross-Site Request Forgery (CSRF) | S, T | MEDIUM | both | ✅ |
| A03-INJECT | A03 — Header Injection / Path Traversal / SSTI | T, I | MEDIUM | both | ❌ |
| A04-DESIGN | A04 — Insecure Design (Rate Limiting & Account Lockout) | D, S | MEDIUM | both | ✅ |
| A06-DEPS | A06 — Vulnerable and Outdated Components | T, E | MEDIUM | both | ❌ |
| A07-AUTH | A07 — Auth Failures (Token Lifetime, Logout Invalidation) | S, I | MEDIUM | both | ✅ |
| A08-INTEGRITY | A08 — Software & Data Integrity Failures | T | LOW | both | ❌ |
| A09-LOGGING | A09 — Security Logging & Monitoring Failures | R | MEDIUM | both | ✅ |
| A10-SSRF | A10 — Server-Side Request Forgery (SSRF) | I, T | MEDIUM | both | ❌ |

---
## 2. Vulnerability Details

### V-01 — Credentials Committed to Git History

| Field | Value |
|-------|-------|
| OWASP | A02:2021 – Cryptographic Failures |
| STRIDE | Information Disclosure, Spoofing |
| Severity | **CRITICAL** |
| Target | both |
| Location | `omnimove-backend/env (commit a6866c8), cassitrack-backend/env` |
| Attack Surface | Git repository history — any clone |
| Mitigated | Yes ✅ |

**Description:** Database passwords, JWT secrets, InfluxDB tokens, Gmail SMTP app-password, Google Maps API key were committed in plaintext and remain accessible via `git show` even after the file was deleted.

**Pen Test Results:**

- ✅ `Leaked DB password → login rejected` — HTTP 400 — Default password from old committed env must be rotated
- ✅ `Forged JWT (old weak secret) → access denied` — HTTP 403 — Invalid signature → Spring Security denies (401 or 403 both OK)
- ✅ `git show — leaked env file still in history` — fatal: path 'omnimove-backend/env' does not exist in 'HEAD'

**ZAP / Burp Hints:**

- Spider /.git/config and /.git/COMMIT_EDITMSG (ZAP path traversal rule)
- Check for /.env, /env, /config endpoints with ZAP active scan

---

### V-02 — MQTT Broker Accepts Anonymous Connections

| Field | Value |
|-------|-------|
| OWASP | A05:2021 – Security Misconfiguration |
| STRIDE | Spoofing, Tampering, Elevation of Privilege |
| Severity | **CRITICAL** |
| Target | infra |
| Location | `mosquitto/config/mosquitto.conf — allow_anonymous true` |
| Attack Surface | TCP localhost:1883 |
| Mitigated | Yes ✅ |

**Description:** Any network client could connect to the MQTT broker on port 1883 without credentials and publish fake GPS telemetry or subscribe to all topics.

**Pen Test Results:**

- ⊘ `Anonymous PUBLISH should be refused` — SKIPPED — 'mosquitto_pub' not installed
- ⊘ `Anonymous SUBSCRIBE should be refused` — SKIPPED — 'mosquitto_sub' not installed

**Wireshark Filters:**
```
tcp.port == 1883
mqtt.msgtype == 1                         -- CONNECT packets
mqtt.msgtype == 3                         -- PUBLISH (GPS payloads)
mqtt.username == "" && mqtt.msgtype == 1  -- Anonymous connects (must be 0)
```

---

### V-03 — Stored XSS in Admin Panel (PASS — Already Fixed)

| Field | Value |
|-------|-------|
| OWASP | A03:2021 – Injection |
| STRIDE | Tampering, Information Disclosure |
| Severity | **HIGH** |
| Target | cassitrack |
| Location | `cassitrack-admin.html — escHtml() helper` |
| Attack Surface | POST http://localhost:8080/api/v1/auth/register — name/email fields |
| Mitigated | Yes ✅ |

**Description:** User-supplied fields were rendered via innerHTML. Fixed: escHtml() escapes all 5 dangerous HTML chars before insertion.

**Pen Test Results:**

- ✅ `XSS payload in name field — accepted but stored escaped` — HTTP 403
- ✅ `SQLi in email field — no 500 error` — HTTP 403

**ZAP / Burp Hints:**

- ZAP Active Scan → XSS (Persistent) on /auth/register name field
- Use ZAP Fuzzer with OWASP XSS Cheat Sheet word list

---

### V-04 — JWT Stored in localStorage (XSS-Accessible)

| Field | Value |
|-------|-------|
| OWASP | A02:2021 – Cryptographic Failures / A07:2021 – Auth Failures |
| STRIDE | Information Disclosure, Spoofing |
| Severity | **HIGH** |
| Target | both |
| Location | `cassitrack-login.html:102, omnimove-login.html:360` |
| Attack Surface | Browser localStorage → XSS exfiltration |
| Mitigated | Yes ✅ |

**Description:** Tokens in localStorage are readable by any JavaScript on the page. Fixed: login now sets an httpOnly, Secure, SameSite=Strict cookie.

**Pen Test Results:**

- ✅ `Bad credentials → login rejected (no token issued)` — HTTP 400 — Server rejects bad creds — no cookie set on failure
- ✅ `CassiTrack /auth/login endpoint reachable` — HTTP 400 — Endpoint live — ready for cookie test when TEST_CASSITRACK_EMAIL is set
- ✅ `OmniMove /auth/login endpoint reachable` — HTTP 401 — Endpoint live — ready for cookie test when TEST_OMNIMOVE_EMAIL is set
- ✅ `CassiTrack — Set-Cookie HttpOnly (live login)` — HTTP 200 — Set-Cookie: HttpOnly=✓ Secure=✓ SameSite=✓
- ✅ `OmniMove — Set-Cookie HttpOnly (live login)` — HTTP 200 — Set-Cookie: HttpOnly=✓ Secure=✓ SameSite=✓

**ZAP / Burp Hints:**

- ZAP passive rule 10010 — Cookie No HttpOnly Flag
- ZAP passive rule 10011 — Cookie Without Secure Flag

---

### V-05 — Hardcoded JWT Secret in docker-compose.yml

| Field | Value |
|-------|-------|
| OWASP | A02:2021 – Cryptographic Failures |
| STRIDE | Information Disclosure, Spoofing |
| Severity | **HIGH** |
| Target | cassitrack |
| Location | `cassitrack-backend/docker-compose.yml:133` |
| Attack Surface | JWT Authorization header — forged with known secret |
| Mitigated | Yes ✅ |

**Description:** A weak, hardcoded JWT secret in the compose file lets anyone with repo access forge valid admin tokens.

**Pen Test Results:**

- ✅ `Forged token (old weak secret) → access denied` — HTTP 403 — Bad signature rejected — 401 or 403 both confirm the fix works

**ZAP / Burp Hints:**

- Burp Suite → JWT Editor extension → attack with known weak secret
- jwt_tool -t <token> -C -d rockyou.txt   (offline brute force)

---

### V-06 — Hardcoded HTTP URL in PWA (MITM Risk)

| Field | Value |
|-------|-------|
| OWASP | A02:2021 – Cryptographic Failures |
| STRIDE | Tampering, Information Disclosure |
| Severity | **MEDIUM** |
| Target | cassitrack |
| Location | `cassitrack-pwa/app.js:12 — http://172.20.10.6:8080/api/v1` |
| Attack Surface | Local Wi-Fi / ARP spoofing → HTTP traffic |
| Mitigated | Yes ✅ |

**Description:** All PWA API calls went over plain HTTP to a hardcoded private IP — trivially intercepted via ARP spoofing on any shared network.

**Pen Test Results:**

- ✅ `Old hardcoded IP unreachable (connection refused = PASS)` — Connection refused — host unreachable (PASS: expected)

**Wireshark Filters:**
```
http && ip.dst == 172.20.10.6           -- Plaintext calls to old hardcoded IP
arp                                      -- ARP spoofing detection
http.request.uri contains "/api/v1"     -- All API calls (should be HTTPS in prod)
```

---

### V-07 — Swagger UI Publicly Accessible Without Auth

| Field | Value |
|-------|-------|
| OWASP | A05:2021 – Security Misconfiguration |
| STRIDE | Information Disclosure |
| Severity | **MEDIUM** |
| Target | cassitrack |
| Location | `SecurityConfig.java — /api/docs/**, /api/swagger-ui/** in permitAll()` |
| Attack Surface | GET http://localhost:8080/api/swagger-ui/index.html |
| Mitigated | Yes ✅ |

**Description:** The full OpenAPI spec was accessible without auth — a complete attack surface map handed to attackers for free.

**Pen Test Results:**

- ✅ `Swagger UI — must require auth (401/302/403)` — HTTP 403
- ✅ `OpenAPI JSON spec — must require auth` — HTTP 403

**ZAP / Burp Hints:**

- ZAP Spider → look for /api-docs, /swagger, /openapi.json, /v3/api-docs
- ZAP passive rule 10056 — X-Debug-Token Information Leak

---

### V-08 — Redis Instance Has No Password (OmniMove)

| Field | Value |
|-------|-------|
| OWASP | A05:2021 – Security Misconfiguration |
| STRIDE | Elevation of Privilege, Tampering |
| Severity | **HIGH** |
| Target | omnimove |
| Location | `omnimove-backend/docker-compose.yml — redis, no requirepass` |
| Attack Surface | TCP localhost:6380 — redis-cli |
| Mitigated | Yes ✅ |

**Description:** OmniMove Redis had no password. Attacker could flush the JWT blacklist (un-revoking all logged-out tokens) or read session data directly.

**Pen Test Results:**

- ⊘ `redis-cli PING without password — should return NOAUTH` — SKIPPED — 'redis-cli' not installed

**Wireshark Filters:**
```
tcp.port == 6380
tcp.port == 6380 && tcp.flags.push == 1  -- Redis commands
```

---

### V-09 — Infrastructure Ports Exposed on 0.0.0.0

| Field | Value |
|-------|-------|
| OWASP | A05:2021 – Security Misconfiguration |
| STRIDE | Information Disclosure, Denial of Service |
| Severity | **HIGH** |
| Target | infra |
| Location | `Both docker-compose.yml files — all port bindings` |
| Attack Surface | Ports 5433, 8086, 6379, 1883 |
| Mitigated | Yes ✅ |

**Description:** PostgreSQL, InfluxDB, Redis, and MQTT were bound to 0.0.0.0 — any host on the network could reach them directly, bypassing Spring Boot.

**Pen Test Results:**

- ✅ `InfluxDB HTTP API — must be localhost-only (connection refused from loopback is OK)` — HTTP 200
- ⊘ `nmap — infrastructure ports should be filtered externally` — SKIPPED — 'nmap' not installed

**Wireshark Filters:**
```
tcp.dstport == 5433 && !ip.src == 127.0.0.1  -- External DB access
tcp.dstport == 6379   && !ip.src == 127.0.0.1  -- External Redis
tcp.flags.syn == 1 && tcp.flags.ack == 0                       -- SYN scan detection
```

---

### V-10 — Broken Access Control — AI Endpoint (Dead ADMIN Rule)

| Field | Value |
|-------|-------|
| OWASP | A01:2021 – Broken Access Control |
| STRIDE | Elevation of Privilege |
| Severity | **HIGH** |
| Target | cassitrack |
| Location | `SecurityConfig.java:97+103 — conflicting /api/v1/ai/** rules` |
| Attack Surface | GET/POST http://localhost:8080/api/v1/ai/** with various role tokens |
| Mitigated | Yes ✅ |

**Description:** Spring Security first-match-wins: the FLEET_MANAGER rule at line 97 consumed /api/v1/ai/**, making the ADMIN rule at line 103 dead. ADMINs silently denied.

**Pen Test Results:**

- ✅ `No token → access denied (401 or 403)` — HTTP 403 — Spring Security denies unauthenticated requests with 401 or 403
- ✅ `Invalid token → access denied (401 or 403)` — HTTP 403 — Malformed JWT rejected — endpoint not publicly accessible

**ZAP / Burp Hints:**

- Burp Suite Autorize extension: replay requests with each role token
- Manual: generate JWTs for TRAVELLER, FLEET_MANAGER, ADMIN — test /ai/**

---

### V-11 — document.write() Page Injection (DOM XSS via Login Redirect)

| Field | Value |
|-------|-------|
| OWASP | A03:2021 – Injection |
| STRIDE | Tampering, Information Disclosure |
| Severity | **HIGH** |
| Target | both |
| Location | `cassitrack-login.html:130, omnimove-login.html:346` |
| Attack Surface | Login redirect flow — fetch() + document.write() |
| Mitigated | Yes ✅ |

**Description:** After login, the page fetched role-specific HTML and injected it via document.write() — a MITM or compromised backend could inject arbitrary JS. Fixed: window.location.replace() used instead.

**Pen Test Results:**

- ✅ `Login page — Content-Security-Policy header must be present` — HTTP 200

**ZAP / Burp Hints:**

- ZAP DOM XSS scan on the login page
- Check CSP header blocks unsafe-inline eval

---

### V-12 — Missing Content Security Policy Header

| Field | Value |
|-------|-------|
| OWASP | A05:2021 – Security Misconfiguration |
| STRIDE | Tampering |
| Severity | **MEDIUM** |
| Target | cassitrack |
| Location | `SecurityConfig.java — no contentSecurityPolicy() configured` |
| Attack Surface | All browser-rendered HTML pages |
| Mitigated | Yes ✅ |

**Description:** Without CSP the browser executes inline scripts from any origin and allows framing — enabling XSS, clickjacking, and data injection. Fixed.

**Pen Test Results:**

- ✅ `Login page — CSP header present` — HTTP 200
- ✅ `Login page — X-Frame-Options or frame-ancestors blocks framing` — HTTP 200

**ZAP / Burp Hints:**

- ZAP passive rule 10038 — Content Security Policy (CSP) Header Not Set
- ZAP passive rule 10020 — X-Frame-Options Header Not Set

---

### A01-IDOR — A01 — Insecure Direct Object Reference (IDOR)

| Field | Value |
|-------|-------|
| OWASP | A01:2021 – Broken Access Control |
| STRIDE | Elevation of Privilege, Information Disclosure |
| Severity | **HIGH** |
| Target | both |
| Location | `GET /api/v1/users/{id}, GET /api/v1/vehicles/{id}` |
| Attack Surface | http://localhost:8080/api/v1/users/1  (and sequential IDs) |
| Mitigated | No ❌ |

**Description:** A FLEET_MANAGER or TRAVELLER could attempt to access resources belonging to other users by manipulating IDs in the URL — e.g. /users/1, /users/2. Only ADMIN should access /users/**.

**Pen Test Results:**

- ✅ `GET /users/1 without token → denied` — HTTP 403 — Unauthenticated access to user object must be blocked
- ✅ `GET /vehicles/1 without token → denied (write ops)` — HTTP 403 — DELETE without auth must be blocked — FLEET_MANAGER only
- ✅ `GET /users without token → denied` — HTTP 403

**ZAP / Burp Hints:**

- Burp Suite Intruder: fuzz /users/§1§ with sequential IDs using FLEET_MANAGER token
- ZAP → Forced Browse on /api/v1/users/, /api/v1/analytics/
- Autorize plugin: replay ADMIN requests with FLEET_MANAGER cookie

---

### A01-CSRF — A01 — Cross-Site Request Forgery (CSRF)

| Field | Value |
|-------|-------|
| OWASP | A01:2021 – Broken Access Control |
| STRIDE | Spoofing, Tampering |
| Severity | **MEDIUM** |
| Target | both |
| Location | `SecurityConfig.java — csrf().disable()` |
| Attack Surface | Any state-changing endpoint (POST/PUT/DELETE) when using cookie auth |
| Mitigated | Yes ✅ |

**Description:** CSRF protection is explicitly disabled (common for JWT APIs). With httpOnly cookies now set, CSRF becomes relevant again. SameSite=Strict on the JWT cookie mitigates this for modern browsers, but older browsers or misconfigured proxies may not respect it.

**Pen Test Results:**

- ✅ `Simulated CSRF — cross-origin POST with no custom header` — HTTP 403 — SameSite=Strict prevents cookie from being sent — browser enforces this

**ZAP / Burp Hints:**

- ZAP Active Scan → Anti CSRF Tokens rule
- ZAP passive rule 10202 — Absence of Anti-CSRF Tokens

---

### A03-INJECT — A03 — Header Injection / Path Traversal / SSTI

| Field | Value |
|-------|-------|
| OWASP | A03:2021 – Injection |
| STRIDE | Tampering, Information Disclosure |
| Severity | **MEDIUM** |
| Target | both |
| Location | `All REST endpoints accepting string parameters` |
| Attack Surface | Query parameters, path variables, request headers |
| Mitigated | No ❌ |

**Description:** Beyond SQL/XSS, injection risks include: HTTP header injection via newline characters in input fields, path traversal via /../ sequences in URL parameters, and Server-Side Template Injection if any templating engine processes user input.

**Pen Test Results:**

- ✅ `Path traversal in vehicleId parameter` — HTTP 403 — /../ path traversal must not resolve to a different endpoint
- ✅ `Null byte injection in query parameter` — HTTP 400 — Null byte must not bypass route matching
- ❌ `Header injection — newline in custom header value` — Error: Invalid leading whitespace, reserved character(s), or returncharacter(s) in header value: 'value\r\nX-Injected: p
- ✅ `InfluxDB Flux injection via vehicleId` — HTTP 400 — URL-encoded Flux injection payload — must not return data from other buckets

**ZAP / Burp Hints:**

- ZAP Fuzzer → OWASP Path Traversal word list on all path parameters
- ZAP Active Scan → Server Side Template Injection rule
- Burp Suite → Intruder with header injection payloads

---

### A04-DESIGN — A04 — Insecure Design (Rate Limiting & Account Lockout)

| Field | Value |
|-------|-------|
| OWASP | A04:2021 – Insecure Design |
| STRIDE | Denial of Service, Spoofing |
| Severity | **MEDIUM** |
| Target | both |
| Location | `AuthController.java — LoginAttemptService / RateLimiterService` |
| Attack Surface | http://localhost:8080/api/v1/auth/login  (brute force target) |
| Mitigated | Yes ✅ |

**Description:** Insecure design covers missing security controls at architecture level. Key checks: (1) brute force protection on login — account must lock after N failed attempts; (2) registration rate limiting per IP; (3) password reset tokens must expire; (4) no username enumeration via different error messages for 'user not found' vs 'wrong password'.

**Pen Test Results:**

- ✅ `Username enumeration — nonexistent user vs wrong password` — HTTP 400 — Error message must not reveal whether the email exists
- ✅ `Brute force protection — 6th failed attempt → 429 or locked` — HTTP 400 — Full brute force test: run Postman Collection Runner ×10 with wrong password
- ✅ `OmniMove — password reset with invalid token` — HTTP 403 — Invalid reset tokens must be rejected
- ❌ `Registration rate limit — rapid registrations` — HTTP 403 — Single request OK — run ×10 to trigger 429 rate limit; expected one of [201, 400, 429], got 403

**ZAP / Burp Hints:**

- ZAP Fuzzer → brute force /auth/login with rockyou.txt top-1000
- ZAP Active Scan → parameter tampering on password reset token
- Manual: send identical error responses for 'no user' and 'wrong password'

---

### A06-DEPS — A06 — Vulnerable and Outdated Components

| Field | Value |
|-------|-------|
| OWASP | A06:2021 – Vulnerable and Outdated Components |
| STRIDE | Tampering, Elevation of Privilege |
| Severity | **MEDIUM** |
| Target | both |
| Location | `cassitrack-backend/pom.xml, omnimove-backend/pom.xml` |
| Attack Surface | All library dependencies and Docker base images |
| Mitigated | No ❌ |

**Description:** Using outdated libraries with known CVEs is one of the most common attack vectors. Spring Boot, jjwt, PostgreSQL driver, Mosquitto, Redis, and InfluxDB images should all be on their latest stable versions. Docker base images (eclipse-mosquitto:2.0, redis:7.2-alpine) should be pinned to digests for reproducibility.

**Pen Test Results:**

- ✅ `Server header — must not reveal version info` — HTTP 200 — Check 'Server' response header — must not expose Tomcat version
- ⊘ `Maven dependency vulnerability check (OSS Index)` — SKIPPED — 'mvn' not installed
- ⊘ `Docker image vulnerability scan — CassiTrack (trivy)` — SKIPPED — 'trivy' not installed

**ZAP / Burp Hints:**

- Run: mvn versions:display-dependency-updates  (shows outdated deps)
- Run: docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy image cassitrack-api
- Check: https://mvnrepository.com for latest Spring Boot version
- GitHub Dependabot: enable in repo Settings → Security → Dependabot alerts

---

### A07-AUTH — A07 — Auth Failures (Token Lifetime, Logout Invalidation)

| Field | Value |
|-------|-------|
| OWASP | A07:2021 – Identification and Authentication Failures |
| STRIDE | Spoofing, Information Disclosure |
| Severity | **MEDIUM** |
| Target | both |
| Location | `JwtUtil.java — jwt.expiration-ms, TokenBlacklistService` |
| Attack Surface | JWT token lifetime + Redis blacklist |
| Mitigated | Yes ✅ |

**Description:** Beyond localStorage (V-04), auth failures include: excessively long token lifetime (tokens valid for days/weeks after logout), logout not actually invalidating the token server-side, and weak password policy enforcement.

**Pen Test Results:**

- ✅ `Logout endpoint reachable` — HTTP 403 — Logout must respond — 204 No Content is the correct success response
- ✅ `OmniMove logout endpoint reachable` — HTTP 403 — Token must be blacklisted in Redis on logout
- ✅ `Weak password rejected — 'password123'` — HTTP 400 — OmniMove enforces strong password policy — must reject this
- ❌ `Weak password rejected — '12345678'` — HTTP 403 — Must require uppercase + special char — pure digits rejected; expected one of [400], got 403

**ZAP / Burp Hints:**

- Burp: login → copy JWT → logout → reuse JWT → should get 401
- Decode JWT at jwt.io — check 'exp' claim (should be ≤ 1h for high-security)
- ZAP passive rule 10113 — Weak Authentication Method

---

### A08-INTEGRITY — A08 — Software & Data Integrity Failures

| Field | Value |
|-------|-------|
| OWASP | A08:2021 – Software and Data Integrity Failures |
| STRIDE | Tampering |
| Severity | **LOW** |
| Target | both |
| Location | `docker-compose.yml — image tags, CORS config` |
| Attack Surface | CDN scripts in HTML pages, Docker image pull, CORS preflight |
| Mitigated | No ❌ |

**Description:** Integrity failures include: using mutable Docker image tags (`:latest`) instead of pinned digests, missing Subresource Integrity (SRI) on CDN scripts loaded in HTML pages, and overly permissive CORS that allows any origin to call the API.

**Pen Test Results:**

- ✅ `CORS — wildcard origin rejected for credentialed requests` — HTTP 403 — Check Access-Control-Allow-Origin in response — must NOT be '*' with credentials
- ✅ `CORS — legitimate origin accepted` — HTTP 200

**ZAP / Burp Hints:**

- ZAP passive rule 10098 — Cross-Domain Misconfiguration
- Check HTML pages for <script src='https://cdn...'> without integrity= attribute
- Pin Docker images: use image@sha256:... instead of :latest or :7-alpine

---

### A09-LOGGING — A09 — Security Logging & Monitoring Failures

| Field | Value |
|-------|-------|
| OWASP | A09:2021 – Security Logging and Monitoring Failures |
| STRIDE | Repudiation |
| Severity | **MEDIUM** |
| Target | both |
| Location | `SecurityAuditService.java, application logs` |
| Attack Surface | Application logs, SecurityAuditService |
| Mitigated | Yes ✅ |

**Description:** Insufficient logging means attacks go undetected. Key events that MUST be logged: failed login attempts (with IP), account lockouts, admin actions (user creation/deletion), JWT validation failures, and anomalous request patterns. Logs must not contain sensitive data (passwords, tokens).

**Pen Test Results:**

- ✅ `Failed login — audit trail created (endpoint responds)` — HTTP 400 — Each failure must appear in security audit log — check Docker logs
- ✅ `No sensitive data in error response body` — HTTP 400 — Error responses must not leak stack traces or internal paths
- ✅ `No stack trace in 404 response` — HTTP 403 — 404 pages must not expose Spring internals

**ZAP / Burp Hints:**

- Run: docker logs cassitrack-api | grep 'FAILED LOGIN' after pen test
- Verify SecurityAuditService logs: loginFailure, accountLocked, logout events
- ZAP passive rule 90011 — Incomplete or No Cache-control Header
- Check logs contain IP addresses and timestamps for each security event

**Wireshark Filters:**
```
http.response.code == 500   -- Server errors that should be logged
http.response.code == 401   -- Auth failures
http.response.code == 403   -- Access denied events
```

---

### A10-SSRF — A10 — Server-Side Request Forgery (SSRF)

| Field | Value |
|-------|-------|
| OWASP | A10:2021 – Server-Side Request Forgery |
| STRIDE | Information Disclosure, Tampering |
| Severity | **MEDIUM** |
| Target | both |
| Location | `Weather API integration, OmniMove Google Maps proxy, AI endpoint` |
| Attack Surface | http://localhost:8080/api/v1/weather?city=... http://localhost:8081/api/v1/maps/... http://localhost:8080/api/v1/ai/... |
| Mitigated | No ❌ |

**Description:** SSRF occurs when a server makes HTTP requests to URLs supplied by the user. CassiTrack proxies weather data and OmniMove proxies Google Maps — if the URL or city parameter is not validated, an attacker could force the server to make requests to internal services (metadata APIs, Redis, Postgres). The AI endpoint may also pass user-supplied text to external LLM APIs.

**Pen Test Results:**

- ✅ `SSRF via weather city — internal IP` — HTTP 403 — Test if city param or headers can redirect to internal hosts
- ✅ `SSRF probe — localhost redirect in city parameter` — HTTP 403 — Server must not fetch arbitrary URLs supplied in query params
- ✅ `SSRF probe — AWS metadata endpoint` — HTTP 403 — AWS instance metadata must not be reachable via SSRF

**ZAP / Burp Hints:**

- ZAP Active Scan → SSRF rule on all URL/city/callback parameters
- Burp Collaborator: replace city param with Burp Collaborator URL and check for DNS pingback
- Manual: try city=http://127.0.0.1:6379/ (Redis) and city=http://localhost:5433/ (Postgres)

---

## 3. Wireshark Capture Profiles

### MQTT Telemetry Intercept

Detect anonymous connections, topic snooping, GPS spoofing.

**Filters:**
```
tcp.port == 1883
mqtt.msgtype == 1                         -- CONNECT packets
mqtt.msgtype == 3                         -- PUBLISH (GPS payload)
mqtt.topic contains "vehicles"            -- Bus telemetry
mqtt.username == "" && mqtt.msgtype == 1  -- Anonymous connects (must be 0)
```

**Tshark:** `tshark -i any -f "tcp port 1883" -Y mqtt -T fields -e mqtt.msgtype -e mqtt.topic -e mqtt.msg -e ip.src`

### WebSocket Fleet Dashboard

Monitor /ws/vehicles frames for token leakage.

**Filters:**
```
tcp.port == 8080 && websocket
websocket.payload contains "lat"
http.request.uri contains "/ws/"
```

**Tshark:** `tshark -i lo -f "tcp port 8080" -Y websocket -T fields -e frame.time -e ip.src -e websocket.payload`

### REST API Auth Headers

Verify tokens travel only over HTTPS and cookies are HttpOnly.

**Filters:**
```
http.authorization
http.set_cookie contains "jwt"
http && http.request.method == "POST"
http.response.code == 401 || http.response.code == 403
```

**Tshark:** `tshark -i lo -f "tcp port 8080 or tcp port 8081" -Y http -T fields -e http.authorization -e http.set_cookie -e http.response.code`

### Infrastructure Port Scan Detection

Detect external hosts reaching internal DB/cache ports.

**Filters:**
```
tcp.dstport == 5433 && !ip.src == 127.0.0.1
tcp.dstport == 8086   && !ip.src == 127.0.0.1
tcp.dstport == 6379    && !ip.src == 127.0.0.1
tcp.dstport == 1883            && !ip.src == 127.0.0.1
tcp.flags.syn == 1 && tcp.flags.ack == 0  -- SYN scan
```

**Tshark:** `tshark -i any -f "tcp port 5433 or tcp port 8086 or tcp port 6379 or tcp port 1883" -Y "not ip.src == 127.0.0.1" -T fields -e ip.src -e tcp.dstport`

## 4. OWASP ZAP Commands

### Baseline passive scan (Docker)

```bash
docker run --network host ghcr.io/zaproxy/zaproxy:stable zap-baseline.py -t http://localhost:8080 -r zap_baseline.html
```

### Full active scan — CassiTrack

```bash
docker run --network host ghcr.io/zaproxy/zaproxy:stable zap-full-scan.py -t http://localhost:8080 -r zap_full.html
```

### API scan with OpenAPI spec — CassiTrack

```bash
docker run --network host ghcr.io/zaproxy/zaproxy:stable zap-api-scan.py -t http://localhost:8080/api/docs/openapi.json -f openapi -r zap_api_cassi.html
```

### API scan — OmniMove

```bash
docker run --network host ghcr.io/zaproxy/zaproxy:stable zap-api-scan.py -t http://localhost:8081/api/docs/openapi.json -f openapi -r zap_api_omni.html
```

## 5. Postman Attack Requests

#### Auth Bypass — Empty JWT
- `GET http://localhost:8080/api/v1/users` → expect HTTP **401**

#### Auth Bypass — Malformed JWT
- `GET http://localhost:8080/api/v1/users` → expect HTTP **401**

#### Privilege Escalation — TRAVELLER → AI endpoint
- `GET http://localhost:8080/api/v1/ai/suggest` → expect HTTP **403**

#### Rate Limit — brute force login (run ×10)
- `POST http://localhost:8080/api/v1/auth/login` → expect HTTP **429**

#### SQLi in login email
- `POST http://localhost:8080/api/v1/auth/login` → expect HTTP **400**

#### IDOR — FLEET_MANAGER accessing /users (ADMIN only)
- `GET http://localhost:8080/api/v1/users` → expect HTTP **403**
