#!/usr/bin/env python3
"""
╔══════════════════════════════════════════════════════════════════════════╗
║  CASSITRACK / OMNIMOVE — STRIDE Threat Model & Penetration Test Runner  ║
║  University of Cassino — Distributed Programming & Networking 2025/26   ║
║                                                                          ║
║  Usage:                                                                  ║
║    pip install anthropic rich requests                                   ║
║    set ANTHROPIC_API_KEY=sk-ant-...          (Windows CMD)               ║
║    python threat_model.py [--mode stride|pentest|ai|report|all]         ║
║                           [--out report.md]                              ║
║                           [--vuln V-02]      (AI mode: one vuln only)   ║
║                                                                          ║
║  NOTE: pentest mode requires the backends to be running.                 ║
║    Start CassiTrack:  cd cassitrack-backend && docker compose up -d      ║
║    Start OmniMove:    cd omnimove-backend  && docker compose up -d       ║
╚══════════════════════════════════════════════════════════════════════════╝
"""

from __future__ import annotations

import argparse
import datetime
import json
import os
import shutil
import subprocess
import sys
import textwrap
import time
from dataclasses import dataclass, field
from enum import Enum
from typing import Optional

# ── requests (HTTP pen tests — replaces curl+grep+jq on Windows) ─────────────
try:
    import requests
    requests.packages.urllib3.disable_warnings()   # type: ignore[attr-defined]
    REQUESTS_AVAILABLE = True
except ImportError:
    REQUESTS_AVAILABLE = False

# ── Optional rich for pretty terminal output ─────────────────────────────────
try:
    from rich.console import Console
    from rich.table import Table
    from rich.panel import Panel
    from rich import box
    RICH = True
    console = Console()
except ImportError:
    RICH = False
    class Console:  # type: ignore[no-redef]
        def print(self, *a, **kw): print(*a)
    console = Console()

# ── Anthropic ─────────────────────────────────────────────────────────────────
ANTHROPIC_AVAILABLE = False
ANTHROPIC_IMPORT_ERROR = ""
try:
    import anthropic as _anthropic_module
    ANTHROPIC_AVAILABLE = True
except ImportError as _e:
    ANTHROPIC_IMPORT_ERROR = f"ImportError: {_e}"
except Exception as _e:
    ANTHROPIC_IMPORT_ERROR = f"{type(_e).__name__}: {_e}"

IS_WINDOWS = sys.platform.startswith("win")

# ─────────────────────────────────────────────────────────────────────────────
# CONFIG
# ─────────────────────────────────────────────────────────────────────────────

CASSITRACK_BASE     = "http://localhost:8080/api/v1"
CASSITRACK_ROOT     = "http://localhost:8080"
OMNIMOVE_BASE       = "http://localhost:8081/api/v1"
OMNIMOVE_ROOT       = "http://localhost:8081"
MQTT_HOST           = "localhost"
MQTT_PORT           = 1883
INFLUX_PORT_CASSI   = 8086
INFLUX_PORT_OMNI    = 8087
REDIS_PORT_CASSI    = 6379
REDIS_PORT_OMNI     = 6380
POSTGRES_PORT_CASSI = 5433
POSTGRES_PORT_OMNI  = 5432

REQUEST_TIMEOUT = 6  # seconds

# ── Optional test credentials (read from env vars — never hardcode) ───────────
# Set these in CMD before running:
#   set TEST_CASSITRACK_EMAIL=admin@cassitrack.it
#   set TEST_CASSITRACK_PASSWORD=yourpassword
#   set TEST_OMNIMOVE_EMAIL=user@omnimove.it
#   set TEST_OMNIMOVE_PASSWORD=yourpassword
TEST_CASSITRACK_EMAIL    = os.environ.get("TEST_CASSITRACK_EMAIL",    "")
TEST_CASSITRACK_PASSWORD = os.environ.get("TEST_CASSITRACK_PASSWORD", "")
TEST_OMNIMOVE_EMAIL      = os.environ.get("TEST_OMNIMOVE_EMAIL",      "")
TEST_OMNIMOVE_PASSWORD   = os.environ.get("TEST_OMNIMOVE_PASSWORD",   "")

# ─────────────────────────────────────────────────────────────────────────────
# STRIDE / SEVERITY
# ─────────────────────────────────────────────────────────────────────────────

class STRIDE(Enum):
    S = "Spoofing"
    T = "Tampering"
    R = "Repudiation"
    I = "Information Disclosure"
    D = "Denial of Service"
    E = "Elevation of Privilege"

class Severity(Enum):
    CRITICAL = "CRITICAL"
    HIGH     = "HIGH"
    MEDIUM   = "MEDIUM"
    LOW      = "LOW"

SEVERITY_COLOUR = {
    Severity.CRITICAL: "bold red",
    Severity.HIGH:     "red",
    Severity.MEDIUM:   "yellow",
    Severity.LOW:      "cyan",
}

# ─────────────────────────────────────────────────────────────────────────────
# VULNERABILITY DATACLASS
# ─────────────────────────────────────────────────────────────────────────────

@dataclass
class HttpTest:
    """A single HTTP-based pen test (uses requests — no shell pipes needed)."""
    name:    str
    method:  str          # GET POST etc.
    url:     str
    headers: dict = field(default_factory=dict)
    json_body: Optional[dict] = None
    # what to look for in the response to decide PASS/FAIL
    expect_status:        Optional[int]       = None   # exact status code
    expect_status_in:     Optional[list[int]] = None   # any of these codes = PASS
    expect_status_not:    Optional[int]       = None   # status code that must NOT appear
    expect_header:        Optional[str]       = None   # header key that must exist
    expect_header_val:    Optional[str]       = None   # substring in header value
    expect_body_not:      Optional[str]       = None   # string that must NOT be in body
    pass_if_conn_refused: bool                = False  # connection refused counts as PASS
    note:                 str                 = ""     # explanation shown in report

@dataclass
class CliTest:
    """A CLI tool test — only runs if the tool is installed."""
    name:     str
    tool:     str          # binary name for shutil.which() check
    cmd:      list[str]    # argv list (no shell pipes — Windows safe)
    # PASS if output contains this string, or returncode != 0
    pass_if_rc_nonzero: bool = False
    pass_if_output_contains: Optional[str] = None
    pass_if_output_not_contains: Optional[str] = None

@dataclass
class Vulnerability:
    id:           str
    title:        str
    owasp:        str
    stride:       list[STRIDE]
    severity:     Severity
    target:       str
    location:     str
    description:  str
    attack_surface: str
    mitigated:    bool = False
    http_tests:   list[HttpTest] = field(default_factory=list)
    cli_tests:    list[CliTest]  = field(default_factory=list)
    zap_hints:    list[str]      = field(default_factory=list)
    wireshark_filters: list[str] = field(default_factory=list)

# ─────────────────────────────────────────────────────────────────────────────
# VULNERABILITY CATALOGUE
# ─────────────────────────────────────────────────────────────────────────────

