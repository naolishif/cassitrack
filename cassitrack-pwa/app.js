/**
 * CASSITRACK PWA — Application Logic
 * University of Cassino — 2025/2026
 *
 * This file powers the entire mobile app.
 * It is plain JavaScript — no framework needed.
 * Works in any modern mobile browser and
 * can be installed on the home screen.
 */

// ── Configuration ────────────────────────────────────────────
// V-06 FIX (OWASP A02): Use a relative URL so the PWA works on any host over HTTPS.
// When served from the CassiTrack backend directly, /api/v1 is the correct relative path.
// If served from a different origin, override CASSITRACK_API_ORIGIN via a build-time env var
// or deploy the PWA as a static asset of the Spring Boot app.
const CASSITRACK_API = (window.CASSITRACK_API_ORIGIN || '') + '/api/v1';
const REFRESH   = 15000; // milliseconds

// Status colours matching the schedule service
const STATUS_COLOUR = {
  ON_TIME:            '#22C55E',
  SLIGHTLY_LATE:      '#F59E0B',
  SIGNIFICANTLY_LATE: '#EF4444',
  EARLY:              '#06B6D4',
  UNKNOWN:            '#4B5563',
};

const STATUS_LABEL = {
  ON_TIME:            'ON TIME',
  SLIGHTLY_LATE:      'SLIGHTLY LATE',
  SIGNIFICANTLY_LATE: 'LATE',
  EARLY:              'EARLY',
  UNKNOWN:            'LIVE',
};

const MODE_ICON = {
  BUS: '🚌', WALK: '🚶', BIKE: '🚲',
  SCOOTER: '🛴', CAR: '🚗', WAIT: '⏳',
};

const MODE_COLOUR = {
  BUS: '#3B82F6', WALK: '#22C55E', BIKE: '#22C55E',
  SCOOTER: '#F59E0B', CAR: '#EF4444', WAIT: '#4B5563',
};

// Known stops
const STOPS = [
  { id: 'CASSINO-STAZIONE',  name: 'Cassino Stazione FS',     lat: 41.4892, lon: 13.8282 },
  { id: 'CASSINO-CENTRO',    name: 'Cassino Centro',           lat: 41.4917, lon: 13.8314 },
  { id: 'CASSINO-OSPEDALE',  name: 'Ospedale S. Scolastica',   lat: 41.4955, lon: 13.8330 },
  { id: 'FOLCARA-VIA',       name: 'Via Folcara',              lat: 41.5020, lon: 13.8200 },
  { id: 'FOLCARA-CAMPUS',    name: 'Campus UNICAS Folcara',    lat: 41.5041, lon: 13.8189 },
];

// ── State ────────────────────────────────────────────────────
const state = {
  vehicles:       {},
  markers:        {},
  selectedVehicle: null,
  lastUpdate:     null,
  currentTab:     'map',
  chatLang:       'en',
  isChatThinking: false,
  deferredInstall: null,
};

// ── Map Initialisation ────────────────────────────────────────
const map = L.map('map', {
  center: [41.497, 13.822],
  zoom: 14,
  zoomControl: true,
});

// Dark CartoDB tiles
L.tileLayer(
  'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png',
  { attribution: '© OpenStreetMap © CARTO', maxZoom: 19 }
).addTo(map);

// Draw stops
STOPS.forEach(stop => {
  L.circleMarker([stop.lat, stop.lon], {
    radius: 7,
    fillColor: '#0f1623',
    color: '#3B82F6',
    weight: 2,
    opacity: 1,
    fillOpacity: 1,
  })
  .addTo(map)
  .bindPopup(buildStopPopup(stop));
});

// Draw route line
L.polyline(
  STOPS.map(s => [s.lat, s.lon]),
  { color: '#1e3a6e', weight: 4, opacity: 0.8, dashArray: '8 8' }
).addTo(map);

