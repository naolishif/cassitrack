# CassiTrack & OmniMove — Security Engineering Report

**Course:** Computer and Network Security (CNS) — Master's Degree
**University:** University of Cassino and Southern Lazio (UNICAS)
**Project:** CassiTrack + OmniMove — Real-Time Smart Mobility Platform for Cassino
**Competition context:** CINI Smart City University Challenge, 10th Edition

---

## 1. Introduction

This report documents the design and the security engineering work carried out on CassiTrack and OmniMove, the two systems we built for the CNS course project. The first half of the report explains, in plain terms, what the systems do and how they are put together, so that the architecture can be understood and defended without requiring a deep background in distributed systems. The second half is the core security deliverable: a walk-through of the OWASP Top 10 (2021) vulnerability categories, explaining what each category means and, concretely, what in our codebase prevents or mitigates it, backed by real code excerpts. The report closes with the results of the automated penetration testing we ran with OWASP ZAP, a conclusion, and an honest account of the limitations of our work.

Throughout the report we have tried to strike a balance: each topic is explained from first principles (what the vulnerability is, why it matters) before we describe our specific implementation, so the report is useful both as project documentation and as a study aid for the oral defense.

---

## 2. What Are CassiTrack and OmniMove

**CassiTrack** is a real-time bus fleet monitoring system built for Linea 16 of MAGNI Autoservizi, the bus line that connects Cassino's city centre to the UNICAS campus via Via Folcara. The motivation behind it is simple and personal: Bus 16 currently has no live tracking at all, so passengers wait at the stop with no idea whether the bus is two minutes away or twenty. CassiTrack solves this by receiving GPS positions from the buses, storing them, computing arrival predictions, and exposing that information through a web dashboard, a public API, and a mobile-friendly app.

**OmniMove** is a multimodal journey-planning layer built on top of CassiTrack. Instead of only telling you where the bus is, OmniMove helps a traveller decide *how* to get from A to B by comparing bus, bike, e-scooter, and walking options side by side, each with a price estimate, an expected duration, and a "Green Index" CO₂ score. OmniMove consumes CassiTrack's live data (bus positions and arrival times) and combines it with external services — Google Maps for traffic-aware travel times, a local bike/scooter rental provider (Elerent), and weather data — to produce a complete journey recommendation.

The two systems are deliberately separated: CassiTrack is the fleet-monitoring backend (it knows about buses, routes, and schedules), while OmniMove is the passenger-facing planning backend (it knows about journeys, pricing, and multimodal comparisons) and talks to CassiTrack as a client. This separation mirrors a common real-world pattern in smart-mobility platforms, where a transport operator's own tracking system is kept separate from the public-facing trip planner that aggregates several operators and transport modes.

Both systems also include role-based web dashboards: an administrator panel for managing user accounts, a fleet-manager dashboard for live analytics, and (for OmniMove) a traveller-facing planning interface. A Python GPS simulator stands in for the real ESP32-based GPS trackers that would eventually be installed on the physical buses, so the whole pipeline can be demonstrated end-to-end without hardware.

---

## 3. System Architecture

At a high level, data flows through the platform in one direction — from the bus to the passenger — and is enriched at every stage:

1. **Bus / GPS source.** Each bus is expected to carry a small GPS tracker (an ESP32 microcontroller in the production design; in our demo, a Python script that simulates two buses moving along the real **Linea 16 route**, publishing a new position every 15 seconds).
2. **MQTT broker.** The GPS devices publish their position to an Eclipse Mosquitto broker — a lightweight messaging system designed for exactly this kind of "many small devices sending frequent updates" scenario. Using MQTT instead of, say, having every bus call a REST endpoint directly, decouples the data producers (buses) from the data consumer (the backend) and copes gracefully with buses going briefly offline.
3. **CassiTrack backend (Spring Boot, port 8080).** This is the core of the platform. It subscribes to the MQTT broker, validates every incoming position, and then: stores the full history in InfluxDB (a database built for time-series data, ideal for "where was this bus at every point in time"), caches the *current* position of every bus in Redis (so live-map queries are fast), and uses PostgreSQL with the PostGIS spatial extension to hold static reference data such as routes, stops, and schedules. On top of this data it runs an ETA service (arrival predictions), a schedule-adherence service (is the bus on time, late, or early), a GTFS-Realtime feed generator (so the data is consumable by national transport-data standards), and an AI assistant (backed by the Claude API) that lets a fleet manager ask natural-language questions about the fleet.
4. **OmniMove backend (Spring Boot, port 8081).** This is a separate service that queries CassiTrack's API for live bus data, and combines it with external providers (Google Maps for traffic-aware travel time, Elerent for bike/scooter pricing, a weather API) to build and rank multimodal journey options for a traveller.
5. **Frontends.** A desktop live map (Leaflet.js-based), a mobile-installable Progressive Web App, and the role-specific admin/fleet-manager/traveller HTML dashboards consume the two backends' REST APIs. All API endpoints are documented with OpenAPI 3.0 and explorable through Swagger UI.

