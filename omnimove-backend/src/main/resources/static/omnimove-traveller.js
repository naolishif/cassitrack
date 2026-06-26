//  FRONTEND ROUTE GUARD
if (!localStorage.getItem('omnimove_token')) {
    window.location.href = 'omnimove-login.html';
}

const _user = JSON.parse(localStorage.getItem('omnimove_user') || '{}');
document.getElementById('sidebarName').textContent  = _user.name || _user.username || 'Utente';
document.getElementById('sidebarEmail').textContent = _user.email || '';
document.getElementById('profileName').textContent  = _user.name  || _user.username || 'Utente';
document.getElementById('profileEmail').textContent = _user.email || '';
document.getElementById('paymentEmail').textContent = _user.email || '';

const _name = _user.name || _user.username || 'there';
document.getElementById('aiGreeting').textContent =
    `👋 Hi ${_name}! I'm OmniAI. I can help you find the best route, check real-time delays, or suggest eco-friendly alternatives. What do you need?`;

const API_BASE = '/api/v1';

async function loadEcoStats() {
    try {
        const r = await apiFetch('/traveller/stats');
        if (!r.ok) throw new Error('stats ' + r.status);
        const s = await r.json();

        document.getElementById('sidebarEcoPoints').textContent = s.ecoPoints.toLocaleString() + ' pts';
        document.getElementById('sidebarEcoSub').textContent = `🌱 ${s.co2SavedKg} kg CO₂ saved this month`;

        document.getElementById('statEcoPoints').textContent = s.ecoPoints.toLocaleString();
        document.getElementById('statCo2Saved').textContent  = s.co2SavedKg + ' kg';
        document.getElementById('statTrips').textContent     = s.trips;
        document.getElementById('statSpent').textContent     = '€' + s.spent30d;
    } catch (e) {
        console.warn('Could not load eco stats:', e);
    }
}

function togglePref(el) {
    el.classList.toggle('on');
}

async function loadPreferences() {
    try {
        const r = await apiFetch('/traveller/preferences');
        if (!r.ok) throw new Error('prefs ' + r.status);
        const p = await r.json();

        // Select
        const sel = document.getElementById('prefDefaultMode');
        if (sel) sel.value = p.defaultJourneyMode || 'ECO';

        // Toggle helper
        const set = (id, val) => {
            const el = document.getElementById(id);
            if (el) el.classList.toggle('on', !!val);
        };

        set('prefAvoidOccupancy', p.avoidHighOccupancy);
        set('prefShowWalking',    p.showWalking);
        set('prefBikeOverBus',    p.preferBikeOverBus);
        set('prefNotifyDelays',   p.notifyDelays);
        set('prefNotifyTicket',   p.notifyTicketExpiry);
        set('prefNotifyEcoTip',   p.notifyEcoTip);

        // Applica subito il sort di default ai chip nella topbar
        const sortMap = { ECO: 'eco', BUDGET: 'budget', FAST: 'fast' };
        const sortVal = sortMap[p.defaultJourneyMode] || 'eco';
        document.querySelectorAll('#sortChips .cat-chip').forEach(c => {
            c.classList.toggle('active', c.dataset.sort === sortVal);
        });
        activeSort = sortVal;

    } catch (e) {
        console.warn('Could not load preferences:', e);
    }
}

async function savePreferences() {
    const isOn = id => document.getElementById(id)?.classList.contains('on') ?? false;

    const body = {
        defaultJourneyMode: document.getElementById('prefDefaultMode')?.value || 'ECO',
        avoidHighOccupancy: isOn('prefAvoidOccupancy'),
        showWalking:        isOn('prefShowWalking'),
        preferBikeOverBus:  isOn('prefBikeOverBus'),
        notifyDelays:       isOn('prefNotifyDelays'),
        notifyTicketExpiry: isOn('prefNotifyTicket'),
        notifyEcoTip:       isOn('prefNotifyEcoTip')
    };

    try {
        const r = await apiFetch('/traveller/preferences', {
            method: 'PUT',
            body: JSON.stringify(body)
        });
        if (!r.ok) throw new Error('save prefs ' + r.status);

        // Aggiorna subito il sort attivo nella topbar
        const sortMap = { ECO: 'eco', BUDGET: 'budget', FAST: 'fast' };
        const sortVal = sortMap[body.defaultJourneyMode] || 'eco';
        document.querySelectorAll('#sortChips .cat-chip').forEach(c => {
            c.classList.toggle('active', c.dataset.sort === sortVal);
        });
        activeSort = sortVal;

        showToast('✅ Preferences saved!');
    } catch (e) {
        console.warn('Could not save preferences:', e);
        showToast('Errore nel salvataggio preferenze', true);
    }
}

function timeAgoLabel(dateStr) {
    const d = new Date(dateStr);
    const now = new Date();
    const sameDay = d.toDateString() === now.toDateString();
    const yest = new Date(now); yest.setDate(now.getDate() - 1);
    const isYest = d.toDateString() === yest.toDateString();

    const hh = String(d.getHours()).padStart(2, '0');
    const mm = String(d.getMinutes()).padStart(2, '0');

    if (sameDay) return { date: 'Today', sub: `Today, ${hh}:${mm}` };
    if (isYest)  return { date: 'Yesterday', sub: `Yesterday, ${hh}:${mm}` };
    const opts = { weekday: 'short', day: 'numeric', month: 'short' };
    const label = d.toLocaleDateString('en-GB', opts);
    return { date: label, sub: `${label}, ${hh}:${mm}` };
}