function buildStopPopup(stop) {
  return `<div style="padding:10px;min-width:150px;font-family:'DM Mono',monospace">
    <div style="font-family:Syne,sans-serif;font-weight:700;color:#3B82F6;margin-bottom:6px">
      🚏 ${stop.name}
    </div>
    <div style="font-size:10px;color:#4B5563">
      Linea 16 · Magni Autoservizi
    </div>
    <div style="margin-top:8px;display:flex;flex-direction:column;gap:4px">
      <span style="color:#3B82F6;cursor:pointer;font-size:10px"
        onclick="selectStopForETA('${stop.id}')">
        → View ETA at this stop
      </span>
    </div>
  </div>`;
}

function selectStopForETA(stopId) {
  document.getElementById('stopPicker').value = stopId;
  switchTab('eta');
  loadETA();
}

// ── Bus Icon ─────────────────────────────────────────────────
function buildBusIcon(vehicleId, status) {
  const colour = STATUS_COLOUR[status] || STATUS_COLOUR.UNKNOWN;
  return L.divIcon({
    className: '',
    html: `<div style="display:flex;flex-direction:column;align-items:center">
      <div style="
        width:38px;height:38px;
        border-radius:50% 50% 50% 0;
        transform:rotate(-45deg);
        background:${colour};
        border:2.5px solid white;
        box-shadow:0 3px 14px rgba(0,0,0,.7);
        display:flex;align-items:center;justify-content:center">
        <span style="transform:rotate(45deg);font-size:16px">🚌</span>
      </div>
      <div style="
        margin-top:3px;
        background:${colour};
        color:white;
        font-family:'DM Mono',monospace;
        font-size:9px;
        padding:2px 5px;
        border-radius:3px;
        box-shadow:0 2px 6px rgba(0,0,0,.5);
        white-space:nowrap">
        ${vehicleId}
      </div>
    </div>`,
    iconSize: [60, 58],
    iconAnchor: [19, 50],
  });
}

// ── Fetch Vehicles ────────────────────────────────────────────
async function fetchVehicles() {
  try {
    const response = await fetch(`${API}/vehicles`);
    if (!response.ok) throw new Error(response.status);

    const vehicles = await response.json();
    state.lastUpdate = new Date();

    // Update header status
    setOnline(true, vehicles.length);

    // Update map markers
    vehicles.forEach(v => {
      state.vehicles[v.vehicle_id] = v;
      const status = v.schedule_status || 'UNKNOWN';
      const pos = [v.lat, v.lon];

      if (state.markers[v.vehicle_id]) {
        state.markers[v.vehicle_id].setLatLng(pos);
        state.markers[v.vehicle_id].setIcon(
          buildBusIcon(v.vehicle_id, status)
        );
      } else {
        const marker = L.marker(pos, {
          icon: buildBusIcon(v.vehicle_id, status),
        }).addTo(map);
        marker.bindPopup(buildVehiclePopup(v));
        marker.on('click', () => {
          selectVehicle(v.vehicle_id);
        });
        state.markers[v.vehicle_id] = marker;
      }

      state.markers[v.vehicle_id]
        .setPopupContent(buildVehiclePopup(v));
    });

    // Update fleet tab if visible
    if (state.currentTab === 'fleet') {
      renderFleetTab(vehicles);
    }

  } catch (error) {
    setOnline(false, 0);
    console.error('Fetch vehicles failed:', error);
  }
}

function buildVehiclePopup(v) {
  const colour = STATUS_COLOUR[v.schedule_status] || STATUS_COLOUR.UNKNOWN;
  const label  = STATUS_LABEL[v.schedule_status]  || 'LIVE';
  return `<div style="padding:12px;min-width:180px;font-family:'DM Mono',monospace">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:9px">
      <div style="font-family:Syne,sans-serif;font-weight:700;font-size:14px;color:${colour}">
        🚌 ${v.vehicle_id}
      </div>
      <div style="font-size:9px;padding:2px 7px;border-radius:4px;
        background:${colour}22;color:${colour};border:1px solid ${colour}44">
        ${label}
      </div>
    </div>
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:7px;font-size:11px">
      <div><div style="color:#4B5563;font-size:9px">SPEED</div>${v.speed_kmh ? v.speed_kmh.toFixed(1) + ' km/h' : '—'}</div>
      <div><div style="color:#4B5563;font-size:9px">ROUTE</div>Linea 16</div>
      <div><div style="color:#4B5563;font-size:9px">PASSENGERS</div>${v.estimated_passengers ? '~' + v.estimated_passengers : '—'}</div>
      <div><div style="color:#4B5563;font-size:9px">CROWDING</div>${v.crowding_level || '—'}</div>
    </div>
  </div>`;
}