A simplified way to describe it to a non-technical evaluator: think of CassiTrack as the "control tower" that always knows where every bus is and when it will arrive, and OmniMove as the "travel agent" that uses the control tower's information, plus knowledge of other transport options, to tell a passenger the best way to make a specific trip right now.

Both backends share the same overall security model rather than reinventing it independently: stateless authentication via signed JSON Web Tokens (JWT), role-based authorization (ADMIN, FLEET_MANAGER, DRIVER, TRAVELLER), password hashing with BCrypt, and a set of supporting services — rate limiting, brute-force lockout, audit logging, token revocation — that are described in detail in the OWASP section below. Both backends are containerised with Docker Compose, which also runs their dependent infrastructure (PostgreSQL, InfluxDB, Redis, and, for CassiTrack, the Mosquitto broker).

---

## 4. Security Assessment Methodology

Our security work followed three complementary approaches, in line with what is normally expected for this kind of course project:

- **Manual code review against the OWASP Top 10 (2021).** We went through both backends' controllers, security configuration, data-access code, and the static HTML/JS dashboards, checking each OWASP category against the actual implementation rather than against documentation claims.
- **STRIDE-style threat modelling.** For each component (web frontends, REST APIs, the MQTT pipeline, the supporting databases), we asked which of the six STRIDE threats — Spoofing, Tampering, Repudiation, Information Disclosure, Denial of Service, Elevation of Privilege — applied, and verified whether a corresponding control existed.
- **Automated dynamic testing with OWASP ZAP**, run against the live, running CassiTrack backend, both as a general web-application scan and as an API-aware scan driven by our OpenAPI specification. The results are discussed in Section 6.

Findings were triaged and several were fixed directly during the review cycle (these are marked accordingly in the sections below); a small number remain open and are listed transparently in the Limitations section rather than hidden.

---

## 5. OWASP Top 10 (2021): Vulnerabilities and How We Addressed Them

This section goes through each of the ten OWASP categories. For each one we explain, in general terms, what the vulnerability class consists of, and then describe specifically what in CassiTrack/OmniMove prevents or mitigates it, with a short excerpt of the actual code responsible.

### A01 — Broken Access Control

Broken Access Control happens when an application fails to properly enforce *who is allowed to do what*. A classic example is an Insecure Direct Object Reference (IDOR): a user is logged in legitimately, but the application lets them access or modify a resource that does not belong to them simply by guessing or changing an identifier (for instance, changing `/users/3` to `/users/4` in the URL).

In our system, every endpoint is mapped to one or more required roles inside Spring Security's filter chain, so a request is rejected before it ever reaches business logic if the caller's JWT does not carry the right authority. For example, CassiTrack restricts the admin dashboard and user-management API to the ADMIN role, and the fleet-manager analytics pages to the FLEET_MANAGER role:

```java
// cassitrack-backend/.../config/SecurityConfig.java
.requestMatchers(
        "/cassitrack-fleetmanager.html",
        "/api/v1/analytics/**",
        "/cassitrack-analytics.html"
).hasAnyAuthority("FLEET_MANAGER", "ROLE_FLEET_MANAGER")

.requestMatchers(
        "/cassitrack-admin.html",
        "/api/v1/users/**",
        "/api/v1/auth/register"
).hasAnyAuthority("ADMIN", "ROLE_ADMIN")
```

