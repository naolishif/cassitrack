const CASSITRACK = '/api/v1';

// Role redirection dictionary (Easy to scale later!)
const roleRoutes = {
    'FLEET_MANAGER': '/cassitrack-fleetmanager.html',
    'ADMIN': '/cassitrack-admin.html',
    'DRIVER': '/cassitrack-driver.html'
};

function showMsg(text, type) {
    const el = document.getElementById('msg');
    el.textContent = text;
    el.className = 'msg ' + type;
}

async function handleLogin(e) {
    e.preventDefault();
    const btn = document.getElementById('loginBtn');
    btn.disabled = true;
    btn.textContent = 'Signing in...';

    try {
        // 1. Authenticate with the backend
        const r = await fetch(`${CASSITRACK}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                email: document.getElementById('loginEmail').value,
                password: document.getElementById('loginPassword').value
            })
        });

        if (!r.ok) {
            const errorText = await r.text();
            showMsg(errorText || 'Username or password incorrect!!', 'err');
            return;
        }

        const data = await r.json();

        if (data.token) {
            // V-04 FIX (OWASP A02/A07): Token is stored in an httpOnly cookie by the server.
            // We do NOT store it in localStorage (XSS-accessible).
            // V-11 FIX (OWASP A03): Redirect via window.location — no document.write() XSS risk.

            const userRole = data.role ? data.role.toUpperCase() : 'USER';
            showMsg(`Success! Verified role: ${userRole}.`, 'ok');

            // Find the correct landing page for this role
            const targetHtmlUrl = roleRoutes[userRole];

            if (targetHtmlUrl) {
                // Wait 1 second so the user sees the success message, then navigate normally.
                // The httpOnly cookie is sent automatically with the GET request.
                setTimeout(() => {
                    window.location.replace(targetHtmlUrl);
                }, 1000);
            } else {
                showMsg('No landing page configured for your role.', 'err');
            }
        }
    } catch (e) {
        showMsg('Network error or server unreachable.', 'err');
    } finally {
        // Reset button if there was an error (if successful, the page will be replaced anyway)
        btn.disabled = false;
        btn.textContent = 'Sign In';
    }
}

// CSP FIX (A03/A05): bind the handler here instead of an inline onsubmit="" attribute,
// since CSP script-src governs inline event-handler attributes too.
document.getElementById('loginForm').addEventListener('submit', handleLogin);