function selectVehicle(vehicleId) {
  state.selectedVehicle = vehicleId;
  const v = state.vehicles[vehicleId];
  if (v) {
    map.flyTo([v.lat, v.lon], 16, { duration: 1.2 });
    state.markers[vehicleId]?.openPopup();
  }
}

// ── Fleet Tab ─────────────────────────────────────────────────
function renderFleetTab(vehicles) {
  const container = document.getElementById('fleetList');

  if (!vehicles || vehicles.length === 0) {
    container.innerHTML = `<div class="empty">
      <div class="empty-icon">🚌</div>
      <p>No active buses.<br>Start the GPS simulator.</p>
    </div>`;
    return;
  }

  container.innerHTML = '';

  vehicles.forEach(v => {
    const status  = v.schedule_status || 'UNKNOWN';
    const colour  = STATUS_COLOUR[status];
    const label   = STATUS_LABEL[status];
    const speed   = v.speed_kmh ? v.speed_kmh.toFixed(1) : '0.0';
    const pax     = v.estimated_passengers
      ? '~' + v.estimated_passengers : '—';
    const crowdPc = crowdPercent(v.crowding_level);
    const crowdC  = crowdPc > 70 ? '#EF4444'
      : crowdPc > 40 ? '#F59E0B' : '#22C55E';

    const card = document.createElement('div');
    card.className = 'bus-card';
    card.innerHTML = `
      <div class="bus-stripe" style="background:${colour}"></div>
      <div style="padding-left:8px">
        <div class="bus-header">
          <div class="bus-id" style="color:${colour}">${v.vehicle_id}</div>
          <div class="bus-badge"
            style="color:${colour};border-color:${colour}44;background:${colour}18">
            ${label}
          </div>
        </div>
        <div class="bus-stats">
          <div>
            <div class="bus-stat-label">Speed</div>
            <div class="bus-stat-value">${speed} km/h</div>
          </div>
          <div>
            <div class="bus-stat-label">Passengers</div>
            <div class="bus-stat-value">${pax}</div>
          </div>
          <div>
            <div class="bus-stat-label">Crowding</div>
            <div class="bus-stat-value">${v.crowding_level || '—'}</div>
          </div>
        </div>
        <div class="crowd-bar">
          <div class="crowd-fill"
            style="width:${crowdPc}%;background:${crowdC}">
          </div>
        </div>
      </div>`;
    card.addEventListener('click', () => {
      selectVehicle(v.vehicle_id);
      switchTab('map');
    });
    container.appendChild(card);
  });
}