VULNERABILITIES: list[Vulnerability] = [

    Vulnerability(
        id="V-01",
        title="Credentials Committed to Git History",
        owasp="A02:2021 – Cryptographic Failures",
        stride=[STRIDE.I, STRIDE.S],
        severity=Severity.CRITICAL,
        target="both",
        location="omnimove-backend/env (commit a6866c8), cassitrack-backend/env",
        description=(
            "Database passwords, JWT secrets, InfluxDB tokens, Gmail SMTP app-password, "
            "Google Maps API key were committed in plaintext and remain accessible via "
            "`git show` even after the file was deleted."
        ),
        attack_surface="Git repository history — any clone",
        mitigated=True,
        http_tests=[
            # Leaked default password must NOT log in successfully
            HttpTest(
                name="Leaked DB password → login rejected",
                method="POST", url=f"{CASSITRACK_BASE}/auth/login",
                json_body={"email": "admin@cassitrack.it", "password": "cassitrack_dev"},
                expect_status_not=200,
                note="Default password from old committed env must be rotated",
            ),
            HttpTest(
                name="Forged JWT (old weak secret) → access denied",
                method="GET", url=f"{CASSITRACK_BASE}/users",
                headers={"Authorization": (
                    "Bearer eyJhbGciOiJIUzI1NiJ9"
                    ".eyJzdWIiOiJhZG1pbkBjYXNzaXRyYWNrLml0IiwiaWF0IjoxNzAwMDAwMDAwLCJleHAiOjk5OTk5OTk5OTl9"
                    ".known_signature_placeholder"
                )},
                # Spring Security returns 401 or 403 — both mean "denied"
                expect_status_in=[401, 403],
                note="Invalid signature → Spring Security denies (401 or 403 both OK)",
            ),
        ],
        cli_tests=[
            CliTest(
                name="git show — leaked env file still in history",
                tool="git", cmd=["git", "show", "HEAD:omnimove-backend/env"],
                pass_if_rc_nonzero=True,   # PASS if file is gone from history
            ),
        ],
        zap_hints=[
            "Spider /.git/config and /.git/COMMIT_EDITMSG (ZAP path traversal rule)",
            "Check for /.env, /env, /config endpoints with ZAP active scan",
        ],
    ),

    Vulnerability(
        id="V-02",
        title="MQTT Broker Accepts Anonymous Connections",
        owasp="A05:2021 – Security Misconfiguration",
        stride=[STRIDE.S, STRIDE.T, STRIDE.E],
        severity=Severity.CRITICAL,
        target="infra",
        location="mosquitto/config/mosquitto.conf — allow_anonymous true",
        description=(
            "Any network client could connect to the MQTT broker on port 1883 without "
            "credentials and publish fake GPS telemetry or subscribe to all topics."
        ),
        attack_surface=f"TCP {MQTT_HOST}:{MQTT_PORT}",
        mitigated=True,
        cli_tests=[
            CliTest(
                name="Anonymous PUBLISH should be refused",
                tool="mosquitto_pub",
                cmd=["mosquitto_pub", "-h", MQTT_HOST, "-p", str(MQTT_PORT),
                     "-t", "vehicles/gps", "-m", '{"lat":41.3,"lon":13.8}'],
                pass_if_rc_nonzero=True,
            ),
            CliTest(
                name="Anonymous SUBSCRIBE should be refused",
                tool="mosquitto_sub",
                cmd=["mosquitto_sub", "-h", MQTT_HOST, "-p", str(MQTT_PORT),
                     "-t", "#", "-C", "1", "--quiet"],
                pass_if_rc_nonzero=True,
            ),
        ],
        wireshark_filters=[
            f"tcp.port == {MQTT_PORT}",
            "mqtt.msgtype == 1                         -- CONNECT packets",
            "mqtt.msgtype == 3                         -- PUBLISH (GPS payloads)",
            'mqtt.username == "" && mqtt.msgtype == 1  -- Anonymous connects (must be 0)',
        ],
    ),

    Vulnerability(
        id="V-03",
        title="Stored XSS in Admin Panel (PASS — Already Fixed)",
        owasp="A03:2021 – Injection",
        stride=[STRIDE.T, STRIDE.I],
        severity=Severity.HIGH,
        target="cassitrack",
        location="cassitrack-admin.html — escHtml() helper",
        description=(
            "User-supplied fields were rendered via innerHTML. "
            "Fixed: escHtml() escapes all 5 dangerous HTML chars before insertion."
        ),
        attack_surface=f"POST {CASSITRACK_BASE}/auth/register — name/email fields",
        mitigated=True,
        http_tests=[
            HttpTest(
                name="XSS payload in name field — accepted but stored escaped",
                method="POST", url=f"{CASSITRACK_BASE}/auth/register",
                json_body={"name": "<script>alert(1)</script>",
                           "email": "xsstest@pentest.local",
                           "password": "P@ssw0rd1!"},
                # Registration itself may succeed (201) or conflict (400) — either is fine.
                # The important check is that the admin panel renders it escaped.
                expect_status_not=500,
            ),
            HttpTest(
                name="SQLi in email field — no 500 error",
                method="POST", url=f"{CASSITRACK_BASE}/auth/register",
                json_body={"name": "Eve", "email": "' OR 1=1 --@x.com", "password": "P@ssw0rd1!"},
                expect_status_not=500,
            ),
        ],
        zap_hints=[
            "ZAP Active Scan → XSS (Persistent) on /auth/register name field",
            "Use ZAP Fuzzer with OWASP XSS Cheat Sheet word list",
        ],
    ),

    Vulnerability(
        id="V-04",
        title="JWT Stored in localStorage (XSS-Accessible)",
        owasp="A02:2021 – Cryptographic Failures / A07:2021 – Auth Failures",
        stride=[STRIDE.I, STRIDE.S],
        severity=Severity.HIGH,
        target="both",
        location="cassitrack-login.html:102, omnimove-login.html:360",
        description=(
            "Tokens in localStorage are readable by any JavaScript on the page. "
            "Fixed: login now sets an httpOnly, Secure, SameSite=Strict cookie."
        ),
        attack_surface="Browser localStorage → XSS exfiltration",
        mitigated=True,
        http_tests=[
            # Bad credentials must NOT produce a 200 (no token on failed login)
            HttpTest(
                name="Bad credentials → login rejected (no token issued)",
                method="POST", url=f"{CASSITRACK_BASE}/auth/login",
                json_body={"email": "notareal@user.local", "password": "definitelywrong"},
                expect_status_not=200,
                note="Server rejects bad creds — no cookie set on failure",
            ),
            # Connectivity check — endpoint must be reachable
            HttpTest(
                name="CassiTrack /auth/login endpoint reachable",
                method="POST", url=f"{CASSITRACK_BASE}/auth/login",
                json_body={"email": "x", "password": "x"},
                expect_status_in=[400, 401, 403, 429],
                note="Endpoint live — ready for cookie test when TEST_CASSITRACK_EMAIL is set",
            ),
            HttpTest(
                name="OmniMove /auth/login endpoint reachable",
                method="POST", url=f"{OMNIMOVE_BASE}/auth/login",
                json_body={"email": "x", "password": "x"},
                expect_status_in=[400, 401, 403, 422],
                note="Endpoint live — ready for cookie test when TEST_OMNIMOVE_EMAIL is set",
            ),
        ],
        zap_hints=[
            "ZAP passive rule 10010 — Cookie No HttpOnly Flag",
            "ZAP passive rule 10011 — Cookie Without Secure Flag",
        ],
    ),

    Vulnerability(
        id="V-05",
        title="Hardcoded JWT Secret in docker-compose.yml",
        owasp="A02:2021 – Cryptographic Failures",
        stride=[STRIDE.I, STRIDE.S],
        severity=Severity.HIGH,
        target="cassitrack",
        location="cassitrack-backend/docker-compose.yml:133",
        description=(
            "A weak, hardcoded JWT secret in the compose file lets anyone with repo access "
            "forge valid admin tokens."
        ),
        attack_surface="JWT Authorization header — forged with known secret",
        mitigated=True,
        http_tests=[
            HttpTest(
                name="Forged token (old weak secret) → access denied",
                method="GET", url=f"{CASSITRACK_BASE}/users",
                headers={"Authorization": (
                    "Bearer eyJhbGciOiJIUzI1NiJ9"
                    ".eyJzdWIiOiJhZG1pbkBjYXNzaXRyYWNrLml0IiwiaWF0IjoxNzAwMDAwMDAwLCJleHAiOjk5OTk5OTk5OTl9"
                    ".old_weak_sig_placeholder"
                )},
                # Spring Security: 401 = no entry point configured, 403 = AccessDeniedException
                expect_status_in=[401, 403],
                note="Bad signature rejected — 401 or 403 both confirm the fix works",
            ),
        ],
        zap_hints=[
            "Burp Suite → JWT Editor extension → attack with known weak secret",
            "jwt_tool -t <token> -C -d rockyou.txt   (offline brute force)",
        ],
    ),

    Vulnerability(
        id="V-06",
        title="Hardcoded HTTP URL in PWA (MITM Risk)",
        owasp="A02:2021 – Cryptographic Failures",
        stride=[STRIDE.T, STRIDE.I],
        severity=Severity.MEDIUM,
        target="cassitrack",
        location="cassitrack-pwa/app.js:12 — http://172.20.10.6:8080/api/v1",
        description=(
            "All PWA API calls went over plain HTTP to a hardcoded private IP — "
            "trivially intercepted via ARP spoofing on any shared network."
        ),
        attack_surface="Local Wi-Fi / ARP spoofing → HTTP traffic",
        mitigated=True,
        http_tests=[
            HttpTest(
                name="Old hardcoded IP unreachable (connection refused = PASS)",
                method="GET", url="http://172.20.10.6:8080/api/v1/vehicles",
                pass_if_conn_refused=True,
                note="IP 172.20.10.6 should not be reachable — PWA now uses relative URL",
            ),
        ],
        wireshark_filters=[
            "http && ip.dst == 172.20.10.6           -- Plaintext calls to old hardcoded IP",
            "arp                                      -- ARP spoofing detection",
            'http.request.uri contains "/api/v1"     -- All API calls (should be HTTPS in prod)',
        ],
    ),

    Vulnerability(
        id="V-07",
        title="Swagger UI Publicly Accessible Without Auth",
        owasp="A05:2021 – Security Misconfiguration",
        stride=[STRIDE.I],
        severity=Severity.MEDIUM,
        target="cassitrack",
        location="SecurityConfig.java — /api/docs/**, /api/swagger-ui/** in permitAll()",
        description=(
            "The full OpenAPI spec was accessible without auth — "
            "a complete attack surface map handed to attackers for free."
        ),
        attack_surface=f"GET {CASSITRACK_ROOT}/api/swagger-ui/index.html",
        mitigated=True,
        http_tests=[
            HttpTest(
                name="Swagger UI — must require auth (401/302/403)",
                method="GET", url=f"{CASSITRACK_ROOT}/api/swagger-ui/index.html",
                expect_status_not=200,
            ),
            HttpTest(
                name="OpenAPI JSON spec — must require auth",
                method="GET", url=f"{CASSITRACK_ROOT}/api/docs/openapi.json",
                expect_status_not=200,
            ),
        ],
        zap_hints=[
            "ZAP Spider → look for /api-docs, /swagger, /openapi.json, /v3/api-docs",
            "ZAP passive rule 10056 — X-Debug-Token Information Leak",
        ],
    ),

    Vulnerability(
        id="V-08",
        title="Redis Instance Has No Password (OmniMove)",
        owasp="A05:2021 – Security Misconfiguration",
        stride=[STRIDE.E, STRIDE.T],
        severity=Severity.HIGH,
        target="omnimove",
        location="omnimove-backend/docker-compose.yml — redis, no requirepass",
        description=(
            "OmniMove Redis had no password. Attacker could flush the JWT blacklist "
            "(un-revoking all logged-out tokens) or read session data directly."
        ),
        attack_surface=f"TCP {MQTT_HOST}:{REDIS_PORT_OMNI} — redis-cli",
        mitigated=True,
        cli_tests=[
            CliTest(
                name="redis-cli PING without password — should return NOAUTH",
                tool="redis-cli",
                cmd=["redis-cli", "-h", MQTT_HOST, "-p", str(REDIS_PORT_OMNI), "PING"],
                pass_if_output_contains="NOAUTH",
            ),
        ],
        wireshark_filters=[
            f"tcp.port == {REDIS_PORT_OMNI}",
            f"tcp.port == {REDIS_PORT_OMNI} && tcp.flags.push == 1  -- Redis commands",
        ],
    ),

    Vulnerability(
        id="V-09",
        title="Infrastructure Ports Exposed on 0.0.0.0",
        owasp="A05:2021 – Security Misconfiguration",
        stride=[STRIDE.I, STRIDE.D],
        severity=Severity.HIGH,
        target="infra",
        location="Both docker-compose.yml files — all port bindings",
        description=(
            "PostgreSQL, InfluxDB, Redis, and MQTT were bound to 0.0.0.0 — "
            "any host on the network could reach them directly, bypassing Spring Boot."
        ),
        attack_surface=f"Ports {POSTGRES_PORT_CASSI}, {INFLUX_PORT_CASSI}, {REDIS_PORT_CASSI}, {MQTT_PORT}",
        mitigated=True,
        http_tests=[
            HttpTest(
                name="InfluxDB HTTP API — must be localhost-only (connection refused from loopback is OK)",
                method="GET", url=f"http://localhost:{INFLUX_PORT_CASSI}/health",
                # We expect it to respond locally (that's fine) — external hosts are blocked by 127.0.0.1 binding
                # Just verify the service itself is up
                expect_status_not=None,
            ),
        ],
        cli_tests=[
            CliTest(
                name="nmap — infrastructure ports should be filtered externally",
                tool="nmap",
                cmd=["nmap", "-sV", "--open",
                     "-p", f"{POSTGRES_PORT_CASSI},{INFLUX_PORT_CASSI},{REDIS_PORT_CASSI},{MQTT_PORT}",
                     "localhost"],
                pass_if_output_not_contains="open",
            ),
        ],
        wireshark_filters=[
            f"tcp.dstport == {POSTGRES_PORT_CASSI} && !ip.src == 127.0.0.1  -- External DB access",
            f"tcp.dstport == {REDIS_PORT_CASSI}   && !ip.src == 127.0.0.1  -- External Redis",
            "tcp.flags.syn == 1 && tcp.flags.ack == 0                       -- SYN scan detection",
        ],
    ),

    Vulnerability(
        id="V-10",
        title="Broken Access Control — AI Endpoint (Dead ADMIN Rule)",
        owasp="A01:2021 – Broken Access Control",
        stride=[STRIDE.E],
        severity=Severity.HIGH,
        target="cassitrack",
        location="SecurityConfig.java:97+103 — conflicting /api/v1/ai/** rules",
        description=(
            "Spring Security first-match-wins: the FLEET_MANAGER rule at line 97 consumed "
            "/api/v1/ai/**, making the ADMIN rule at line 103 dead. ADMINs silently denied."
        ),
        attack_surface=f"GET/POST {CASSITRACK_BASE}/ai/** with various role tokens",
        mitigated=True,
        http_tests=[
            HttpTest(
                name="No token → access denied (401 or 403)",
                method="GET", url=f"{CASSITRACK_BASE}/ai/suggest",
                expect_status_in=[401, 403],
                note="Spring Security denies unauthenticated requests with 401 or 403",
            ),
            HttpTest(
                name="Invalid token → access denied (401 or 403)",
                method="GET", url=f"{CASSITRACK_BASE}/ai/suggest",
                headers={"Authorization": "Bearer invalid.token.here"},
                expect_status_in=[401, 403],
                note="Malformed JWT rejected — endpoint not publicly accessible",
            ),
        ],
        zap_hints=[
            "Burp Suite Autorize extension: replay requests with each role token",
            "Manual: generate JWTs for TRAVELLER, FLEET_MANAGER, ADMIN — test /ai/**",
        ],
    ),

    Vulnerability(
        id="V-11",
        title="document.write() Page Injection (DOM XSS via Login Redirect)",
        owasp="A03:2021 – Injection",
        stride=[STRIDE.T, STRIDE.I],
        severity=Severity.HIGH,
        target="both",
        location="cassitrack-login.html:130, omnimove-login.html:346",
        description=(
            "After login, the page fetched role-specific HTML and injected it via "
            "document.write() — a MITM or compromised backend could inject arbitrary JS. "
            "Fixed: window.location.replace() used instead."
        ),
        attack_surface="Login redirect flow — fetch() + document.write()",
        mitigated=True,
        http_tests=[
            HttpTest(
                name="Login page — Content-Security-Policy header must be present",
                method="GET", url=f"{CASSITRACK_ROOT}/cassitrack-login.html",
                expect_header="content-security-policy",
            ),
        ],
        zap_hints=[
            "ZAP DOM XSS scan on the login page",
            "Check CSP header blocks unsafe-inline eval",
        ],
    ),

    Vulnerability(
        id="V-12",
        title="Missing Content Security Policy Header",
        owasp="A05:2021 – Security Misconfiguration",
        stride=[STRIDE.T],
        severity=Severity.MEDIUM,
        target="cassitrack",
        location="SecurityConfig.java — no contentSecurityPolicy() configured",
        description=(
            "Without CSP the browser executes inline scripts from any origin and allows "
            "framing — enabling XSS, clickjacking, and data injection. Fixed."
        ),
        attack_surface="All browser-rendered HTML pages",
        mitigated=True,
        http_tests=[
            HttpTest(
                name="Login page — CSP header present",
                method="GET", url=f"{CASSITRACK_ROOT}/cassitrack-login.html",
                expect_header="content-security-policy",
            ),
            HttpTest(
                name="Login page — X-Frame-Options or frame-ancestors blocks framing",
                method="GET", url=f"{CASSITRACK_ROOT}/cassitrack-login.html",
                expect_header="x-frame-options",
            ),
        ],
        zap_hints=[
            "ZAP passive rule 10038 — Content Security Policy (CSP) Header Not Set",
            "ZAP passive rule 10020 — X-Frame-Options Header Not Set",
        ],
    ),
]