We also found and fixed a real broken-access-control bug during this review: the `/api/v1/ai/**` endpoint was matched by two conflicting rules, and because Spring Security uses "first match wins", the FLEET_MANAGER rule silently absorbed all requests, meaning an authenticated ADMIN was being denied access to a feature they were supposed to have. The fix was to merge the two rules into a single one listing every authority that should be allowed.

Penetration testing also confirmed that direct object access is correctly blocked: requests to `/api/v1/users/{id}` or `/api/v1/vehicles/{id}` without a valid token, or with a token belonging to a lower-privileged role, are rejected with an HTTP 403, rather than returning the requested resource.

One access-control issue remains open and unresolved at the time of writing: the driver-facing GPS-publishing endpoint accepts a `vehicle_id` from the request body without verifying that the authenticated driver is actually the one assigned to that vehicle, so a malicious or compromised driver account could currently publish a false position for a different bus. The relevant code still contains the acknowledgment of this gap:

```java
// cassitrack-backend/.../controller/DriverController.java
String vehicleId = (String) body.getOrDefault("vehicle_id", "UNKNOWN_VEHICLE");

// TODO: Query your database here to verify this driverEmail is currently
// assigned to this vehicleId!
// Example: if (!assignmentService.isDriverAssignedToBus(driverEmail, vehicleId)) throw new SecurityException();
```

We list this honestly in the Limitations section, together with the straightforward fix (adding a vehicle-assignment table and checking it before publishing).

### A02 — Cryptographic Failures

This category covers situations where sensitive data is exposed because it was not protected with appropriate cryptography — for instance, storing passwords in plain text, using a weak or predictable signing key for authentication tokens, or transmitting credentials over an unencrypted channel.

We never store a user's password directly; it is hashed with BCrypt (an adaptive, salted hashing algorithm specifically designed for passwords) before being saved, and is never logged or returned in any API response. Authentication tokens are signed JWTs using HMAC-SHA256, and the signing secret is read exclusively from an environment variable, with no hardcoded fallback value in the configuration — meaning the application will fail to start rather than silently use a guessable secret:

```java
// cassitrack-backend/.../security/JwtUtil.java
@Value("${jwt.secret}")
private String jwtSecret;

private SecretKey getSigningKey() {
    return Keys.hmacShaKeyFor(jwtSecret.getBytes());
}
```

During the review we also fixed a related weakness: tokens used to be returned to the browser in the JSON response body and stored by the frontend in `localStorage`, which is readable by any JavaScript running on the page — including injected, malicious JavaScript from an XSS attack. We changed login to deliver the token as an `httpOnly`, `Secure`, `SameSite=Strict` cookie instead, so client-side JavaScript can no longer read it at all:

```java
// cassitrack-backend/.../controller/AuthController.java
response.setHeader("Set-Cookie",
    String.format("%s=%s; Path=/; Max-Age=%d; HttpOnly; Secure; SameSite=Strict",
        JWT_COOKIE_NAME, token, (int) (jwtUtil.getExpirationMs() / 1000)));
```

