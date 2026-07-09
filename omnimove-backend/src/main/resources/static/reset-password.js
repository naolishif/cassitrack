const params = new URLSearchParams(window.location.search);
const TOKEN  = window.location.hash.slice(1) || '';

// Show error state immediately if the server said the token is invalid/expired
if (params.get('expired') === 'true' || !TOKEN) {
  document.getElementById('resetForm').style.display = 'none';
  document.getElementById('msgBox').innerHTML =
    "<div class='msg err'>This reset link has expired or is invalid. " +
    "<a href='/omnimove-login.html' style='color:#f87171'>Back to login</a></div>";
}

function valid(p) {
  return /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z0-9]).{8,}$/.test(p);
}
function showErr(id, msg) {
  const e = document.getElementById(id);
  e.textContent = msg;
  e.className = 'ferr show';
}
function clearErr(id) {
  const e = document.getElementById(id);
  e.textContent = '';
  e.className = 'ferr';
}

async function doReset(e) {
  e.preventDefault();
  const p1 = document.getElementById('p1').value;
  const p2 = document.getElementById('p2').value;
  clearErr('e1'); clearErr('e2');
  let ok = true;
  if (!p1) { showErr('e1', '⚠ Required'); ok = false; }
  else if (!valid(p1)) { showErr('e1', '⚠ Min 8 chars: uppercase, lowercase, number & special character'); ok = false; }
  if (!p2) { showErr('e2', '⚠ Required'); ok = false; }
  else if (p1 !== p2) { showErr('e2', '⚠ Passwords do not match'); ok = false; }
  if (!ok) return;

  const btn = document.getElementById('btn');
  btn.disabled = true;
  btn.textContent = 'Saving…';

  try {
    const r = await fetch('/omnimove/api/v1/auth/reset-password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token: TOKEN, newPassword: p1, confirmPassword: p2 })
    });
    const d = await r.json();
    const box = document.getElementById('msgBox');
    if (r.ok) {
      box.innerHTML = "<div class='msg ok'>&#x2713; Password updated! <a href='/omnimove-login.html' style='color:#22C55E'>Sign in</a></div>";
      document.getElementById('resetForm').style.display = 'none';
    } else {
      box.innerHTML = "<div class='msg err'>" + (d.message || 'Reset failed') + "</div>";
      btn.disabled = false;
      btn.textContent = 'Set New Password';
    }
  } catch (ex) {
    console.error(ex);
    document.getElementById('msgBox').innerHTML = "<div class='msg err'>Cannot reach server.</div>";
    btn.disabled = false;
    btn.textContent = 'Set New Password';
  }
}

document.getElementById('p2').addEventListener('input', function () {
  const p1 = document.getElementById('p1').value;
  if (this.value && p1 && this.value !== p1) showErr('e2', '⚠ Passwords do not match');
  else clearErr('e2');
});