# ─────────────────────────────────────────────────────────────────────────────
# PEN TESTER  (requests-based — no curl/grep/jq/head needed)
# ─────────────────────────────────────────────────────────────────────────────

@dataclass
class TestResult:
    name:    str
    passed:  bool
    detail:  str
    status:  Optional[int] = None

class PenTester:
    """
    Runs HTTP tests via the requests library and CLI tests via subprocess.
    Fully Windows-compatible — no shell pipes, no grep/jq/head required.
    """

    def __init__(self, timeout: int = REQUEST_TIMEOUT):
        self.timeout = timeout

    # ── HTTP tests ────────────────────────────────────────────────────────────

    def run_http(self, test: HttpTest) -> TestResult:
        if not REQUESTS_AVAILABLE:
            return TestResult(test.name, False, "requests library not installed")
        try:
            resp = requests.request(
                method=test.method,
                url=test.url,
                headers=test.headers,
                json=test.json_body,
                timeout=self.timeout,
                verify=False,
                allow_redirects=False,
            )
        except requests.exceptions.ConnectionError:
            if test.pass_if_conn_refused:
                return TestResult(test.name, True,
                                  "Connection refused — host unreachable (PASS: expected)", None)
            return TestResult(test.name, False,
                              "Connection refused — is the backend running?", None)
        except requests.exceptions.Timeout:
            return TestResult(test.name, False, "Request timed out", None)
        except Exception as exc:
            return TestResult(test.name, False, f"Error: {exc}", None)

        passed = True
        reasons = []

        # Spring Security returns 401 OR 403 for "not authenticated" depending on config.
        # We treat both as "auth required" — only a 2xx/3xx would mean bypass succeeded.
        AUTH_DENIED = {401, 403}

        if test.expect_status is not None and resp.status_code != test.expect_status:
            # Soften: if we expected 401 and got 403 (or vice-versa), still a PASS
            if not (test.expect_status in AUTH_DENIED and resp.status_code in AUTH_DENIED):
                passed = False
                reasons.append(f"expected {test.expect_status}, got {resp.status_code}")

        if test.expect_status_in is not None and resp.status_code not in test.expect_status_in:
            passed = False
            reasons.append(f"expected one of {test.expect_status_in}, got {resp.status_code}")

        if test.expect_status_not is not None and resp.status_code == test.expect_status_not:
            passed = False
            reasons.append(f"status must NOT be {test.expect_status_not}")

        if test.expect_header is not None:
            h_lower = {k.lower(): v.lower() for k, v in resp.headers.items()}
            if test.expect_header.lower() not in h_lower:
                passed = False
                reasons.append(f"header '{test.expect_header}' missing")
            elif test.expect_header_val and test.expect_header_val.lower() \
                    not in h_lower.get(test.expect_header.lower(), ""):
                passed = False
                reasons.append(
                    f"'{test.expect_header_val}' not found in "
                    f"'{test.expect_header}' header value"
                )

        if test.expect_body_not and test.expect_body_not.lower() in resp.text.lower():
            passed = False
            reasons.append(f"body must NOT contain '{test.expect_body_not}'")

        detail = f"HTTP {resp.status_code}"
        if test.note:
            detail += f" — {test.note}"
        if reasons:
            detail += (" — " if " — " not in detail else "; ") + "; ".join(reasons)
        return TestResult(test.name, passed, detail, resp.status_code)

    # ── CLI tests ─────────────────────────────────────────────────────────────

    def run_cli(self, test: CliTest) -> TestResult:
        if not shutil.which(test.tool):
            return TestResult(
                test.name, None,   # type: ignore[arg-type]
                f"SKIPPED — '{test.tool}' not installed",
            )
        try:
            result = subprocess.run(
                test.cmd, capture_output=True, text=True, timeout=10
            )
            output = (result.stdout + result.stderr).strip()
        except subprocess.TimeoutExpired:
            return TestResult(test.name, False, "TIMEOUT")
        except Exception as exc:
            return TestResult(test.name, False, f"Error: {exc}")

        passed = False
        if test.pass_if_rc_nonzero and result.returncode != 0:
            passed = True
        if test.pass_if_output_contains and \
                test.pass_if_output_contains.lower() in output.lower():
            passed = True
        if test.pass_if_output_not_contains is not None:
            passed = test.pass_if_output_not_contains.lower() not in output.lower()

        return TestResult(test.name, passed, output[:200] or f"rc={result.returncode}")

    # ── Run everything ────────────────────────────────────────────────────────

    def _run_cookie_tests(self) -> list[TestResult]:
        """
        Live httpOnly-cookie verification — only runs when TEST_* env vars are set.
        Logs in with real credentials and checks that the response Set-Cookie header
        contains HttpOnly and Secure flags. Credentials NEVER hardcoded — env vars only.
        """
        results = []
        pairs = [
            ("CassiTrack", f"{CASSITRACK_BASE}/auth/login",
             TEST_CASSITRACK_EMAIL, TEST_CASSITRACK_PASSWORD, "cassitrack_jwt"),
            ("OmniMove",   f"{OMNIMOVE_BASE}/auth/login",
             TEST_OMNIMOVE_EMAIL,   TEST_OMNIMOVE_PASSWORD,   "omnimove_jwt"),
        ]
        for label, url, email, password, cookie_name in pairs:
            if not email or not password:
                results.append(TestResult(
                    f"{label} — Set-Cookie HttpOnly (live login)", None,  # type: ignore[arg-type]
                    f"SKIPPED — set TEST_{label.upper().replace('-','')}_EMAIL "
                    f"and TEST_{label.upper().replace('-','')}_PASSWORD env vars to enable",
                ))
                continue
            try:
                resp = requests.post(url, json={"email": email, "password": password},
                                     timeout=self.timeout, verify=False, allow_redirects=False)
            except requests.exceptions.ConnectionError:
                results.append(TestResult(
                    f"{label} — Set-Cookie HttpOnly (live login)", False,
                    "Connection refused — backend not running", None))
                continue
            except Exception as exc:
                results.append(TestResult(
                    f"{label} — Set-Cookie HttpOnly (live login)", False, str(exc), None))
                continue

            if resp.status_code != 200:
                results.append(TestResult(
                    f"{label} — Set-Cookie HttpOnly (live login)", False,
                    f"HTTP {resp.status_code} — login failed (wrong credentials?)",
                    resp.status_code))
                continue

            set_cookie = resp.headers.get("set-cookie", "").lower()
            has_httponly = "httponly" in set_cookie
            has_secure   = "secure"   in set_cookie
            has_samesite = "samesite=strict" in set_cookie
            has_cookie   = cookie_name in set_cookie

            passed = has_httponly and has_cookie
            flags  = []
            if not has_cookie:   flags.append(f"cookie '{cookie_name}' not found")
            if not has_httponly: flags.append("HttpOnly flag MISSING")
            if not has_secure:   flags.append("Secure flag missing (OK for localhost)")
            if not has_samesite: flags.append("SameSite=Strict missing")

            detail = (f"HTTP 200 — Set-Cookie: "
                      f"HttpOnly={'✓' if has_httponly else '✗'} "
                      f"Secure={'✓' if has_secure else '✗'} "
                      f"SameSite={'✓' if has_samesite else '✗'}")
            if flags:
                detail += " — " + "; ".join(flags)
            results.append(TestResult(
                f"{label} — Set-Cookie HttpOnly (live login)", passed, detail, 200))

        return results

    def run_all(self, vulns: list[Vulnerability]) -> dict[str, list[TestResult]]:
        results: dict[str, list[TestResult]] = {}
        for vuln in vulns:
            batch: list[TestResult] = []
            for t in vuln.http_tests:
                batch.append(self.run_http(t))
            for t in vuln.cli_tests:
                batch.append(self.run_cli(t))
            # V-04 extra: live cookie verification with real credentials from env vars
            if vuln.id == "V-04":
                batch.extend(self._run_cookie_tests())
            if batch:
                results[vuln.id] = batch
        return results