const MODE_ICON = { BUS: ['ri-bus', '🚌'], BIKE: ['ri-bike', '🚲'], SCOOTER: ['ri-scooter', '🛴'], WALK: ['ri-walk', '🚶'] };

function renderHistory(items) {
    const container = document.getElementById('history-list');
    if (!items || items.length === 0) {
        container.innerHTML = '<div class="empty-state">No trips yet. Start your first journey!</div>';
        return;
    }

    container.innerHTML = items.map(j => {
        const [iconClass, emoji] = MODE_ICON[j.mode] || ['ri-bus', '🚌'];
        const { date, sub } = timeAgoLabel(j.createdAt);
        const cost = (j.costEuros ?? 0).toFixed(2);
        const origin = j.originName || 'My Location';
        const dest = j.destName || '—';

        return `
        <div class="route-hist-card">
            <div class="route-hist-icon ${iconClass}">${emoji}</div>
            <div class="route-hist-info">
                <div class="route-hist-name">${origin} → ${dest}</div>
                <div class="route-hist-sub">${sub} · €${cost}</div>
            </div>
            <div class="route-hist-meta">
                <div class="route-hist-date">${date}</div>
                <div class="route-hist-green">🌱 ${j.greenIndex}</div>
            </div>
            <span class="fav-star ${j.isFavorite ? 'starred' : ''}"
                  data-mode="${j.mode}" data-origin="${escAttr(origin)}" data-dest="${escAttr(dest)}"
                  title="Add to favourites">${j.isFavorite ? '★' : '☆'}</span>
        </div>`;
    }).join('');
}

async function loadHistory() {
    try {
        const r = await apiFetch('/traveller/history');
        if (!r.ok) throw new Error('history ' + r.status);
        const items = await r.json();
        renderHistory(items);
    } catch (e) {
        console.warn('Could not load history:', e);
    }
}

function renderFavorites(items) {
    const container = document.getElementById('favs-list');
    if (!items || items.length === 0) {
        container.innerHTML = '<div class="empty-state">No favourites yet. Tap the ☆ on a trip to save its route here.</div>';
        return;
    }

    container.innerHTML = items.map(f => {
        const [iconClass, emoji] = MODE_ICON[f.mode] || ['ri-bus', '🚌'];
        return `
        <div class="route-hist-card">
            <div class="route-hist-icon ${iconClass}">${emoji}</div>
            <div class="route-hist-info">
                <div class="route-hist-name">${f.originName} → ${f.destName}</div>
                <div class="route-hist-sub">${f.mode} · ~€${f.avgCost.toFixed(2)} · Used ${f.usedCount}×</div>
            </div>
            <div class="route-hist-meta">
                <div class="route-hist-green">🌱 ${f.greenIndex}</div>
            </div>
            <span class="fav-star starred" data-mode="${f.mode}" data-origin="${escAttr(f.originName)}" data-dest="${escAttr(f.destName)}">★</span>
        </div>`;
    }).join('');
}

async function loadFavorites() {
    try {
        const r = await apiFetch('/traveller/favorites');
        if (!r.ok) throw new Error('favorites ' + r.status);
        const items = await r.json();
        renderFavorites(items);
    } catch (e) {
        console.warn('Could not load favourites:', e);
    }
}

loadEcoStats();
loadHistory();
loadPreferences();

async function apiFetch(path, options = {}) {
    const token = localStorage.getItem('omnimove_token');
    return fetch(API_BASE + path, {
        ...options,
        headers: {
            'Content-Type': 'application/json',
            ...(token ? {'Authorization': 'Bearer ' + token} : {}),
            ...(options.headers || {})
        }
    });
}

// ── Map init ──────────────────────────────────────────────────────
const map = L.map('map', { zoomControl: true }).setView([41.4901, 13.8303], 15);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '© OpenStreetMap'
}).addTo(map);

// ── Icons ─────────────────────────────────────────────────────────

// Pulsing blue dot — used for "you are here" both on the idle map and during journey
const userIcon = L.divIcon({
    html: `<div style="
        width:18px;height:18px;border-radius:50%;
        background:#3b82f6;border:3px solid white;
        box-shadow:0 0 0 4px rgba(59,130,246,0.35);
        animation:pulse-blue 2s infinite;
    "></div>`,
    className: '',
    iconSize: [18, 18],
    iconAnchor: [9, 9]
});