Outbound email (used for OmniMove's verification and password-reset flows) is sent over STARTTLS rather than plaintext SMTP.

### A03 — Injection

Injection vulnerabilities occur when untrusted input is interpreted as code or commands by a downstream system — the best-known example is SQL Injection, where attacker-controlled text ends up inside a database query. Cross-Site Scripting (XSS) is also classified under this category in OWASP's 2021 list: it happens when attacker-controlled text ends up being interpreted as HTML/JavaScript in someone else's browser.

We avoid classic SQL injection structurally: all database access goes through Spring Data JPA with Hibernate, which always uses parameterised queries, so there is no string concatenation of user input into SQL anywhere in the codebase.

The more interesting findings here were stored XSS issues in the two admin dashboards, which we identified and fixed during this review. Both `cassitrack-admin.html` and `omnimove-admin.html` originally rendered user-supplied fields (name, email, etc.) directly into the page using `innerHTML` template literals, with no escaping — so a user registering with a name like `<script>...</script>` would have that script execute in an administrator's browser the next time the user list was loaded, potentially stealing the admin's session cookie. The fix was twofold: an `escHtml()` helper that escapes the five dangerous HTML characters is now applied to every user-controlled field before it is inserted into the page, and the registration request objects now validate the input itself:

```javascript
// omnimove-backend/.../static/omnimove-admin.html
function escHtml(s) { /* escapes &, <, >, ", ' */ }
...
<td>${escHtml(u.name)}</td>
<td style="color:var(--text-secondary);font-size:12px">${escHtml(u.email)}</td>
```

```java
// omnimove-backend/.../dto/RegisterRequest.java
@NotBlank
@Size(max = 100)
@Pattern(regexp = "[^<>\"']*", message = "Name contains invalid characters")
private String name;
```

A third, less obvious injection point was the vehicle ID carried in MQTT GPS messages: it was stored and later displayed on the analytics dashboard without any character restriction, so a rogue or compromised GPS publisher could have smuggled an XSS payload into a field that *looked* like a vehicle identifier. We closed this by validating the format of `vehicle_id` at the point it enters the system, before it is ever stored:

```java
// cassitrack-backend/.../mqtt/MqttMessageHandler.java
if (!pos.getVehicleId().matches("[A-Za-z0-9_\\-]{1,50}")) return false;
```

Finally, the NeTEx XML import feature (used to ingest standard transport-network data) explicitly disables external entity resolution and DTD processing, which prevents XML External Entity (XXE) attacks — a specialised injection technique where a malicious XML file tries to make the parser read local files or make outbound network requests:

```java
// .../service/NetexImportService.java
xmlFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
xmlFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
```

### A04 — Insecure Design

This category is broader than a single bug: it is about whether security was considered at the architecture level at all — for example, whether there is any brute-force protection on login, whether a password-reset token can be reused indefinitely, or whether error messages accidentally tell an attacker which part of their guess was wrong (username enumeration).

Both backends lock an account out after repeated failed login attempts, though they do it slightly differently, reflecting how each system's accounts are created. CassiTrack accounts are created only by an administrator (there is no public self-registration), so a Redis-backed counter blocks an email address for 15 minutes after 5 failed attempts and then automatically resets:

```java
// cassitrack-backend/.../service/LoginAttemptService.java
private static final int MAX_ATTEMPTS = 5;
private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
```

OmniMove, by contrast, exposes public self-registration to travellers, so the lockout is tracked per-user in the database and — deliberately — does **not** auto-expire; the account stays locked until the user goes through the password-reset flow, which is a stronger response appropriate for a public-facing system. OmniMove also rate-limits registration itself (5 attempts per IP per hour) and the password-reset/resend-verification flows (3 per hour), using a Redis counter:

```java
// omnimove-backend/.../service/RateLimiterService.java
/** 5 registrations per IP per hour */
public boolean allowRegister(String ip) {
    return isAllowed("rl:register:" + ip, 5, Duration.ofHours(1));
}
```

We also avoid username enumeration: the forgot-password endpoint always returns the same message ("if that email is registered, you will receive a reset link") regardless of whether the address exists, and login failures return a single generic message rather than distinguishing "wrong password" from "no such user".

One low-severity design leftover remains: CassiTrack's registration response currently includes a placeholder string, `"MOCK_TOKEN_UNTIL_LOGIN"`, in the `token` field, rather than omitting it. It is correctly rejected by the authentication filter if a client mistakenly tries to use it, but it is a confusing API contract that should simply be removed; it is listed in the Limitations section.

### A05 — Security Misconfiguration

Security misconfiguration covers anything that is wrong not because of a bug in custom code, but because of how a component was set up — default credentials left in place, unnecessary services exposed to the network, debug information left switched on, or missing security headers.

Several concrete misconfigurations were found and corrected during this review:

The MQTT broker originally accepted anonymous connections, meaning anyone who could reach it on the network could publish fake bus positions or eavesdrop on the real ones. We switched it to require authentication:

```
# mosquitto/config/mosquitto.conf
allow_anonymous false
password_file /mosquitto/config/passwd
acl_file /mosquitto/config/acl
```

All the supporting infrastructure containers — PostgreSQL, InfluxDB, Redis, and Mosquitto — were originally bound to `0.0.0.0`, meaning any machine on the same network could talk to them directly, bypassing the application entirely. They are now bound to `127.0.0.1` (localhost-only), so only the backend process on the same machine can reach them:

```yaml
# cassitrack-backend/docker-compose.yml
ports:
  - "127.0.0.1:5433:5432"   # PostgreSQL — localhost only
  - "127.0.0.1:6379:6379"   # Redis — localhost only
```

OmniMove's Redis instance — which backs the JWT logout/revocation blacklist — was running without a password, which would have let anyone with network access to that port erase the blacklist and effectively "un-log-out" a stolen token. It now requires the same password as the rest of the stack:

```yaml
# omnimove-backend/src/main/resources/application.yml
redis:
    host: localhost
    port: 6380
    password: ${SPRING_REDIS_PASSWORD}
```

The Swagger UI / OpenAPI documentation endpoints, which describe every API route in detail, were originally public; an attacker could use them as a ready-made map of the attack surface. Both backends now require authentication to view them.

Finally, CassiTrack now sets a full Content-Security-Policy together with a small set of supporting headers (`Permissions-Policy`, `Cross-Origin-Resource-Policy`, `Strict-Transport-Security`, frame-deny) to reduce the impact of any XSS that might slip through and to prevent the page from being framed by another site:

```java
// cassitrack-backend/.../config/SecurityConfig.java
http.headers(headers -> headers
    .frameOptions(frame -> frame.deny())
    .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31536000).includeSubDomains(true))
    .contentSecurityPolicy(csp -> csp.policyDirectives(
        "default-src 'self'; script-src 'self' 'unsafe-inline'; ... frame-ancestors 'none'; " +
        "object-src 'none'; base-uri 'self'; form-action 'self';"
    ))
);
```

OmniMove currently only sets `frameOptions(sameOrigin)` and does not yet declare an equivalent Content-Security-Policy header; this asymmetry between the two backends is a real, acknowledged gap and is listed in the Limitations section.

### A06 — Vulnerable and Outdated Components

Many real-world breaches do not come from a flaw in an organisation's own code at all, but from a known, already-patched vulnerability in a third-party library or framework that was never updated.

Both backends wire the OWASP Dependency-Check Maven plugin into the build itself, configured to fail the build outright if any dependency is found to have a known vulnerability with a CVSS severity score of 7 or higher (i.e. "high" or "critical"):

```xml
<!-- cassitrack-backend/pom.xml and omnimove-backend/pom.xml -->
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>10.0.4</version>
    <configuration>
        <failBuildOnCVSS>7</failBuildOnCVSS>
        ...
    </configuration>
</plugin>
```

This means the check is not a one-off manual exercise but a continuous gate: every time the project is built with `mvn verify`, the dependency list is checked against the National Vulnerability Database, and a newly disclosed high-severity CVE in, say, Spring Boot or the JWT library would block the build until it is addressed. We did not, however, have the auxiliary command-line tools (`mvn`, `trivy`) available in the sandboxed environment used for the live penetration-testing session, so this control could not be exercised end-to-end during that particular test run; this is noted honestly in the Limitations section.

### A07 — Identification and Authentication Failures

This category covers weaknesses in how a system verifies who someone is and keeps them logged in (or logged out) correctly — for example, sessions that never expire, weak password rules, or a "logout" that does not actually invalidate anything server-side.

JWTs in both backends expire after one hour. Password complexity is enforced server-side (minimum length, plus uppercase, lowercase, digit, and special-character requirements) via a regular expression applied to every registration and password-reset request:

```java
// omnimove-backend/.../controller/AuthController.java
private boolean isPasswordValid(String password) {
    if (password == null || password.length() < 8) return false;
    return password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{8,}$");
}
```

Logging out is not just a frontend action: the server records the still-valid token in a Redis-backed blacklist for whatever time remains on it, and the JWT filter consults that blacklist on every request, so a logged-out token cannot be replayed even if an attacker captured it beforehand:

```java
// cassitrack-backend/.../service/TokenBlacklistService.java
public void blacklist(String token, long remainingMs) {
    redisTemplate.opsForValue().set(PREFIX + token, "revoked", Duration.ofMillis(remainingMs));
}
```

OmniMove additionally requires email verification before first login (a UUID-based token, expiring after 24 hours, sent via a link rather than embedded in a logged URL), and password-reset tokens are single-use, UUID-based, and expire after one hour.

### A08 — Software and Data Integrity Failures

This category is about trusting data or code without verifying its integrity — for example, deserialising arbitrary, attacker-controlled Java objects (a common and dangerous pattern in older Java applications), or loading a third-party script from a CDN without any way to detect if that script has been tampered with.

We do not use Java's native object deserialisation (`ObjectInputStream`) anywhere on untrusted input; all JSON parsing goes through Jackson, mapping directly into well-defined DTO classes rather than generic, polymorphic objects. Redis is used purely to store strings (tokens, counters), never serialised Java objects. The same XML-parsing hardening described under A03 (disabling external entities and DTDs) also protects the integrity of NeTEx data imports.

Two integrity-related items remain as accepted, lower-severity gaps rather than fixed issues: the Docker Compose files reference infrastructure images by a mutable tag (e.g. `redis:7.2-alpine`) rather than an immutable digest, so a tag could in theory point to a different image in the future; and the CDN-hosted fonts/styles loaded by the dashboards do not yet carry a Subresource Integrity (`integrity=`) attribute, which would let the browser refuse to load the file if the CDN ever served something different from what we expect. Both are listed in the Limitations section as concrete, low-effort follow-up work.

### A09 — Security Logging and Monitoring Failures

If an attack happens and nobody — and nothing — notices, the strongest access control in the world does not help. This category covers whether security-relevant events are actually recorded, and recorded *safely* (i.e. without leaking the very secrets you are trying to protect).

Both backends have a dedicated `SecurityAuditService`, writing to its own logger topic, that records every authentication-relevant event: registration, login success/failure, account lockout, logout, email verification, password-reset requests, and weak-password rejections. OmniMove's version, shown below, is representative of both:

```java
// omnimove-backend/.../service/SecurityAuditService.java
public void loginFailure(String email, String ip) {
    log.warn("LOGIN_FAILURE email={} ip={}", email, ip);
}
public void accountLocked(String email) {
    log.warn("ACCOUNT_LOCKED email={}", email);
}
```

Crucially, none of these methods ever take a password or a raw token as a parameter, so it is structurally difficult to accidentally log a credential — the audit service simply has no code path through which one could pass.

### A10 — Server-Side Request Forgery (SSRF)

SSRF happens when a server can be tricked into making an HTTP request to a destination chosen by the attacker — for example, a "fetch this image URL" feature that is abused to make the server probe its own internal network or an internal-only metadata service.

Our strongest defence against SSRF here is architectural rather than a filter or a blocklist: none of our outbound integrations accept a URL, hostname, or IP address from the client at all. The traffic-aware ETA endpoint, for instance, only accepts a `stopId` — one of a small, fixed set of known bus stops — and it is the server itself, not the client, that turns that identifier into coordinates and calls a hardcoded Google Maps endpoint:

```java
// omnimove-backend/.../controller/TrafficController.java
public ResponseEntity<List<TrafficAwareETAService.TrafficEtaResult>> getTrafficEta(
        @Parameter(description = "Stop ID, e.g. UNI for Università Folcara")
        @RequestParam String stopId) { ... }
```

```java
// omnimove-backend/.../service/GoogleMapsService.java
private static final String BASE_URL =
        "https://maps.googleapis.com/maps/api/distancematrix/json";
```

The same pattern holds for every other outbound call the platform makes: OmniMove's call into CassiTrack uses a URL read from server-side configuration (`${CASSITRACK_URL}`), the call to the Anthropic API for the AI assistant uses a hardcoded base URL, and the weather lookup uses a hardcoded provider URL with a fixed city. Because the client never supplies anything resembling a URL or a host, there is no input for an SSRF payload to even be placed into.

---

## 6. Penetration Testing Results (OWASP ZAP)

In addition to the manual review above, we ran the OWASP ZAP automated security scanner against the live, running CassiTrack backend, using two complementary scan profiles: a general web-application scan against the running site, and an API-aware scan driven directly by our published OpenAPI specification.

The general web-application scan reported zero high-risk alerts. It did surface five medium-risk findings, all of them refinements to our Content-Security-Policy rather than missing protections outright. ZAP flagged that the policy allows `'unsafe-inline'` for both scripts and styles; this is a real, acknowledged trade-off — several of our dashboard pages still use inline `<script>` blocks and inline style attributes, and tightening this would require moving that code into external files, which we have not yet done. It also flagged the `img-src` directive for allowing any HTTPS source (we deliberately left this open because the live map loads tiles from an external map-tile provider), and it flagged a small number of CDN-hosted assets (fonts and the mapping library) for not carrying a Subresource Integrity hash — the same gap already noted under A08 above. Interestingly, ZAP also reported that the version of the CSP header it observed on the live server did not include the `form-action`, `object-src`, and `base-uri` directives that are present in our `SecurityConfig.java` source; we are flagging this discrepancy transparently rather than quietly resolving it, since it suggests either a stale build was running at scan time or an inconsistency in how the header is rendered, and it is worth re-verifying against a fresh deployment before the project is handed over.

Four low-risk findings concerned newer browser cross-origin isolation headers — `Cross-Origin-Embedder-Policy`, `Cross-Origin-Opener-Policy`, and, on some responses, `Cross-Origin-Resource-Policy` and `Permissions-Policy` — being absent or inconsistent across different routes, even though the latter two are explicitly set for some endpoints in our security configuration. This points to the headers not yet being applied uniformly to every route, which is a reasonable next-step hardening item rather than an active exploit path. The remaining three informational findings were not security issues: ZAP noticed that some of our own internal code comments (including the very fix-tracking comments quoted earlier in this report, such as references to specific fix identifiers) are visible in client-delivered JavaScript files, which is a minor information-disclosure hygiene point worth cleaning up before any public release, and it correctly classified the application as a modern, JavaScript-driven web app with non-cacheable dynamic content, both of which are expected and benign observations.

The second scan, run against our OpenAPI specification so that ZAP understood the shape of the REST API, reported zero high- and zero medium-risk alerts. The single low-risk finding was a false trigger: ZAP expected every endpoint listed in the API spec to return JSON, and flagged the root path for returning an HTML login page instead — which is correct behaviour, since that route is the human-facing login screen, not an API endpoint. The informational findings were, if anything, a positive signal: seventeen instances were logged of the server correctly returning an HTTP 403 (Forbidden) response when ZAP's automated scanner probed protected routes — including the actuator health endpoint, generic API paths, and a randomly generated numeric path — without a valid authenticated session. In other words, the scanner's own attempts at unauthorized access were consistently and correctly rejected rather than silently succeeding or leaking data, which is exactly the behaviour we want our access-control layer to exhibit under hostile probing.

Taken together, the dynamic scan results corroborate the manual review: no SQL injection, no reflected or DOM-based XSS, no broken authentication, and no missing access control were found by the automated tooling. The open items are all hardening refinements around the Content-Security-Policy and a small number of newer, optional security headers, plus one discrepancy between source and a scanned build that should be re-checked before the system is considered production-ready.

---

## 7. Conclusion

CassiTrack and OmniMove were designed and reviewed with a "secure by default, fix what's found" approach: stateless JWT authentication, role-based access control enforced centrally in the Spring Security filter chain, BCrypt password hashing, parameterised database queries, and dedicated services for rate limiting, brute-force lockout, token revocation, and security audit logging are present across both backends from the start, not bolted on afterwards. The review process described in this report — manual analysis against all ten OWASP Top 10 (2021) categories, STRIDE-based threat modelling, and dynamic scanning with OWASP ZAP — surfaced a meaningful number of real issues over the course of the project, almost all of which (anonymous MQTT access, infrastructure ports exposed to the network, an unauthenticated Redis instance, stored XSS in both admin dashboards, tokens readable via JavaScript, a dead access-control rule on the AI endpoint, and a missing Content-Security-Policy) were identified and fixed during the review cycle itself, with the fix and the reasoning behind it documented in the code. The automated ZAP scan, run independently against the live system, did not surface any high- or medium-risk finding that the manual review had missed, which gives us reasonable confidence that the security posture documented here reflects the system as it actually behaves, not just as it is intended to behave on paper.

At the same time, we do not consider the system "finished" from a security standpoint, and the next section lists, deliberately and specifically, what is still open. We see that transparency as part of the deliverable: a security report that claims zero remaining issues is far less credible — and far less useful to whoever inherits this codebase next — than one that draws a clear line between what has been verified, what has been fixed, and what is known to still need work.

---

## 8. Limitations

The following gaps were identified during this review and remain open. We list them explicitly, together with what would be required to close each one, rather than omitting them:

**Driver GPS spoofing (A01).** The driver-facing location-publishing endpoint does not yet verify that the authenticated driver is actually assigned to the vehicle ID they are submitting a position for. A malicious or compromised driver account could currently publish a false position under a different bus's identifier. Fixing this requires adding a driver-to-vehicle assignment relationship to the data model and checking it before the position is published — the code already contains a `TODO` marking exactly where this check belongs.

**Asymmetric Content-Security-Policy coverage (A05).** CassiTrack defines a full, explicit CSP plus several supporting headers (`Permissions-Policy`, `Cross-Origin-Resource-Policy`). OmniMove currently only sets `X-Frame-Options` (via `frameOptions(sameOrigin)`) and has no equivalent CSP declared in its security configuration. Bringing OmniMove up to the same standard as CassiTrack is a contained, low-risk piece of follow-up work.

**`'unsafe-inline'` in the Content-Security-Policy (A03 / A05).** Several dashboard pages still rely on inline `<script>` blocks and inline style attributes, which forced the CSP to allow `'unsafe-inline'` for both `script-src` and `style-src`. This does not reopen the specific stored-XSS bugs we fixed (those are now blocked by output escaping regardless of the CSP), but it does mean the CSP provides weaker defence-in-depth against any *other*, not-yet-discovered injection point than a stricter policy would. Removing it requires migrating the remaining inline scripts/styles into external files.

**Unpinned Docker images and missing Subresource Integrity (A08).** Infrastructure containers are referenced by a mutable tag rather than an immutable digest, and CDN-hosted assets (fonts, mapping library) are loaded without an `integrity=` attribute. Both are recognised hardening practices we have not yet applied; both are low-effort to add.

**Dependency vulnerability scanning could not be exercised live during penetration testing.** The OWASP Dependency-Check Maven plugin is correctly wired into both build pipelines and will fail a build on any high-severity CVE, but the auxiliary tools used to double-check this at network-scan time (`mvn`, `trivy`, `nmap`) were not available inside the sandboxed environment used for the live penetration test, so several checks were skipped rather than executed. The build-time control exists and is configured correctly; it simply was not re-verified through a second, independent tool during this specific testing session.

**A CSP discrepancy flagged by ZAP needs re-verification.** Our automated scan observed a CSP header missing the `form-action`, `object-src`, and `base-uri` directives that are present in the current `SecurityConfig.java`. This is most likely explained by the scan having run against a build taken at an earlier point in the review cycle, but we have not re-run the scan against the latest build to confirm this, and we are flagging it rather than assuming it is resolved.

**No live hardware in the loop.** The entire pipeline was demonstrated and security-tested using a Python GPS simulator standing in for the real ESP32-based bus trackers described in the project's "Phase 2" plans. The MQTT authentication, payload validation, and network-exposure fixes described in this report were all designed with real hardware in mind, but none of them have been validated against an actual physical device, radio link, or production network topology.

**Scope of the security review.** This review covered the CassiTrack and OmniMove Spring Boot backends and their first-party static frontends in depth. It did not include a formal review of the Python GPS simulator script itself, the Docker host's own operating-system hardening, or the security posture of the third-party services we depend on (Google Maps, Anthropic's API, the email provider) beyond confirming that we call them over TLS and never forward user-supplied destinations to them.