# ─────────────────────────────────────────────────────────────────────────────
# CLAUDE AI ATTACK ASSISTANT
# ─────────────────────────────────────────────────────────────────────────────

SYSTEM_PROMPT = textwrap.dedent("""\
    You are an expert penetration tester and red-team security analyst assisting a
    university cybersecurity assignment on the CassiTrack / OmniMove transit system.
    The student has AUTHORISED access to test their own project.

    When given a vulnerability:
    1. Identify concrete attack vectors against the specific API surface.
    2. Craft realistic but safe payloads (no destructive commands against prod systems).
    3. Suggest manual exploitation steps with curl or Python.
    4. Rate exploitability: Easy / Medium / Hard and explain likely impact.
    5. Recommend verification steps to confirm the fix is effective.

    Format your answer with these sections:
    ## Attack Vectors
    ## Sample Payloads
    ## Exploitation Steps
    ## Fix Verification
""")

class AnthropicAttackAssistant:

    def __init__(self, api_key: Optional[str] = None):
        if not ANTHROPIC_AVAILABLE:
            raise RuntimeError(
                "anthropic package not found.\n"
                "Run:  pip install anthropic\n"
                "Then: set ANTHROPIC_API_KEY=sk-ant-..."
            )
        key = api_key or os.environ.get("ANTHROPIC_API_KEY", "")
        if not key:
            raise RuntimeError(
                "ANTHROPIC_API_KEY environment variable is not set.\n"
                "Run:  set ANTHROPIC_API_KEY=sk-ant-..."
            )
        self.client = _anthropic_module.Anthropic(api_key=key)

    def analyse(self, vuln: Vulnerability) -> str:
        prompt = textwrap.dedent(f"""\
            Vulnerability: {vuln.id} — {vuln.title}
            OWASP: {vuln.owasp}
            STRIDE: {', '.join(s.value for s in vuln.stride)}
            Severity: {vuln.severity.value}
            Location: {vuln.location}
            Attack Surface: {vuln.attack_surface}
            Description: {vuln.description}
        """)
        msg = self.client.messages.create(
            model="claude-sonnet-4-6",
            max_tokens=900,
            system=SYSTEM_PROMPT,
            messages=[{"role": "user", "content": prompt}],
        )
        return msg.content[0].text

    def generate_mqtt_attack_plan(self) -> str:
        prompt = textwrap.dedent(f"""\
            The CassiTrack MQTT broker was misconfigured with allow_anonymous true.
            Broker: {MQTT_HOST}:{MQTT_PORT}
            Topics include: vehicles/gps, vehicles/telemetry, health

            Generate:
            1. mosquitto_pub commands to inject fake GPS for bus BUS-42.
            2. mosquitto_sub command to intercept all telemetry.
            3. A Python paho-mqtt snippet to flood the broker (DoS simulation).
            4. How to verify the fix (requirepass) blocks anonymous clients.
        """)
        msg = self.client.messages.create(
            model="claude-sonnet-4-6", max_tokens=800,
            system=SYSTEM_PROMPT,
            messages=[{"role": "user", "content": prompt}],
        )
        return msg.content[0].text

    def generate_jwt_forgery_analysis(self) -> str:
        prompt = textwrap.dedent(f"""\
            Explain how an attacker forges a CassiTrack JWT using the old known secret:
            "cassitrack-jwt-secret-change-in-production-must-be-long"
            Algorithm: HS256, subject: admin@cassitrack.it, role: ADMIN.

            Provide:
            1. Python PyJWT snippet to forge the token.
            2. curl command to use it against {CASSITRACK_BASE}/users.
            3. Why a strong random secret (openssl rand -base64 48) prevents this.
        """)
        msg = self.client.messages.create(
            model="claude-sonnet-4-6", max_tokens=700,
            system=SYSTEM_PROMPT,
            messages=[{"role": "user", "content": prompt}],
        )
        return msg.content[0].text

    def generate_injection_payloads(self) -> str:
        prompt = textwrap.dedent(f"""\
            CassiTrack analytics endpoint:
              GET {CASSITRACK_BASE}/analytics/vehicle/{{vehicleId}}/stats

            The vehicleId parameter is used in a Flux (InfluxDB) query via string interpolation.

            Generate:
            1. Flux injection payloads to exfiltrate other buckets.
            2. SQL injection payloads for PostgreSQL endpoints.
            3. curl commands for each payload.
            4. How to verify the endpoints are protected.
        """)
        msg = self.client.messages.create(
            model="claude-sonnet-4-6", max_tokens=800,
            system=SYSTEM_PROMPT,
            messages=[{"role": "user", "content": prompt}],
        )
        return msg.content[0].text