// Destination pin — SVG teardrop
function makeDestIcon(color) {
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="28" height="42" viewBox="0 0 28 42">
        <circle cx="14" cy="14" r="12" fill="${color}" stroke="white" stroke-width="2.5"
            style="filter:drop-shadow(0 3px 6px rgba(0,0,0,0.35))"/>
        <circle cx="14" cy="14" r="5" fill="white" opacity="0.9"/>
        <line x1="14" y1="26" x2="14" y2="41" stroke="${color}" stroke-width="3"
            stroke-linecap="round" style="filter:drop-shadow(0 2px 3px rgba(0,0,0,0.2))"/>
    </svg>`;
    return L.divIcon({
        html: svg,
        className: '',
        iconSize: [28, 42],
        iconAnchor: [14, 41]
    });
}

// ── Stop markers (bus stops) ──────────────────────────────────────
let STOPS = {};  // popolato dinamicamente da loadStops()

window._stopMarkers = [];

const STOP_ICON = L.divIcon({
    html: '<div style="background:#10b981;color:white;width:28px;height:28px;border-radius:50%;display:flex;align-items:center;justify-content:center;border:2px solid white;box-shadow:0 2px 8px rgba(0,0,0,0.2);font-size:11px;font-weight:800">M</div>',
    className: '', iconSize: [28, 28]
});

function renderStopMarkers() {
    window._stopMarkers.forEach(m => map.removeLayer(m));
    window._stopMarkers = [];
    Object.values(STOPS).forEach(stop => {
        const marker = L.marker([stop.lat, stop.lon], { icon: STOP_ICON })
            .addTo(map)
            .bindPopup('<b>' + stop.name + '</b><br><span style="color:#94a3b8">Caricamento arrivi…</span>');

        marker.on('click', () => loadStopArrivals(stop, marker));
        window._stopMarkers.push(marker);
    });
}

async function loadStopArrivals(stop, marker) {
    try {
        const r = await apiFetch('/journeys/stops/' + encodeURIComponent(stop.id) + '/arrivals');
        if (!r.ok) throw new Error('arrivals ' + r.status);
        const arrivals = await r.json();

        let html = '<b>' + stop.name + '</b>';
        if (!Array.isArray(arrivals) || arrivals.length === 0) {
            html += '<br><span style="color:#94a3b8">Nessun bus in arrivo</span>';
        } else {
            const now = Date.now();
            html += '<div style="margin-top:6px;display:flex;flex-direction:column;gap:4px">'
                + arrivals.slice(0, 5).map(a => {
                    const line = a.route_name || a.route_id || 'Bus';
                    const etaMin = a.estimated_arrival
                        ? Math.max(0, Math.round((new Date(a.estimated_arrival).getTime() - now) / 60000))
                        : null;
                    const when = etaMin === null ? '—' : (etaMin === 0 ? 'in arrivo' : etaMin + ' min');
                    const late = (a.delay_minutes && a.delay_minutes > 0)
                        ? ' <span style="color:#ef4444">+' + a.delay_minutes + '</span>' : '';
                    return '<div style="display:flex;justify-content:space-between;gap:12px;font-size:12px">'
                        + '<span style="font-weight:700">🚌 ' + line + '</span>'
                        + '<span>' + when + late + '</span></div>';
                }).join('')
                + '</div>';
        }
        marker.setPopupContent(html);
    } catch (e) {
        marker.setPopupContent('<b>' + stop.name + '</b><br><span style="color:#ef4444">Arrivi non disponibili</span>');
    }
}

// ── Load stops from the backend → fill dropdowns + map markers ─────
async function loadStops() {
    const originSel = document.getElementById('originSelect');
    const destSel   = document.getElementById('destSelect');
    try {
        const r = await apiFetch('/journeys/stops');
        if (!r.ok) throw new Error('stops ' + r.status);
        const stops = await r.json();

        if (!Array.isArray(stops) || stops.length === 0) {
            showToast('Nessuna fermata disponibile', true);
            return;
        }

        STOPS = {};
        stops.forEach(s => { STOPS[s.id] = { id: s.id, name: s.name, lat: s.lat, lon: s.lon }; });

        const optionsHtml = stops
            .map(s => `<option value="${s.id}">${s.name}</option>`)
            .join('');

        // Origin keeps "My Location" (GPS) first, then every stop
        originSel.innerHTML = '<option value="GPS">📍 My Location</option>' + optionsHtml;
        destSel.innerHTML   = optionsHtml;

        // Sensible defaults: first stop as origin, a different one as destination
        originSel.value = stops[0].id;
        destSel.value   = stops.length > 1 ? stops[1].id : stops[0].id;

        renderStopMarkers();

        const bounds = stops.map(s => [s.lat, s.lon]);
        if (bounds.length) map.fitBounds(bounds, { padding: [60, 60], maxZoom: 15 });
    } catch (e) {
        console.error('loadStops failed:', e);
        showToast('Impossibile caricare le fermate dal backend', true);
    }
}

// ── GPS state ─────────────────────────────────────────────────────
let userLat = null;
let userLon = null;
let userMarker = null;

function placeUserMarker(lat, lon) {
    if (userMarker) map.removeLayer(userMarker);
    userMarker = L.marker([lat, lon], { icon: userIcon })
        .addTo(map)
        .bindPopup('📍 You are here');
}

function tryGetGPS() {
    return new Promise((resolve, reject) => {
        if (!navigator.geolocation) { reject('no_geolocation'); return; }
        navigator.geolocation.getCurrentPosition(
            pos => {
                userLat = pos.coords.latitude;
                userLon = pos.coords.longitude;
                placeUserMarker(userLat, userLon);
                resolve({ name: 'My Location', lat: userLat, lon: userLon, isGPS: true });
            },
            err => {
                // Friendly fallback so the demo still works
                userLat = 41.5020; userLon = 13.8200;
                placeUserMarker(userLat, userLon);
                resolve({ name: 'Via Folcara (approx)', lat: userLat, lon: userLon, isGPS: true });
            },
            { timeout: 8000, maximumAge: 60000 }
        );
    });
}

// ── GPS trigger when user switches to "My Location" ───────────────
document.getElementById('originSelect').addEventListener('change', function () {
    if (this.value === 'GPS') {
        showToast('📡 Getting your location...');
        tryGetGPS()
            .then(pos => {
                showToast('📍 Location detected!');
                map.setView([pos.lat, pos.lon], 16);
            })
            .catch(() => {
                showToast('GPS unavailable — select a stop', true);
                const firstStop = Object.keys(STOPS)[0];
                if (firstStop) this.value = firstStop;
            });
    }
});

function swapStops() {
    const originSel = document.getElementById('originSelect');
    const destSel   = document.getElementById('destSelect');
    const ov = originSel.value;
    const dv = destSel.value;
    if (ov !== 'GPS') destSel.value = ov;
    originSel.value = dv;
}

function getOrigin() {
    const val = document.getElementById('originSelect').value;
    if (val === 'GPS') {
        if (!userLat) return null;
        return { name: 'My Location', lat: userLat, lon: userLon, isGPS: true };
    }
    return { ...STOPS[val], isGPS: false };
}

// ── Filter / sort state ──────────────────────────────────────────
let activeSort  = 'eco';   // 'eco' | 'budget' | 'fast'  — always exactly one
let activeModes = [];      // [] = all modes; otherwise subset of BUS/BIKE/SCOOTER (WALK always included)

function setSort(el) {
    document.querySelectorAll('#sortChips .cat-chip').forEach(c => c.classList.remove('active'));
    el.classList.add('active');
    activeSort = el.dataset.sort;
    if (window._lastSearchData) renderRoutes(window._lastSearchData);
}

function toggleModeChip(el) {
    el.classList.toggle('active');
    activeModes = Array.from(document.querySelectorAll('#modeChips .cat-chip.active'))
        .map(c => c.dataset.mode);
    // Mode filter changes what was actually computed server-side, so re-search.
    doSearch();
}

function sortOptions(options) {
    const sorted = [...options];
    if (activeSort === 'eco') {
        sorted.sort((a, b) => b.green_index - a.green_index);
    } else if (activeSort === 'budget') {
        sorted.sort((a, b) => a.cost_euros - b.cost_euros);
    } else if (activeSort === 'fast') {
        sorted.sort((a, b) => a.duration_minutes - b.duration_minutes);
    }
    return sorted;
}

// ── Search ────────────────────────────────────────────────────────
async function doSearch() {
    const destId = document.getElementById('destSelect').value;
    const dest   = STOPS[destId];
    let origin   = getOrigin();

    if (!origin) {
        showToast('📡 Getting your location...', false);
        try { origin = await tryGetGPS(); }
        catch (e) { showToast('GPS unavailable — select a stop as origin', true); return; }
    }

    if (origin.name === dest.name) {
        showToast('Origin and destination cannot be the same', true); return;
    }

    // Switch to map pane
    document.querySelectorAll('.sidebar-nav .nav-item').forEach(n => n.classList.remove('active'));
    document.querySelector('[data-pane="map"]').classList.add('active');
    document.querySelectorAll('.pane').forEach(p => p.classList.remove('active'));
    document.getElementById('pane-map').classList.add('active');
    setTimeout(() => map.invalidateSize(), 50);

    document.querySelector('.routes-list').innerHTML =
        '<div style="text-align:center;padding:40px 20px;color:var(--text-soft)">'
        + '<div style="font-size:28px;margin-bottom:10px">🔄</div>'
        + '<div style="font-size:13px;font-weight:600">Finding best routes...</div>'
        + '</div>';

    try {
        const payload = {
            origin_lat: origin.lat, origin_lon: origin.lon, origin_name: origin.name, origin_is_gps: origin.isGPS === true,
            dest_lat:   dest.lat,   dest_lon:   dest.lon,   dest_name:   dest.name,
            user_id: _user.id
        };
        // Only constrain modes when the traveler picked specific ones via the chips.
        if (activeModes.length > 0) {
            payload.modes = [...activeModes];
        }
        const r = await apiFetch('/journeys/search', {
            method: 'POST',
            body: JSON.stringify(payload)
        });
        if (r.status === 429) throw new Error('rate_limited');
        if (!r.ok) throw new Error('Search failed');
        const data = await r.json();
        window._currentOrigin = origin;
        window._currentDest   = dest;
        window._lastSearchData = data;
        renderRoutes(data);
    } catch (e) {
        const msg = e.message === 'rate_limited'
            ? 'Too many searches. Please wait a moment before trying again.'
            : 'Could not load routes.';
        document.querySelector('.routes-list').innerHTML =
            '<div style="text-align:center;padding:40px 20px;color:var(--red)">'
            + '<div style="font-size:28px;margin-bottom:10px">⚠️</div>'
            + `<div style="font-size:13px;font-weight:600">${msg}</div>`
            + '</div>';
    }
}

// ── Route rendering ───────────────────────────────────────────────
const MODE_ICONS = { BUS:'🚌', BIKE:'🚲', SCOOTER:'🛴', WALK:'🚶' };
const MODE_BTNS  = {
    BUS:     { label:'Select Bus',     cls:'btn-dark'   },
    BIKE:    { label:'Select Bike',    cls:'btn-blue'   },
    SCOOTER: { label:'Select Scooter', cls:'btn-purple' },
    WALK:    { label:'Start Walking',  cls:'btn-green'  },
};
const LINE_COLORS = { BUS:'#0f172a', BIKE:'#3b82f6', SCOOTER:'#7c3aed', WALK:'#10b981' };

function greenColor(g) {
    return g >= 75 ? '#10b981' : g >= 50 ? '#f59e0b' : '#ef4444';
}

let selectedJourney = null;

function renderRoutes(data) {
    if (data.weather_summary) {
        document.querySelector('.weather-pill').textContent = data.weather_summary;
    }
    const list = document.querySelector('.routes-list');
    if (!data.options || data.options.length === 0) {
        list.innerHTML = '<div style="padding:20px;color:var(--text-soft);font-size:13px">No routes found.</div>';
        return;
    }

    window._routeOptions = {};
    const orderedOptions = sortOptions(data.options);

    list.innerHTML = orderedOptions.map(opt => {
        window._routeOptions[opt.mode] = opt;

        const icon = MODE_ICONS[opt.mode] || '🚗';
        const btn  = MODE_BTNS[opt.mode]  || { label: 'Select', cls: 'btn-dark' };
        const cost = opt.cost_euros === 0 ? 'Free' : '€' + opt.cost_euros.toFixed(2);
        const co2  = opt.co2_grams > 0 ? Math.round(opt.co2_grams) + ' g' : '0 g';
        const warn = opt.weather_warning
            ? `<span class="status-badge s-delay">${opt.weather_warning}</span>` : '';
        return `
<div class="route-card" id="card-${opt.mode}">
    <div class="route-top">
        <div class="route-name">${icon} ${opt.mode_label}</div>
        <div class="route-time">${opt.duration_minutes} min</div>
    </div>
    <div class="status-row">
        ${warn || '<span class="status-badge s-ok">✓ Available</span>'}
    </div>
    <div class="metrics-row">
        <div class="metric-box"><div class="metric-label">Cost</div><div class="metric-value">${cost}</div></div>
        <div class="metric-box"><div class="metric-label">CO₂</div><div class="metric-value">${co2}</div></div>
        <div class="metric-box"><div class="metric-label">Green</div>
            <div class="metric-value" style="color:${greenColor(opt.green_index)}">${opt.green_index}/100</div>
        </div>
    </div>
    <button class="action-btn ${btn.cls}"
        onclick="selectMode('${opt.mode}','${opt.mode_label}',${opt.green_index},${opt.distance_metres},${opt.cost_euros})">
        ${btn.label}
    </button>
</div>`;
    }).join('');
}

// selectMode — only highlights the card and shows the Start Journey banner.
// The actual map drawing happens in startJourney so GPS is resolved first.
function selectMode(mode, label, greenIndex, distanceMetres, costEuros) {
    selectedJourney = { mode, label, greenIndex, distanceKm: distanceMetres / 1000, costEuros };

    if (window._routeOptions && window._routeOptions[mode]) {
        selectedJourney.legs = window._routeOptions[mode].legs || [];
    }

    // Highlight selected card
    document.querySelectorAll('.route-card').forEach(c => {
        c.style.border = '1px solid var(--border-mid)';
        c.style.opacity = '0.6';
    });
    const card = document.getElementById('card-' + mode);
    if (card) { card.style.border = '2px solid var(--primary)'; card.style.opacity = '1'; }

    // Remove old banner if any
    const existing = document.getElementById('start-banner');
    if (existing) existing.remove();

    const modeEmoji = MODE_ICONS[mode];

    const banner = document.createElement('div');
    banner.id = 'start-banner';
    banner.style.cssText = 'position:sticky;bottom:0;background:var(--bg-white);border-top:2px solid var(--primary);padding:14px;margin:0 -14px -14px';

    const infoDiv = document.createElement('div');
    infoDiv.style.cssText = 'font-size:11px;color:var(--text-soft);margin-bottom:8px;font-weight:600';
    infoDiv.textContent = `${modeEmoji} ${label} · 🌱 ${greenIndex}/100 · ${(distanceMetres/1000).toFixed(1)} km`;

    const startBtn = document.createElement('button');
    startBtn.className = 'action-btn btn-green';
    startBtn.style.cssText = 'width:100%;font-size:14px;padding:13px';
    startBtn.textContent = '🚀 Start Journey';
    startBtn.onclick = startJourney;

    banner.appendChild(infoDiv);
    banner.appendChild(startBtn);
    document.querySelector('.routes-list').appendChild(banner);

    showToast(`${modeEmoji} ${label} selected — tap Start Journey`);
}

async function startJourney() {
    if (!selectedJourney) return;
    if (window._journeyStarting) return;
    window._journeyStarting = true;
    const _startBtn = document.querySelector('#start-banner button');
    if (_startBtn) { _startBtn.disabled = true; _startBtn.textContent = '⏳ Starting…'; }

    try {
        const origin = window._currentOrigin;
        const dest   = window._currentDest;

        // 1) GPS silenzioso
        let gpsPos = null;
        if (origin && origin.isGPS) {
            try {
                gpsPos = await new Promise((resolve, reject) => {
                    if (!navigator.geolocation) { reject(); return; }
                    navigator.geolocation.getCurrentPosition(
                        p => resolve({ lat: p.coords.latitude, lon: p.coords.longitude }),
                        () => reject(),
                        { timeout: 6000, maximumAge: 30000 }
                    );
                });
                userLat = gpsPos.lat;
                userLon = gpsPos.lon;
            } catch (e) { gpsPos = null; }
        }

        // 2) Registra l'evento (non bloccante)
        try {
            await apiFetch('/journeys/select', {
                method: 'POST',
                body: JSON.stringify({
                    mode:        selectedJourney.mode,
                    green_index: selectedJourney.greenIndex,
                    distance_km: selectedJourney.distanceKm,
                    cost_euros:  selectedJourney.costEuros,
                    origin_name: origin ? origin.name : null,
                    dest_name:   dest ? dest.name : null
                })
            });
        } catch (e) { console.warn('Could not record journey event:', e); }

        // 3) Nascondi le fermate
        if (window._stopMarkers) window._stopMarkers.forEach(m => map.removeLayer(m));

        // 4) Pulisci i layer del viaggio precedente
        ['_routeLine','_routeLineGps','_journeyDestMarker','_journeyOriginMarker','_journeyGpsMarker']
            .forEach(k => { if (window[k]) { map.removeLayer(window[k]); window[k] = null; } });

        const { mode, label, greenIndex, distanceKm } = selectedJourney;
        const color = LINE_COLORS[mode] || '#0f172a';

        // 5) Marker origine
        const stopIcon = L.divIcon({
            html: '<div style="background:#10b981;color:white;width:28px;height:28px;border-radius:50%;display:flex;align-items:center;justify-content:center;border:2px solid white;box-shadow:0 2px 8px rgba(0,0,0,0.2);font-size:11px;font-weight:800">M</div>',
            className: '', iconSize: [28, 28]
        });
        window._journeyOriginMarker = L.marker([origin.lat, origin.lon], { icon: stopIcon })
            .addTo(map)
            .bindPopup('<b>' + origin.name + '</b><br>Starting point');

        // 6) Linea GPS→fermata solo se origine = My Location
        if (gpsPos && origin.isGPS) {
            placeUserMarker(gpsPos.lat, gpsPos.lon);
            window._routeLineGps = L.polyline(
                [[gpsPos.lat, gpsPos.lon], [origin.lat, origin.lon]],
                { color: '#94a3b8', weight: 3, opacity: 0.7, dashArray: '6,6' }
            ).addTo(map);
        } else if (gpsPos) {
            placeUserMarker(gpsPos.lat, gpsPos.lon);
        } else if (userMarker) {
            map.removeLayer(userMarker);
            userMarker = null;
        }

        // 7) Marker destinazione
        window._journeyDestMarker = L.marker([dest.lat, dest.lon], { icon: makeDestIcon('#ef4444') })
            .addTo(map)
            .bindPopup('<b>📍 ' + dest.name + '</b>').openPopup();

        // 8) Disegna il percorso
        window._busRouteLines = window._busRouteLines || [];
        window._busRouteLines.forEach(l => map.removeLayer(l));
        window._busRouteLines = [];

        if (mode === 'BUS' && selectedJourney.legs && selectedJourney.legs.length > 0) {
            const busColors = ['#0f172a', '#3b82f6', '#7c3aed'];
            let colorIdx = 0;

            selectedJourney.legs.forEach(leg => {
                if (leg.mode === 'BUS' && leg.stop_coords && leg.stop_coords.length >= 2) {
                    const legColor = busColors[colorIdx % busColors.length];
                    colorIdx++;
                    const coords = leg.stop_coords.map(c => [c[0], c[1]]);
                    const line = L.polyline(coords, { color: legColor, weight: 5, opacity: 0.9 }).addTo(map);
                    window._busRouteLines.push(line);

                    leg.stop_coords.forEach((c, i) => {
                        const isFirst = i === 0;
                        const isLast  = i === leg.stop_coords.length - 1;
                        const dotColor = isFirst || isLast ? legColor : '#fff';
                        const dot = L.circleMarker([c[0], c[1]], {
                            radius: isFirst || isLast ? 7 : 5,
                            color: legColor, fillColor: dotColor, fillOpacity: 1, weight: 2
                        }).addTo(map);
                        window._busRouteLines.push(dot);
                    });
                } else if (leg.mode === 'WALK' && leg.stop_coords && leg.stop_coords.length >= 2) {
                    const coords = leg.stop_coords.map(c => [c[0], c[1]]);
                    const line = L.polyline(coords, { color: '#94a3b8', weight: 3, opacity: 0.7, dashArray: '6,6' }).addTo(map);
                    window._busRouteLines.push(line);
                }
            });

            const allBusCoords = selectedJourney.legs
                .filter(l => l.stop_coords)
                .flatMap(l => l.stop_coords.map(c => [c[0], c[1]]));
            if (allBusCoords.length > 0) map.fitBounds(allBusCoords, { padding: [50, 50] });

        } else {
            window._routeLine = L.polyline(
                [[origin.lat, origin.lon], [dest.lat, dest.lon]],
                { color, weight: 5, opacity: 0.85, dashArray: mode === 'WALK' ? '8,8' : null }
            ).addTo(map);
        }

        // 9) Fit bounds
        const allPoints = (gpsPos && origin.isGPS)
            ? [[gpsPos.lat, gpsPos.lon], [origin.lat, origin.lon], [dest.lat, dest.lon]]
            : [[origin.lat, origin.lon], [dest.lat, dest.lon]];
        map.fitBounds(allPoints, { padding: [50, 50] });

        // 10) Pannello "in progress"
        const durationMin = Math.ceil(distanceKm / (mode==='WALK'?5:mode==='BIKE'?15:mode==='SCOOTER'?20:25) * 60);
        const modeEmoji   = MODE_ICONS[mode];

        document.querySelector('.routes-list').innerHTML =
            '<div style="padding:16px 0">'
            + '<div style="background:linear-gradient(135deg,#10b981,#3b82f6);border-radius:20px;padding:20px;color:white;margin-bottom:16px;text-align:center">'
            + `<div style="font-size:36px;margin-bottom:8px">${modeEmoji}</div>`
            + '<div style="font-size:18px;font-weight:800;margin-bottom:4px">Journey in Progress</div>'
            + `<div style="font-size:13px;opacity:0.85">${label}</div>`
            + '<div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:10px;margin-top:16px">'
            + '<div style="background:rgba(255,255,255,0.2);border-radius:12px;padding:10px">'
            + '<div style="font-size:10px;opacity:0.8;font-weight:700;text-transform:uppercase">Distance</div>'
            + `<div style="font-size:16px;font-weight:800">${distanceKm.toFixed(1)} km</div></div>`
            + '<div style="background:rgba(255,255,255,0.2);border-radius:12px;padding:10px">'
            + '<div style="font-size:10px;opacity:0.8;font-weight:700;text-transform:uppercase">ETA</div>'
            + `<div style="font-size:16px;font-weight:800" id="etaCounter">${durationMin} min</div></div>`
            + '<div style="background:rgba(255,255,255,0.2);border-radius:12px;padding:10px">'
            + '<div style="font-size:10px;opacity:0.8;font-weight:700;text-transform:uppercase">Green</div>'
            + `<div style="font-size:16px;font-weight:800">🌱 ${greenIndex}</div></div>`
            + '</div></div>'
            + '<button class="action-btn" onclick="endJourney()" style="width:100%;background:#fef2f2;color:#ef4444;border:1px solid #fee2e2;font-size:14px;padding:13px;margin-bottom:12px">🏁 End Journey</button>'
            + '<div style="text-align:center;font-size:11px;color:var(--text-soft);font-weight:600">📡 Live tracking active</div>'
            + '</div>';

        let remaining = durationMin;
        window._etaInterval = setInterval(() => {
            if (remaining > 0) {
                remaining--;
                const el = document.getElementById('etaCounter');
                if (el) el.textContent = remaining + ' min';
            } else {
                clearInterval(window._etaInterval);
                showToast('🎉 You have arrived!');
            }
        }, 60000);

        showToast(`🚀 Journey started! ${durationMin} min to destination`);

    } catch (err) {
        console.error('startJourney failed:', err);
        showToast('⚠️ Impossibile avviare il percorso', true);
        window._journeyStarting = false;
        if (_startBtn) { _startBtn.disabled = false; _startBtn.textContent = '🚀 Start Journey'; }
    }
}

function endJourney() {
    clearInterval(window._etaInterval);
    selectedJourney = null;
    window._journeyStarting = false;

    // Remove all journey map layers
    ['_routeLine','_routeLineGps','_journeyDestMarker','_journeyOriginMarker','_journeyGpsMarker']
        .forEach(k => { if (window[k]) { map.removeLayer(window[k]); window[k] = null; } });

    if (window._busRouteLines) {
        window._busRouteLines.forEach(l => map.removeLayer(l));
        window._busRouteLines = [];
    }
    // Keep the live GPS dot if we have a real position
    if (userLat) placeUserMarker(userLat, userLon);

    // Restore bus-stop markers
    if (window._stopMarkers) window._stopMarkers.forEach(m => m.addTo(map));

    map.setView([41.4901, 13.8303], 15);

    document.querySelector('.routes-list').innerHTML =
        '<div style="text-align:center;padding:48px 20px;color:var(--text-soft)">'
        + '<div style="font-size:36px;margin-bottom:12px">🌱</div>'
        + '<div style="font-size:14px;font-weight:700;color:var(--text-dark)">Journey completed!</div>'
        + '<div style="font-size:12px;margin-top:6px">Search a new route above</div>'
        + '</div>';

    showToast('🏁 Journey ended — great trip!');
    loadEcoStats();
}

// ── Toast ─────────────────────────────────────────────────────────
function showToast(msg, isError = false) {
    const existing = document.getElementById('toast-notif');
    if (existing) existing.remove();
    const toast = document.createElement('div');
    toast.id = 'toast-notif';
    toast.style.cssText = `
        position:fixed;bottom:24px;left:50%;transform:translateX(-50%);
        background:${isError ? '#ef4444' : '#0f172a'};
        color:white;padding:12px 20px;border-radius:50px;
        font-size:13px;font-weight:700;z-index:9999;
        box-shadow:0 8px 24px rgba(0,0,0,0.2);white-space:nowrap;
    `;
    toast.textContent = msg;
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 3000);
}

// ── Sidebar nav ───────────────────────────────────────────────────
document.querySelectorAll('.sidebar-nav .nav-item').forEach(item => {
    item.addEventListener('click', () => {
        const pane = item.dataset.pane;
        const tab  = item.dataset.tab;
        document.querySelectorAll('.sidebar-nav .nav-item').forEach(n => n.classList.remove('active'));
        item.classList.add('active');
        document.querySelectorAll('.pane').forEach(p => p.classList.remove('active'));
        document.getElementById('pane-' + pane).classList.add('active');
        if (pane === 'profile' && tab) switchProfileTab(tab);
        if (pane === 'map') setTimeout(() => map.invalidateSize(), 50);
    });
});

function showTicketType(type, el) {
    document.querySelectorAll('.type-chip').forEach(c => c.classList.remove('active'));
    el.classList.add('active');
    document.querySelectorAll('.ticket-list').forEach(l => l.classList.remove('active'));
    document.getElementById(type + '-tickets').classList.add('active');
}

function switchProfileTab(tab) {
    document.querySelectorAll('.profile-tab').forEach(t => t.classList.toggle('active', t.dataset.ptab === tab));
    document.querySelectorAll('.ptab-pane').forEach(p => p.classList.remove('active'));
    const el = document.getElementById('ptab-' + tab);
    if (el) el.classList.add('active');
    if (tab === 'history') loadHistory();
    if (tab === 'favorites') loadFavorites();
    if (tab === 'settings') loadPreferences();
}

// Escape a string so it is safe inside a double-quoted HTML attribute.
function escAttr(s) {
    return String(s ?? '')
        .replace(/&/g, '&amp;').replace(/"/g, '&quot;')
        .replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

// One delegated listener handles every star, present or future.
document.addEventListener('click', e => {
    const star = e.target.closest('.fav-star');
    if (!star) return;
    toggleFav(star, star.dataset.mode, star.dataset.origin, star.dataset.dest);
});

async function toggleFav(star, mode, originName, destName) {
    try {
        const r = await apiFetch('/traveller/favorites/toggle', {
            method: 'POST',
            body: JSON.stringify({ mode, originName, destName })
        });
        if (!r.ok) throw new Error('toggle fav failed');
        const result = await r.json();

        star.classList.toggle('starred', result.favorited);
        star.textContent = result.favorited ? '★' : '☆';

        if (document.getElementById('ptab-favorites').classList.contains('active')) {
            loadFavorites();
        }
    } catch (e) {
        console.warn('Could not toggle favourite:', e);
        showToast('Errore nel salvare il preferito', true);
    }
}

function openAI() {
    document.getElementById('aiOverlay').classList.add('open');
    setTimeout(() => document.getElementById('aiInput').focus(), 100);
}

function closeAI(e) {
    if (!e || e.target === document.getElementById('aiOverlay')) {
        document.getElementById('aiOverlay').classList.remove('open');
    }
}

async function logout() {
    const token = localStorage.getItem('omnimove_token');
    if (token) {
        await fetch('/api/v1/auth/logout', {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + token }
        }).catch(() => {});
    }
    localStorage.removeItem('omnimove_token');
    localStorage.removeItem('omnimove_user');
    window.location.href = 'omnimove-login.html';
}

function confirmDeleteAccount() {
    const modal = document.getElementById('deleteModal');
    modal.style.display = 'flex';
}

async function deleteAccount() {
    const btn = document.getElementById('confirmDeleteBtn');
    btn.disabled = true;
    btn.textContent = 'Deleting…';

    try {
        const token = localStorage.getItem('omnimove_token');
        const r = await fetch('/api/v1/auth/account', {
            method: 'DELETE',
            headers: { 'Authorization': 'Bearer ' + token }
        });

        if (r.ok) {
            localStorage.removeItem('omnimove_token');
            localStorage.removeItem('omnimove_user');
            window.location.href = 'omnimove-login.html';
        } else {
            const data = await r.json().catch(() => ({}));
            alert(data.message || 'Could not delete account. Please try again.');
            btn.disabled = false;
            btn.textContent = 'Yes, delete my account';
        }
    } catch (e) {
        alert('Cannot reach server.');
        btn.disabled = false;
        btn.textContent = 'Yes, delete my account';
    }
}

let aiThinking = false;
async function sendAI() {
    if (aiThinking) return;
    const input = document.getElementById('aiInput');
    const msgs  = document.getElementById('aiMessages');
    const text  = input.value.trim();
    if (!text) return;

    const userMsg = document.createElement('div');
    userMsg.className = 'ai-msg user';
    userMsg.textContent = text;
    msgs.appendChild(userMsg);
    input.value = '';
    msgs.scrollTop = msgs.scrollHeight;

    aiThinking = true;
    const waitMsg = document.createElement('div');
    waitMsg.className = 'ai-msg bot';
    waitMsg.textContent = '…';
    msgs.appendChild(waitMsg);
    msgs.scrollTop = msgs.scrollHeight;

    try {
        const r = await apiFetch('/ai/chat', {
            method: 'POST',
            body: JSON.stringify({ message: text, language: 'it' })
        });
        const data = await r.json();
        waitMsg.textContent = data.answer || 'Risposta non disponibile.';
    } catch (e) {
        waitMsg.textContent = 'Errore di connessione al backend. Verifica che OMNIMOVE sia avviato.';
    } finally {
        aiThinking = false;
        msgs.scrollTop = msgs.scrollHeight;
    }
}

// ── Initial load: populate stops (dropdowns + map markers) ─────────
loadStops();