// ── ETA Tab ───────────────────────────────────────────────────
async function loadETA() {
  const stopId = document.getElementById('stopPicker').value;
  const list   = document.getElementById('etaList');

  if (!stopId) {
    list.innerHTML = `<div class="empty">
      <div class="empty-icon">🚏</div>
      <p>Select a stop to see<br>predicted arrivals.</p>
    </div>`;
    return;
  }

  list.innerHTML = `<div class="empty">
    <div class="empty-icon">⏳</div><p>Loading...</p>
  </div>`;

  try {
    const r = await fetch(`${API}/stops/${stopId}/arrivals`);
    const arrivals = await r.json();

    if (!arrivals || arrivals.length === 0) {
      list.innerHTML = `<div class="empty">
        <div class="empty-icon">🚌</div>
        <p>No buses expected soon.<br>Try again in a moment.</p>
      </div>`;
      return;
    }

    list.innerHTML = '';
    arrivals.forEach(a => {
      const now     = Date.now();
      const eta     = new Date(a.estimated_arrival).getTime();
      const diffMin = Math.max(0, Math.round((eta - now) / 60000));
      const diffSec = Math.max(0, Math.round((eta - now) / 1000));

      const countdownText = diffMin > 0
        ? `${diffMin} min`
        : diffSec > 0
          ? `${diffSec} sec`
          : 'Arriving now';

      const countdownClass = diffMin > 5
        ? '' : diffMin > 2 ? 'amber' : 'red';

      const sc = STATUS_COLOUR[a.schedule_status]
        || STATUS_COLOUR.UNKNOWN;
      const sl = STATUS_LABEL[a.schedule_status]
        || a.schedule_status;

      const card = document.createElement('div');
      card.className = 'eta-card';
      card.innerHTML = `
        <div class="eta-header">
          <div class="eta-bus-id">${a.vehicle_id}</div>
          <div style="font-family:'DM Mono',monospace;font-size:9px;
            padding:2px 7px;border-radius:4px;
            color:${sc};border:1px solid ${sc}44;background:${sc}18">
            ${sl}
          </div>
        </div>
        <div class="eta-countdown ${countdownClass}">
          ${countdownText}
        </div>
        <div class="eta-times">
          <span>Est: ${formatTime(a.estimated_arrival)}</span>
          <span>Scheduled: ${formatTime(a.scheduled_arrival)}</span>
        </div>`;
      list.appendChild(card);
    });

  } catch (e) {
    list.innerHTML = `<div class="empty">
      <div class="empty-icon">❌</div>
      <p>Could not load ETA data.<br>Check backend is running.</p>
    </div>`;
  }
}