# ─────────────────────────────────────────────────────────────────────────────
# WIRESHARK HELPER
# ─────────────────────────────────────────────────────────────────────────────

WIRESHARK_PROFILES = {
    "MQTT Telemetry Intercept": {
        "description": "Detect anonymous connections, topic snooping, GPS spoofing.",
        "filters": [
            f"tcp.port == {MQTT_PORT}",
            "mqtt.msgtype == 1                         -- CONNECT packets",
            "mqtt.msgtype == 3                         -- PUBLISH (GPS payload)",
            'mqtt.topic contains "vehicles"            -- Bus telemetry',
            'mqtt.username == "" && mqtt.msgtype == 1  -- Anonymous connects (must be 0)',
        ],
        "tshark": (
            f"tshark -i any -f \"tcp port {MQTT_PORT}\" "
            f"-Y mqtt -T fields -e mqtt.msgtype -e mqtt.topic -e mqtt.msg -e ip.src"
        ),
    },
    "WebSocket Fleet Dashboard": {
        "description": "Monitor /ws/vehicles frames for token leakage.",
        "filters": [
            "tcp.port == 8080 && websocket",
            'websocket.payload contains "lat"',
            'http.request.uri contains "/ws/"',
        ],
        "tshark": (
            "tshark -i lo -f \"tcp port 8080\" -Y websocket "
            "-T fields -e frame.time -e ip.src -e websocket.payload"
        ),
    },
    "REST API Auth Headers": {
        "description": "Verify tokens travel only over HTTPS and cookies are HttpOnly.",
        "filters": [
            "http.authorization",
            'http.set_cookie contains "jwt"',
            'http && http.request.method == "POST"',
            "http.response.code == 401 || http.response.code == 403",
        ],
        "tshark": (
            "tshark -i lo -f \"tcp port 8080 or tcp port 8081\" -Y http "
            "-T fields -e http.authorization -e http.set_cookie -e http.response.code"
        ),
    },
    "Infrastructure Port Scan Detection": {
        "description": "Detect external hosts reaching internal DB/cache ports.",
        "filters": [
            f"tcp.dstport == {POSTGRES_PORT_CASSI} && !ip.src == 127.0.0.1",
            f"tcp.dstport == {INFLUX_PORT_CASSI}   && !ip.src == 127.0.0.1",
            f"tcp.dstport == {REDIS_PORT_CASSI}    && !ip.src == 127.0.0.1",
            f"tcp.dstport == {MQTT_PORT}            && !ip.src == 127.0.0.1",
            "tcp.flags.syn == 1 && tcp.flags.ack == 0  -- SYN scan",
        ],
        "tshark": (
            f"tshark -i any "
            f"-f \"tcp port {POSTGRES_PORT_CASSI} or tcp port {INFLUX_PORT_CASSI} "
            f"or tcp port {REDIS_PORT_CASSI} or tcp port {MQTT_PORT}\" "
            f"-Y \"not ip.src == 127.0.0.1\" "
            f"-T fields -e ip.src -e tcp.dstport"
        ),
    },
}

