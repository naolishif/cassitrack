## 1. System Architecture

Our platform is made up of two separate backend services. CassiTrack is the fleet-monitoring system — it tracks buses in real time. OmniMove is the passenger-facing journey planner, built on top of CassiTrack as a client.

Data flows in one direction, from the bus to the passenger, and gets enriched at every stage. Each bus carries a GPS tracker — an ESP32 microcontroller in the production design, a Python simulator in our demo — that publishes its position every 15 seconds over MQTT, to an Eclipse Mosquitto broker. We use MQTT rather than a direct REST call because it decouples the devices from the backend and copes gracefully with buses going briefly offline.

CassiTrack subscribes to the broker, validates every incoming position, and stores it across three databases, each chosen for a different job: InfluxDB for the time-series GPS history, Redis for caching each bus's current position so live-map queries are fast, and PostgreSQL with the PostGIS extension for static reference data like routes and stops. On top of that data, CassiTrack runs an ETA service, a schedule-adherence service, a GTFS-Realtime feed generator, and an AI assistant backed by the Claude API.

OmniMove, a separate Spring Boot service on its own port, queries CassiTrack's API for live bus data and combines it with external providers — Google Maps for traffic-aware travel time, and a weather API — to rank journey options for a traveller across bus, bike, scooter, and walking.

Both backends are containerised with Docker Compose, and — importantly for the rest of this talk — they share the same security model: stateless JWT authentication, role-based authorization, password hashing with BCrypt, and a set of supporting services like rate limiting and brute-force lockout, which I'll get into shortly.

## 2. Security Methodology — Three Complementary Approaches

Going into the security part of the project, we evaluated the security state of our system following these three different approaches.

The first was manual code review against the OWASP Top 10, 2021 edition. We went through both backends' controllers, their security configuration, and their data-access code, checking each of the ten categories against what the code actually does.

The second was STRIDE-style threat modelling. For every component — the web frontends, the REST APIs, the MQTT pipeline, and the databases behind them — we asked which of the six STRIDE threats applied: spoofing, tampering, repudiation, information disclosure, denial of service, and elevation of privilege. For each one that applied, we checked whether a corresponding control actually existed in the code.

The third was automated dynamic testing, using OWASP ZAP against the live, running backends — both as a general web-application scan, and as an API-aware scan driven directly from our OpenAPI specification, so it could exercise every documented endpoint systematically rather than only what a generic crawler could discover.

Together, these three approaches cover the system from three different angles: manual review catches design and logic issues a scanner can't see; threat modelling forces us to reason systematically about every component, not just the obvious ones; and the automated scan validates the live, deployed system rather than just the source code on disk.

## 3. A04 — Insecure Design

Insecure Design is about whether security was built into the architecture itself, rather than added on afterwards — things like brute-force protection on login, or whether error messages accidentally tell an attacker which part of their guess was wrong.

Both of our backends lock an account after repeated failed logins, but they do it differently, because the two systems create accounts differently.

CassiTrack accounts are created only by an administrator — there's no public self-registration — so a Redis-backed counter blocks an email address for 15 minutes after 5 failed attempts, then resets automatically.

OmniMove, by contrast, is open to public self-registration, so its lockout is tracked per-user directly in the database, and deliberately does not auto-expire: the account stays locked until the user goes through the password-reset flow. That's a stronger response, appropriate for a public-facing system where an attacker could otherwise just wait out a timer.

OmniMove also rate-limits the endpoints that are most attractive to abuse: registration is capped at 5 attempts per IP per hour, and password-reset and resend-verification requests are capped at 3 per hour, all enforced through Redis counters.

Finally, we avoid username enumeration entirely. The forgot-password endpoint always returns the same message — "if that email is registered, you'll receive a reset link" — regardless of whether the account exists. A failed login always returns one generic message too, rather than separately revealing whether the email or the password was the wrong part. So an attacker probing the system learns nothing about which accounts actually exist.

## 4. A05 — Security Misconfiguration

Security Misconfiguration covers everything that's wrong not because of a bug in our own code, but because of how a component was set up — exposed services, missing headers, that kind of thing.

Our MQTT broker requires authentication: anonymous connections are disabled, and every client authenticates against a password file, with an access-control list governing exactly what each client is allowed to publish or subscribe to.

All the supporting infrastructure — PostgreSQL, InfluxDB, Redis, and the MQTT broker — is bound to localhost only on the host machine, rather than to all network interfaces. That means only the backend process running on the same machine can reach them directly; they are not reachable from anywhere else on the network.

OmniMove's Redis instance, which backs the JWT logout blacklist, requires a password as well, the same way the rest of the stack does, so that revocation list can't be tampered with by anyone who manages to reach that port.

The Swagger UI and OpenAPI documentation, which describe every API route in detail, require authentication to view in both backends, rather than being publicly browsable to anyone who finds the URL.

And CassiTrack sets a full Content-Security-Policy, together with a small set of supporting headers — Permissions-Policy, Cross-Origin-Resource-Policy, Strict-Transport-Security, and frame-deny — which limit what a browser is allowed to load or render on the page, and prevent the site from being embedded inside another page through an iframe.

Taken together, these are all about shrinking the attack surface down to exactly what needs to be reachable, and nothing more.

## 5. A06 — Vulnerable and Outdated Components

Vulnerable and Outdated Components covers the risk of a breach that doesn't come from a flaw in our own code at all, but from a known vulnerability in a third-party library that was never updated.

Both backends wire the OWASP Dependency-Check Maven plugin directly into the build process, configured to fail the build outright if any dependency is found to have a known vulnerability with a CVSS severity score of 7 or higher — in other words, anything rated high or critical.

That makes this a continuous gate rather than a one-off manual audit. Every time the project is built, the full dependency list is checked against the National Vulnerability Database, and a newly disclosed high-severity vulnerability — say, in Spring Boot or in the JWT library we use — would block the build until it's addressed, instead of silently shipping.

On the frontend, the external libraries we load from a CDN — the mapping and charting libraries — carry a Subresource Integrity hash, so the browser verifies the exact bytes it downloads match what we expect, and refuses to run the script if a CDN were ever compromised and started serving something different.

Every infrastructure container image is also pinned by its SHA-256 digest, rather than by a mutable tag, so we always know exactly which build of, say, Postgres or Redis we're actually running — a tag like "latest" can silently point to a different image tomorrow, a digest can't.

## 6. A07 — Identification and Authentication Failures

Identification and Authentication Failures covers weaknesses in how a system verifies who someone is, and keeps them logged in or logged out correctly.

Both backends issue stateless, signed JSON Web Tokens that expire after one hour, so even a captured token has a short shelf life.

Password complexity is enforced server-side on every registration and password-reset request, through a regular expression that requires a minimum length plus a mix of uppercase, lowercase, digit, and special characters — so it's not a check a user could bypass by tweaking the frontend.

Logging out isn't just a frontend action that deletes a cookie. The server records the still-valid token in a Redis-backed blacklist for whatever time remains on it, and the authentication filter checks that blacklist on every single request. So a token that's been logged out can't be replayed, even if an attacker had captured it beforehand.

OmniMove adds two more layers, since it has public self-registration: new accounts must verify their email before their first login, using a UUID-based token that expires after 24 hours and is delivered through a link rather than embedded anywhere it could leak; and password-reset tokens are single-use, UUID-based, and expire after one hour, so a reset link can't be reused later or guessed.