// ── Journey Planner ───────────────────────────────────────────
async function searchJourney() {
  const originVal = document.getElementById('jpFrom').value;
  const destVal   = document.getElementById('jpTo').value;
  const results   = document.getElementById('journeyResults');
  const btn       = document.getElementById('jpBtn');

  if (!originVal || !destVal) {
    results.innerHTML = `<div class="empty">
      <div class="empty-icon">⚠️</div>
      <p>Select both origin<br>and destination.</p>
    </div>`;
    return;
  }

  if (originVal === destVal) {
    results.innerHTML = `<div class="empty">
      <div class="empty-icon">🤔</div>
      <p>Origin and destination<br>are the same.</p>
    </div>`;
    return;
  }

  const [originId, oLat, oLon] = originVal.split('|');
  const [destId,   dLat, dLon] = destVal.split('|');

  const stopName = id => STOPS.find(s => s.id === id)?.name || id;

  results.innerHTML = `<div class="empty">
    <div class="empty-icon">⏳</div>
    <p>Finding best routes...</p>
  </div>`;
  btn.disabled = true;
  btn.textContent = 'Searching...';

  try {
    const r = await fetch(`${OMNIMOVE_API}/journeys/search`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        origin_lat:  parseFloat(oLat),
        origin_lon:  parseFloat(oLon),
        origin_name: stopName(originId),
        dest_lat:    parseFloat(dLat),
        dest_lon:    parseFloat(dLon),
        dest_name:   stopName(destId),
        modes: ['BUS', 'BIKE', 'SCOOTER', 'WALK'],
      }),
    });

    const data = await r.json();

    if (!data.options || data.options.length === 0) {
      results.innerHTML = `<div class="empty">
        <div class="empty-icon">😔</div>
        <p>No routes found.</p>
      </div>`;
      return;
    }

    results.innerHTML = '';
    if (data.weather_summary) {
      const wb = document.createElement('div');
      wb.style.cssText = "background:var(--panel2);border:1px solid var(--border);border-radius:8px;padding:10px 12px;margin-bottom:8px;font-family:'DM Mono',monospace;font-size:11px;color:var(--text)";
      wb.textContent = data.weather_summary + (data.temperature_celsius ? ' · ' + data.temperature_celsius.toFixed(1) + '°C' : '');
      results.appendChild(wb);
    }
    const _rh = document.createElement('div');
    _rh.style.cssText = "font-family:'DM Mono',monospace;font-size:10px;color:#4B5563;margin-bottom:4px";
    _rh.textContent = (data.origin || '') + ' → ' + (data.destination || '') + (data.realtimeAvailable ? ' · 🟢 Live data' : '');
    results.appendChild(_rh);

    data.options.forEach((opt, i) => {
      const mc  = MODE_COLOUR[opt.mode] || '#3B82F6';
      const mi  = MODE_ICON[opt.mode]   || '🚌';
      const gi  = opt.green_index       || 0;
      const giC = gi >= 80 ? '#22C55E'
        : gi >= 60 ? '#F59E0B' : '#EF4444';
      const giL = gi >= 90 ? 'Excellent ♻️'
        : gi >= 70 ? 'Good 🌿'
        : gi >= 50 ? 'Moderate 🌱' : 'Poor ⚠️';

      const card = document.createElement('div');
      card.className = 'jopt-card';
      if (i === 0) card.style.borderColor = mc;

      card.innerHTML = `
        <div class="jopt-stripe" style="background:${mc}"></div>
        <div style="padding-left:8px">
          <div class="jopt-header">
            <div class="jopt-mode" style="color:${mc}">
              ${mi} ${opt.mode_label}
            </div>
            <div class="jopt-time" style="color:${mc}">
              ${opt.duration_minutes}<span> min</span>
            </div>
          </div>
          <div class="jopt-stats">
            <div>
              <div class="jopt-stat-label">Cost</div>
              <div class="jopt-stat-value">
                €${opt.cost_euros.toFixed(2)}
              </div>
            </div>
            <div>
              <div class="jopt-stat-label">CO₂</div>
              <div class="jopt-stat-value">
                ${opt.co2_grams
                  ? opt.co2_grams.toFixed(0) + 'g' : '0g'}
              </div>
            </div>
            <div>
              <div class="jopt-stat-label">Distance</div>
              <div class="jopt-stat-value">
                ${formatDist(opt.distance_metres)}
              </div>
            </div>
          </div>
          <div class="green-row">
            <div class="green-track">
              <div class="green-fill"
                style="width:${gi}%;background:${giC}">
              </div>
            </div>
            <div class="green-text" style="color:${giC}">
              ${gi}/100 · ${giL}
            </div>
          </div>
          ${opt.mode === 'WALK' ? `<div style="font-family:'DM Mono',monospace;font-size:10px;color:var(--green);margin-top:5px;font-style:italic">🌿 Have a car free day!</div>` : ''}
          ${opt.weather_warning ? `<div style="font-family:'DM Mono',monospace;font-size:10px;color:var(--amber);margin-top:6px;padding:5px 8px;background:rgba(245,158,11,0.1);border-radius:5px;border:1px solid rgba(245,158,11,0.3)">${opt.weather_warning}</div>` : ''}
        </div>`;
      results.appendChild(card);
    });

  } catch (e) {
    results.innerHTML = `<div class="empty">
      <div class="empty-icon">❌</div>
      <p>Could not reach backend.</p>
    </div>`;
  } finally {
    btn.disabled = false;
    btn.textContent = '🔍 Find Best Route';
  }
}

// ── AI Chat ───────────────────────────────────────────────────
function setLang(lang) {
  state.chatLang = lang;
  document.getElementById('langEN').className =
    'lang-opt' + (lang === 'en' ? ' active' : '');
  document.getElementById('langIT').className =
    'lang-opt' + (lang === 'it' ? ' active' : '');
}

function handleChatKey(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendChat();
  }
}

function askSuggestion(el) {
  document.getElementById('chatInput').value =
    el.textContent;
  sendChat();
}

function addMessage(role, text, isTyping = false) {
  const container = document.getElementById('chatMessages');
  const div = document.createElement('div');
  div.className = `msg ${role}`;

  const who = document.createElement('div');
  who.className = 'msg-who';
  who.textContent = role === 'user' ? 'YOU' : 'CASSITRACK AI';

  const bubble = document.createElement('div');
  bubble.className = 'msg-text';

  if (isTyping) {
    bubble.innerHTML = `<div class="typing">
      <span></span><span></span><span></span>
    </div>`;
  } else {
    bubble.textContent = text;
  }

  div.appendChild(who);
  div.appendChild(bubble);
  container.appendChild(div);
  container.scrollTop = container.scrollHeight;
  return div;
}