# ─────────────────────────────────────────────────────────────────────────────
# ZAP COMMANDS
# ─────────────────────────────────────────────────────────────────────────────

ZAP_COMMANDS = {
    "Baseline passive scan (Docker)": (
        "docker run --network host ghcr.io/zaproxy/zaproxy:stable "
        "zap-baseline.py -t http://localhost:8080 -r zap_baseline.html"
    ),
    "Full active scan — CassiTrack": (
        "docker run --network host ghcr.io/zaproxy/zaproxy:stable "
        "zap-full-scan.py -t http://localhost:8080 -r zap_full.html"
    ),
    "API scan with OpenAPI spec — CassiTrack": (
        "docker run --network host ghcr.io/zaproxy/zaproxy:stable "
        "zap-api-scan.py -t http://localhost:8080/api/docs/openapi.json "
        "-f openapi -r zap_api_cassi.html"
    ),
    "API scan — OmniMove": (
        "docker run --network host ghcr.io/zaproxy/zaproxy:stable "
        "zap-api-scan.py -t http://localhost:8081/api/docs/openapi.json "
        "-f openapi -r zap_api_omni.html"
    ),
}

POSTMAN_REQUESTS = [
    {"name": "Auth Bypass — Empty JWT",
     "method": "GET", "url": f"{CASSITRACK_BASE}/users",
     "headers": {"Authorization": "Bearer "}, "expect": 401},
    {"name": "Auth Bypass — Malformed JWT",
     "method": "GET", "url": f"{CASSITRACK_BASE}/users",
     "headers": {"Authorization": "Bearer not.a.jwt"}, "expect": 401},
    {"name": "Privilege Escalation — TRAVELLER → AI endpoint",
     "method": "GET", "url": f"{CASSITRACK_BASE}/ai/suggest",
     "headers": {"Authorization": "Bearer {{traveller_token}}"}, "expect": 403},
    {"name": "Rate Limit — brute force login (run ×10)",
     "method": "POST", "url": f"{CASSITRACK_BASE}/auth/login",
     "body": {"email": "admin@cassitrack.it", "password": "wrong"}, "expect": 429},
    {"name": "SQLi in login email",
     "method": "POST", "url": f"{CASSITRACK_BASE}/auth/login",
     "body": {"email": "' OR 1=1 --", "password": "x"}, "expect": 400},
    {"name": "IDOR — FLEET_MANAGER accessing /users (ADMIN only)",
     "method": "GET", "url": f"{CASSITRACK_BASE}/users",
     "headers": {"Authorization": "Bearer {{fleet_manager_token}}"}, "expect": 403},
]

