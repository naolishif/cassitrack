# Security by Design and OWASP for IoT/Smart Mobility: The CASSITRACK and OMNIMOVE Case

**Course:** Computer and Network Security (CNS) — Master's Degree
**University:** University of Cassino and Southern Lazio (UNICAS)
**Project:** CassiTrack + OmniMove — Real-Time Smart Mobility Platform for Cassino
**Competition context:** CINI Smart City University Challenge, 10th Edition

---

## 1. Introduction

This report documents the design and the security engineering work carried out on CassiTrack and OmniMove, the two systems we built for the Distributed Programming and Networking course project. The first half explains, in plain terms, what the systems do and how they are put together, so the architecture can be understood from a deeper distributed-systems point of view. The second half is the core security deliverable: a walk-through of the OWASP Top 10 (2021) vulnerability categories, explaining what each category means and, concretely, what in our codebase prevents or mitigates it. The report closes with the results of the automated penetration testing we ran with OWASP ZAP, a conclusion, and an honest account of the limitations of our work.

**CassiTrack** is a real-time bus fleet monitoring system built for MAGNI Autoservizi, the bus company that operates in Cassino's city centre and surroundings. The motivation behind it is simple and personal: Buses currently have no live tracking that's reliable enough to be useful for the citizens. CassiTrack aims to solve this by receiving GPS positions from the buses, storing them, computing arrival predictions, and exposing that information through a web dashboard and a user-friendly app.

**OmniMove** is a multimodal journey-planning layer built as a CassiTrack consumer. OmniMove helps a traveller decide how to get from the starting stop decided by the user to the destination stop by comparing bus, bike, e-scooter, and walking options side by side, each with a price estimate, an expected duration, and a "Green Index" CO₂ score. OmniMove consumes CassiTrack's live data (bus positions and arrival times) and combines it with external services — Google Maps for traffic-aware travel times, weather data, and more — to produce a complete journey recommendation.

The two systems are deliberately separated: CassiTrack is the fleet-monitoring backend (it knows about buses, routes, and schedules), while OmniMove is the passenger-facing planning backend (it knows about journeys, pricing, and multimodal comparisons) and talks to CassiTrack as a client. This separation mirrors a common real-world pattern in smart-mobility platforms, where a transport operator's own tracking system is kept separate from the public-facing trip planner that aggregates several operators and transport modes.

Both systems also include role-based web dashboards: an administrator panel for managing user accounts, a fleet-manager dashboard for live analytics (for CassiTrack), and a traveller-facing planning interface (for OmniMove). A Python GPS simulator stands in for the real ESP32-based GPS trackers that would eventually be installed on the physical buses, so the whole pipeline can be demonstrated end-to-end without hardware.

---

## 2. System Architecture

At a high level, data flows through the platform in one direction — from the bus to the passenger — and is enriched at every stage:

1. **Bus / GPS source.** Each bus is expected to carry a small GPS tracker (an ESP32 microcontroller in the production design; in our demo, it's a Python script that simulates buses moving along real routes, publishing a new position every 15 seconds).
2. **MQTT broker.** The GPS devices publish their position to an Eclipse Mosquitto broker — a lightweight messaging system designed for exactly this kind of "many small devices sending frequent updates" scenario. Using MQTT instead of, say, having every bus call a REST endpoint directly, decouples the data producers (buses) from the data consumer (the backend) and copes gracefully with buses going briefly offline.
3. **CassiTrack backend (Spring Boot, port 8080).** It subscribes to the MQTT broker, validates every incoming position, and then stores the full history in InfluxDB (a database built for time-series data), caches the current position of every bus in Redis (so live-map queries are fast), and uses PostgreSQL with the PostGIS spatial extension to hold static reference data such as routes, stops, and schedules. On top of this data it runs an ETA service (arrival predictions), a schedule-adherence service (is the bus on time, late, or early), a GTFS-Realtime feed generator (so the data is consumable by national transport-data standards), and an AI assistant (backed by the Claude API) that lets a fleet manager ask natural-language questions about the fleet.
4. **OmniMove backend (Spring Boot, port 8081).** This is a separate service that queries CassiTrack's API for live bus data, and combines it with external providers (Google Maps for traffic-aware travel time and a weather API to inform the user about which travelling mode is best depending on the weather) to build and rank journey options for a traveller.
5. **Frontends.** A desktop live map (Leaflet.js-based) and the role-specific admin/fleet-manager/traveller HTML dashboards consume the two backends' REST APIs. All API endpoints are documented with OpenAPI 3.0.

Both backends share the same overall security model rather than reinventing it independently: stateless authentication via signed JSON Web Tokens (JWT), role-based authorization (ADMIN (both CassiTrack and Omnimove have Admin roles), FLEET_MANAGER (only for CassiTrack), TRAVELLER(only for OmniMove)), password hashing with BCrypt, and a set of supporting services — rate limiting, brute-force lockout, audit logging, token revocation — that are described in detail in the OWASP section below. Both backends are containerised with Docker Compose, which also runs their dependent infrastructure (PostgreSQL, InfluxDB, Redis, and, for CassiTrack, the Mosquitto broker).

---

## 3. Security Assessment Methodology

Our security work followed three complementary approaches:

- **Manual code review against the OWASP Top 10 (2021).** We went through both backends' controllers, security configuration, data-access code, and the static HTML/JS dashboards, checking each OWASP category against the actual implementation.
- **STRIDE-style threat modelling.** For each component (web frontends, REST APIs, the MQTT pipeline, the supporting databases), we evaluated which of the six STRIDE threats — Spoofing, Tampering, Repudiation, Information Disclosure, Denial of Service, Elevation of Privilege — applied, and verified whether a corresponding control existed.
- **Automated dynamic testing with OWASP ZAP**, run against the live, running CassiTrack and OmniMove backend, both as a general web-application scan and as an API-aware scan driven by our OpenAPI specification. The results are discussed in Section 5.

Findings were studied and several were fixed after the review cycle; a small number remain open and are listed transparently in the Limitations section.

---

## 4. OWASP Top 10 (2021): Vulnerability Evaluation and Prevention

This section goes through each of the ten OWASP categories. For each one we explain, in general terms, what the vulnerability class consists of, and then describe specifically what in CassiTrack/OmniMove prevents or mitigates it, referencing the actual file and mechanism responsible.

### A01 — Broken Access Control

Broken Access Control happens when an application fails to properly enforce who is allowed to do what. A classic example is an Insecure Direct Object Reference (IDOR): a user is logged in legitimately, but the application lets them access or modify a resource that does not belong to them simply by guessing or changing an identifier.

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

We also found and fixed a real broken-access-control bug during the code review: the `/api/v1/ai/**` endpoint was matched by two conflicting rules, and because Spring Security uses "first match wins", the FLEET_MANAGER rule silently absorbed all requests, meaning an authenticated ADMIN was being denied access to a feature they were supposed to have. The fix was to merge the two rules into a single one listing every authority that should be allowed.

Penetration testing also confirmed that direct object access is correctly blocked: requests to `/api/v1/users/{id}` or `/api/v1/vehicles/{id}` without a valid token, or with a token belonging to a lower-privileged role, are rejected with an HTTP 403, rather than returning the requested resource.

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

Injection vulnerabilities occur when untrusted input is interpreted as code or commands by a downstream system — the best-known example is SQL Injection, where attacker-controlled text ends up inside a database query.

We avoid classic SQL injection structurally: all database access goes through Spring Data JPA with Hibernate, which always uses parameterised queries, so there is no string concatenation of user input into SQL anywhere in the codebase.

The more interesting findings here were stored XSS issues in the two admin dashboards, which we identified and fixed during this review. Both `cassitrack-admin.html` and `omnimove-admin.html` originally rendered user-supplied fields (name, email, etc.) directly into the page using `innerHTML` template literals, with no escaping — allowing a crafted name field to execute arbitrary script in an administrator's browser. The fix was twofold: an `escHtml()` helper that escapes the five dangerous HTML characters is now applied to every user-controlled field before it is inserted into the page, and the registration request objects now validate the input itself:

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

### A05 — Security Misconfiguration

Security misconfiguration covers anything that is wrong not because of a bug in custom code, but because of how a component was set up — default credentials left in place, unnecessary services exposed to the network, debug information left switched on, or missing security headers.

Several concrete misconfigurations were found and corrected during the review:

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
        "default-src 'self'; script-src 'self'; ... frame-ancestors 'none'; " +
        "object-src 'none'; base-uri 'self'; form-action 'self';"
    ))
);
```

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

Dependency scanning with Trivy (`trivy fs`) on both backends revealed 6 CRITICAL and 40 HIGH CVEs in the original Spring Boot 3.2.5 baseline, including CVE-2025-24813 (Tomcat partial PUT RCE, CVSS 9.8) and CVE-2024-38821 (Spring Security authorization bypass, CVSS 9.1). Remediation consisted of upgrading the Spring Boot BOM to 3.5.5 and explicitly pinning `tomcat.version=10.1.55` and `spring-security.version=6.5.9`. Post-remediation scans confirmed 0 CRITICAL CVEs on both backends. Remaining HIGH findings (Netty request smuggling) are not exploitable without a reverse proxy in the request path. Frontend CDN resources are protected against supply chain substitution via Subresource Integrity hashes on all external `<script>` and `<link>` tags.

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

## 5. Penetration Testing Results (OWASP ZAP)

We ran OWASP ZAP against the live backends in two modes: a general web-application scan, and an API-aware scan driven by our OpenAPI specification.

The web-application scan reported zero high-risk alerts. Its four medium-risk findings were all Content-Security-Policy refinements. `img-src` allows any HTTPS source, which is deliberate since the live map loads tiles from an external provider. ZAP also flagged a small number of CDN-hosted assets for lacking a Subresource Integrity hash; this has since been fixed — the mapping and charting libraries now carry a verified `integrity=` attribute, and every infrastructure container image is pinned by SHA-256 digest rather than a mutable tag (Google Fonts CSS stays excluded by design, since its content varies per browser). Separately, ZAP reported that the live server's CSP header omitted the `form-action`, `object-src`, and `base-uri` directives present in our source — a discrepancy we flag transparently, since it suggests either a stale build at scan time or a rendering inconsistency worth re-verifying against a fresh deployment.

Four low-risk findings concerned newer cross-origin isolation headers (`Cross-Origin-Embedder-Policy`, `Cross-Origin-Opener-Policy`, and inconsistent `Cross-Origin-Resource-Policy`/`Permissions-Policy`) not being applied uniformly across every route — a reasonable next hardening step, not an active exploit path. The remaining informational findings were benign: some internal fix-tracking code comments are visible in client-delivered JavaScript, and ZAP correctly identified the app as a modern, non-cacheable JavaScript application.

The API-aware scan reported zero high- and medium-risk alerts. Its one low-risk finding was a false trigger (the root path returning an HTML login page instead of JSON, which is correct behaviour). More notably, seventeen unauthorized probes against protected routes — including the actuator health endpoint and a randomly generated numeric path — were all correctly rejected with HTTP 403, confirming the access-control layer holds up under automated hostile probing.

Taken together, the dynamic scan corroborates the manual review: no SQL injection, XSS, broken authentication, or missing access control was found. The remaining open items are CSP hardening refinements, a handful of optional cross-origin headers, and one source/build discrepancy to re-verify before the system is considered production-ready.

---

## 6. Conclusion

CassiTrack and OmniMove were designed and reviewed with a "secure by default, fix what's found" approach: stateless JWT authentication, role-based access control enforced centrally in the Spring Security filter chain, BCrypt password hashing, parameterised database queries, and dedicated services for rate limiting, brute-force lockout, token revocation, and security audit logging are present across both backends from the start, not bolted on afterwards. The review process described in this report — manual analysis against all ten OWASP Top 10 (2021) categories, STRIDE-based threat modelling, and dynamic scanning with OWASP ZAP — surfaced a meaningful number of real issues over the course of the project, almost all of which (anonymous MQTT access, infrastructure ports exposed to the network, an unauthenticated Redis instance, stored XSS in both admin dashboards, tokens readable via JavaScript, a dead access-control rule on the AI endpoint, and a missing Content-Security-Policy) were identified and fixed during the review cycle itself, with the fix and the reasoning behind it documented in the code. The automated ZAP scan, run independently against the live system, did not surface any high- or medium-risk finding that the manual review had missed, which gives us reasonable confidence that the security posture documented here reflects the system as it actually behaves, not just as it is intended to behave on paper.

At the same time, we do not consider the system "finished" from a security standpoint, and the next section lists, deliberately and specifically, what is still open. We see that transparency as part of the deliverable: a security report that claims zero remaining issues is far less credible — and far less useful to whoever inherits this codebase next — than one that draws a clear line between what has been verified, what has been fixed, and what is known to still need work.

---

## 7. Limitations

The following gaps were identified during this review and remain open. We list them explicitly, together with what would be required to close each one, rather than omitting them:

**Dependency vulnerability scanning could not be exercised live during penetration testing (A06).** The OWASP Dependency-Check Maven plugin is correctly wired into both build pipelines and will fail a build on any high-severity CVE, but the auxiliary tools used to double-check this at network-scan time (`mvn`, `trivy`, `nmap`) were not available inside the sandboxed environment used for the live penetration test, so several checks were skipped rather than executed. The build-time control exists and is configured correctly; it simply was not re-verified through a second, independent tool during this specific testing session.

**No live hardware in the loop.** The entire pipeline was demonstrated and security-tested using a Python GPS simulator standing in for the real ESP32-based bus trackers described in the project's "Phase 2" plans. The MQTT authentication, payload validation, and network-exposure fixes described in this report were all designed with real hardware in mind, but none of them have been validated against an actual physical device, radio link, or production network topology.

**Scope of the security review.** This review covered the CassiTrack and OmniMove Spring Boot backends and their first-party static frontends in depth. It did not include a formal review of the Python GPS simulator script itself, the Docker host's own operating-system hardening, or the security posture of the third-party services we depend on (Google Maps, Anthropic's API, the email provider) beyond confirming that we call them over TLS and never forward user-supplied destinations to them.