async function sendChat() {
  if (state.isChatThinking) return;

  const input = document.getElementById('chatInput');
  const btn   = document.getElementById('sendBtn');
  const text  = input.value.trim();
  if (!text) return;

  addMessage('user', text);
  input.value = '';

  state.isChatThinking = true;
  btn.disabled = true;

  const typingDiv = addMessage('ai', '', true);

  try {
    const r = await fetch(`${OMNIMOVE_API}/ai/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        message:  text,
        language: state.chatLang,
      }),
    });

    const data = await r.json();
    typingDiv.remove();
    addMessage('ai', data.answer || 'Sorry, could not process that.');

  } catch (e) {
    typingDiv.remove();
    addMessage('ai', 'Cannot reach the backend. Make sure Spring Boot is running.');
  } finally {
    state.isChatThinking = false;
    btn.disabled = false;
    input.focus();
  }
}

// ── Tab Switching ─────────────────────────────────────────────
function switchTab(name) {
  state.currentTab = name;

  document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.classList.toggle(
      'active',
      btn.dataset.tab === name
    );
  });

  document.querySelectorAll('.tab-panel').forEach(p => {
    p.classList.remove('active');
  });

  const panel = document.getElementById('panel-' + name);
  if (panel) panel.classList.add('active');

  const sheet = document.querySelector('.bottom-sheet');

  if (name === 'map') {
    sheet.classList.remove('expanded');
    sheet.classList.add('collapsed');
    map.invalidateSize();
  } else {
    sheet.classList.remove('collapsed');
    sheet.classList.add('expanded');
    map.invalidateSize();

    if (name === 'fleet') {
      renderFleetTab(Object.values(state.vehicles));
    }
    if (name === 'eta') {
      loadETA();
    }
  }
}

// ── PWA Install Prompt ────────────────────────────────────────
window.addEventListener('beforeinstallprompt', e => {
  e.preventDefault();
  state.deferredInstall = e;

  const banner = document.getElementById('installBanner');
  if (banner) {
    banner.classList.remove('hidden');
  }
});

function installApp() {
  if (!state.deferredInstall) return;
  state.deferredInstall.prompt();
  state.deferredInstall.userChoice.then(result => {
    if (result.outcome === 'accepted') {
      console.log('CASSITRACK installed!');
    }
    state.deferredInstall = null;
    const banner = document.getElementById('installBanner');
    if (banner) banner.classList.add('hidden');
  });
}

// ── Header Status ─────────────────────────────────────────────
function setOnline(online, count) {
  const dot  = document.getElementById('statusDot');
  const text = document.getElementById('statusText');

  if (online) {
    dot.className = 'dot dot-green';
    text.textContent = count > 0
      ? `${count} bus${count !== 1 ? 'es' : ''} active`
      : 'Connected';
  } else {
    dot.className = 'dot dot-red';
    text.textContent = 'Backend offline';
  }
}

// ── Helpers ───────────────────────────────────────────────────
function crowdPercent(level) {
  return { LOW: 18, MEDIUM: 48, HIGH: 74, VERY_HIGH: 95 }[level] || 0;
}

function formatTime(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleTimeString('it-IT', {
    hour: '2-digit', minute: '2-digit'
  });
}

function formatDist(metres) {
  if (!metres) return '—';
  return metres < 1000
    ? Math.round(metres) + 'm'
    : (metres / 1000).toFixed(1) + 'km';
}

// ── Boot ──────────────────────────────────────────────────────
// Register service worker
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker
      .register('./sw.js')
      .then(() => console.log('[PWA] Service worker registered'))
      .catch(e => console.warn('[PWA] SW registration failed:', e));
  });
}

// Hide loading screen
setTimeout(() => {
  document.getElementById('loading').classList.add('gone');
}, 1500);

// Start fetching
fetchVehicles();
setInterval(fetchVehicles, REFRESH);

// Update countdown timer in header
setInterval(() => {
  if (state.lastUpdate) {
    const sec = Math.round(
      (Date.now() - state.lastUpdate.getTime()) / 1000
    );
    const next = Math.max(0, 15 - sec);
    document.getElementById('statusTime').textContent =
      `· ${next}s`;
  }
}, 1000);