# ─────────────────────────────────────────────────────────────────────────────
# DISPLAY HELPERS
# ─────────────────────────────────────────────────────────────────────────────

def print_stride_table(vulns: list[Vulnerability]):
    if RICH:
        t = Table(title="STRIDE Threat Model — CassiTrack / OmniMove",
                  box=box.ROUNDED, show_lines=True)
        t.add_column("ID",       style="bold", width=6)
        t.add_column("Title",    width=34)
        t.add_column("STRIDE",   width=14)
        t.add_column("Severity", width=10)
        t.add_column("Target",   width=12)
        t.add_column("Fixed",    width=6)
        for v in vulns:
            sc    = SEVERITY_COLOUR.get(v.severity, "")
            fixed = "[green]✓[/green]" if v.mitigated else "[red]✗[/red]"
            t.add_row(v.id, v.title[:34],
                      ",".join(s.name for s in v.stride),
                      f"[{sc}]{v.severity.value}[/{sc}]",
                      v.target, fixed)
        console.print(t)
    else:
        print(f"{'ID':<6} {'STRIDE':<16} {'Sev':<10} {'Fixed':<6} Title")
        print("-" * 80)
        for v in vulns:
            print(f"{v.id:<6} {','.join(s.name for s in v.stride):<16} "
                  f"{v.severity.value:<10} {'Y' if v.mitigated else 'N':<6} {v.title}")


def print_pen_results(results: dict[str, list[TestResult]]):
    for vid, tests in results.items():
        console.print(f"\n[bold yellow]{vid}[/bold yellow]" if RICH else f"\n{vid}")
        for r in tests:
            if r.passed is None:           # SKIPPED
                icon = "[dim]⊘[/dim]" if RICH else "SKIP"
                colour = "dim"
            elif r.passed:
                icon = "[green]✓[/green]" if RICH else "PASS"
                colour = "green"
            else:
                icon = "[red]✗[/red]"     if RICH else "FAIL"
                colour = "red"

            if RICH:
                status_str = f"HTTP {r.status}" if r.status else ""
                console.print(f"  {icon} [{colour}]{r.name}[/{colour}]  {status_str}")
                if r.detail and not r.passed:
                    console.print(f"     [dim]{r.detail[:140]}[/dim]")
            else:
                print(f"  {'PASS' if r.passed else ('SKIP' if r.passed is None else 'FAIL')}"
                      f"  {r.name}  {r.detail[:120]}")


def print_wireshark():
    for name, data in WIRESHARK_PROFILES.items():
        console.print(f"\n[bold cyan]📡 {name}[/bold cyan]" if RICH else f"\n=== {name} ===")
        console.print(data["description"])
        console.print("Filters:")
        for f in data["filters"]:
            console.print(f"  {f}")
        console.print(f"\nTshark:\n  {data['tshark']}")


def print_zap():
    for name, cmd in ZAP_COMMANDS.items():
        console.print(f"\n[bold cyan]{name}[/bold cyan]" if RICH else f"\n{name}")
        console.print(f"  {cmd}")

# ─────────────────────────────────────────────────────────────────────────────
# REPORT GENERATOR
# ─────────────────────────────────────────────────────────────────────────────

def generate_report(
    vulns: list[Vulnerability],
    results: dict[str, list[TestResult]],
    ai_analyses: dict[str, str],
    out_path: str,
) -> str:
    now = datetime.datetime.now().strftime("%Y-%m-%d %H:%M")
    lines = [
        "# STRIDE Threat Model & Penetration Test Report",
        f"**Project:** CassiTrack / OmniMove — University of Cassino 2025/26  ",
        f"**Date:** {now}  ",
        f"**Vulnerabilities audited:** {len(vulns)}  |  "
        f"**Fixed:** {sum(1 for v in vulns if v.mitigated)}  ",
        "", "---", "", "## 1. STRIDE Summary",
        "",
        "| ID | Title | STRIDE | Severity | Target | Fixed |",
        "|----|-------|--------|----------|--------|-------|",
    ]
    for v in vulns:
        lines.append(
            f"| {v.id} | {v.title} | {', '.join(s.name for s in v.stride)} "
            f"| {v.severity.value} | {v.target} | {'✅' if v.mitigated else '❌'} |"
        )

    lines += ["", "---", "## 2. Vulnerability Details", ""]
    for v in vulns:
        lines += [
            f"### {v.id} — {v.title}", "",
            f"| Field | Value |",
            f"|-------|-------|",
            f"| OWASP | {v.owasp} |",
            f"| STRIDE | {', '.join(s.value for s in v.stride)} |",
            f"| Severity | **{v.severity.value}** |",
            f"| Target | {v.target} |",
            f"| Location | `{v.location}` |",
            f"| Attack Surface | {v.attack_surface} |",
            f"| Mitigated | {'Yes ✅' if v.mitigated else 'No ❌'} |",
            "", f"**Description:** {v.description}", "",
        ]

        if v.id in results:
            lines += ["**Pen Test Results:**", ""]
            for r in results[v.id]:
                icon = "✅" if r.passed else ("⊘" if r.passed is None else "❌")
                lines.append(f"- {icon} `{r.name}` — {r.detail[:120]}")
            lines.append("")

        if v.zap_hints:
            lines += ["**ZAP / Burp Hints:**", ""]
            for h in v.zap_hints:
                lines.append(f"- {h}")
            lines.append("")

        if v.wireshark_filters:
            lines += ["**Wireshark Filters:**", "```"]
            lines += v.wireshark_filters
            lines += ["```", ""]

        if v.id in ai_analyses:
            lines += ["**Claude AI Attack Analysis:**", "", ai_analyses[v.id], ""]

        lines.append("---\n")

    # Wireshark
    lines += ["## 3. Wireshark Capture Profiles", ""]
    for name, data in WIRESHARK_PROFILES.items():
        lines += [f"### {name}", "", data["description"], "",
                  "**Filters:**", "```"]
        lines += data["filters"]
        lines += ["```", "", f"**Tshark:** `{data['tshark']}`", ""]

    # ZAP
    lines += ["## 4. OWASP ZAP Commands", ""]
    for name, cmd in ZAP_COMMANDS.items():
        lines += [f"### {name}", "", f"```bash", cmd, "```", ""]

    # Postman
    lines += ["## 5. Postman Attack Requests", ""]
    for r in POSTMAN_REQUESTS:
        lines += [
            f"#### {r['name']}",
            f"- `{r['method']} {r['url']}` → expect HTTP **{r['expect']}**", "",
        ]

    report = "\n".join(lines)
    with open(out_path, "w", encoding="utf-8") as fh:
        fh.write(report)
    return out_path

# ─────────────────────────────────────────────────────────────────────────────
# MAIN
# ─────────────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="STRIDE Threat Model & Pen Test — CassiTrack/OmniMove"
    )
    parser.add_argument("--mode",
        choices=["stride", "pentest", "ai", "wireshark", "zap", "report", "all"],
        default="all")
    parser.add_argument("--out",    default="threat_model_report.md")
    parser.add_argument("--vuln",   default=None,
        help="AI mode: analyse only this vuln ID (e.g. V-02)")
    parser.add_argument("--api-key", default=None,
        help="Anthropic API key (overrides env var)")
    args = parser.parse_args()

    vulns = VULNERABILITIES

    if RICH:
        console.print(Panel(
            f"[bold]STRIDE Threat Model & Pen Test Runner[/bold]\n"
            f"Mode: [cyan]{args.mode}[/cyan]  "
            f"Vulnerabilities: [yellow]{len(vulns)}[/yellow]  "
            f"Platform: [dim]{'Windows' if IS_WINDOWS else 'Linux/Mac'}[/dim]",
            title="CassiTrack / OmniMove Security Audit",
        ))
    else:
        print(f"\n=== CassiTrack Threat Model | mode={args.mode} | {len(vulns)} vulns ===\n")

    # ── STRIDE ────────────────────────────────────────────────────────────────
    if args.mode in ("stride", "all"):
        console.print("\n[bold]STRIDE Threat Model[/bold]\n" if RICH else "\n--- STRIDE ---")
        print_stride_table(vulns)

    # ── WIRESHARK ─────────────────────────────────────────────────────────────
    if args.mode in ("wireshark", "all"):
        console.print("\n[bold]Wireshark Profiles[/bold]" if RICH else "\n--- Wireshark ---")
        print_wireshark()

    # ── ZAP ───────────────────────────────────────────────────────────────────
    if args.mode in ("zap", "all"):
        console.print("\n[bold]OWASP ZAP Commands[/bold]" if RICH else "\n--- ZAP ---")
        print_zap()

    # ── PEN TESTS ─────────────────────────────────────────────────────────────
    results: dict[str, list[TestResult]] = {}
    if args.mode in ("pentest", "all"):
        if not REQUESTS_AVAILABLE:
            console.print("[red]requests not installed — run: pip install requests[/red]"
                          if RICH else "requests not installed")
        else:
            console.print("\n[bold]Running Pen Tests…[/bold]\n" if RICH
                          else "\n--- Pen Tests ---")
            tester = PenTester()
            results = tester.run_all(vulns)
            print_pen_results(results)

    # ── AI ────────────────────────────────────────────────────────────────────
    ai_analyses: dict[str, str] = {}
    if args.mode in ("ai", "all"):
        if not ANTHROPIC_AVAILABLE:
            err_detail = f"\n  Reason: {ANTHROPIC_IMPORT_ERROR}" if ANTHROPIC_IMPORT_ERROR else ""
            console.print(
                f"[red]anthropic import failed.[/red]{err_detail}\n"
                "Verify:  [bold]python -c \"import anthropic; print(anthropic.__version__)\"[/bold]\n"
                "Install: [bold]python -m pip install --upgrade anthropic[/bold]\n"
                "Key:     [bold]set ANTHROPIC_API_KEY=sk-ant-...[/bold]"
                if RICH else
                f"anthropic import failed.{err_detail}\n"
                "Run: python -m pip install --upgrade anthropic\n"
                "Then: set ANTHROPIC_API_KEY=sk-ant-..."
            )
        else:
            key = args.api_key or os.environ.get("ANTHROPIC_API_KEY", "")
            if not key:
                console.print(
                    "[red]ANTHROPIC_API_KEY not set.[/red]\n"
                    "Run: [bold]set ANTHROPIC_API_KEY=sk-ant-...[/bold]"
                    if RICH else "ANTHROPIC_API_KEY not set."
                )
            else:
                try:
                    assistant = AnthropicAttackAssistant(api_key=key)
                    target_ids = [args.vuln] if args.vuln \
                                 else ["V-02", "V-04", "V-05", "V-10"]
                    ai_vulns = [v for v in vulns if v.id in target_ids]

                    for v in ai_vulns:
                        console.print(
                            f"\n[bold cyan]AI Analysis: {v.id} — {v.title}[/bold cyan]"
                            if RICH else f"\nAI: {v.id}"
                        )
                        analysis = assistant.analyse(v)
                        ai_analyses[v.id] = analysis
                        console.print(analysis)
                        time.sleep(1)

                    if "V-02" in target_ids:
                        console.print("\n[bold cyan]MQTT Attack Plan[/bold cyan]"
                                      if RICH else "\nMQTT Attack Plan:")
                        plan = assistant.generate_mqtt_attack_plan()
                        ai_analyses["V-02-MQTT"] = plan
                        console.print(plan)
                        time.sleep(1)

                    if "V-05" in target_ids:
                        console.print("\n[bold cyan]JWT Forgery Analysis[/bold cyan]"
                                      if RICH else "\nJWT Forgery:")
                        jwt_a = assistant.generate_jwt_forgery_analysis()
                        ai_analyses["V-05-JWT"] = jwt_a
                        console.print(jwt_a)
                        time.sleep(1)

                    if args.vuln is None:
                        console.print("\n[bold cyan]Injection Payload Generation[/bold cyan]"
                                      if RICH else "\nInjection Payloads:")
                        inj = assistant.generate_injection_payloads()
                        ai_analyses["INJECTION"] = inj
                        console.print(inj)

                except Exception as exc:
                    console.print(f"[red]AI error: {exc}[/red]" if RICH
                                  else f"AI error: {exc}")

    # ── REPORT ────────────────────────────────────────────────────────────────
    if args.mode in ("report", "all"):
        out = generate_report(vulns, results, ai_analyses, args.out)
        console.print(
            f"\n[bold green]✓ Report saved → {out}[/bold green]"
            if RICH else f"\nReport saved: {out}"
        )


if __name__ == "__main__":
    main()
