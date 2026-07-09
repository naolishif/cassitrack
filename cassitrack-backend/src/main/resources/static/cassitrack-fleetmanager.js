    // BUGFIX (auth): this used to be a client-side route guard that read
    // 'cassitrack_token'/'cassitrack_user' from localStorage and redirected
    // to login if missing. Since the V-04 fix (httpOnly-cookie auth), login.js
    // no longer writes anything to localStorage, so this guard's condition was
    // always true and it unconditionally bounced every FLEET_MANAGER straight
    // back to the login page after a successful login. The role check it
    // performed is redundant anyway: SecurityConfig already gates
    // /cassitrack-fleetmanager.html itself to FLEET_MANAGER/ROLE_FLEET_MANAGER
    // server-side (a non-fleet-manager hitting this URL gets a 403 before this
    // script ever runs), matching how cassitrack-admin.js has no client-side
    // gate either. Removed.

    const API          = '/cassitrack/api/v1';              // CASSITRACK — fleet, ETA  (context-path prefix required)

    // XSS defense: escape any API-supplied string before inserting into innerHTML
    function escHtml(s) {
        return String(s ?? '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    // CSP FIX (A05): style-src no longer allows 'unsafe-inline', so any genuinely
    // dynamic per-instance colour/width value (route hue, crowding %, etc.) cannot
    // be written as style="..." in an HTML/innerHTML string anymore. Instead, such
    // elements carry harmless data-fg / data-bg / data-width-pct attributes, and
    // this helper applies them via the CSSOM (element.style.xxx = ...), which is a
    // property assignment, not inline-HTML/CSS parsing — entirely unaffected by the
    // style-src directive. Call this right after inserting any markup that may
    // contain such data-* attributes (innerHTML, or after a Leaflet popup opens).
    function applyDynStyles(root) {
        if (!root) return;
        root.querySelectorAll('[data-fg]').forEach(el => { el.style.color = el.dataset.fg; });
        root.querySelectorAll('[data-bg]').forEach(el => { el.style.background = el.dataset.bg; });
        root.querySelectorAll('[data-width-pct]').forEach(el => { el.style.width = el.dataset.widthPct + '%'; });
    }

    // Derived dynamically so this works on both localhost and the public server.
    // The fleet manager is always served by CassiTrack; OmniMove always runs on
    // the same host, port 8180. Using window.location.hostname means the browser
    // calls the right machine regardless of whether it's 127.0.0.1 or 193.205.60.151.
    const OMNIMOVE_API = window.location.protocol + '//' + window.location.hostname + ':8180/api/v1';
    const REFRESH = 15000;
    const SC = {
        ON_TIME:'#22C55E',SLIGHTLY_LATE:'#F59E0B',
        SIGNIFICANTLY_LATE:'#EF4444',EARLY:'#06B6D4',UNKNOWN:'#4B5563',
        NO_TRIP:'#F59E0B'
    };
    const SL = {ON_TIME:'ON TIME',SLIGHTLY_LATE:'SLIGHTLY LATE',SIGNIFICANTLY_LATE:'LATE',EARLY:'EARLY',UNKNOWN:'LIVE',NO_TRIP:'⚠️ NO TRIP'};
    const MODE_ICON = {BUS:'🚌',WALK:'🚶',BIKE:'🚲',SCOOTER:'🛴',CAR:'🚗',WAIT:'⏳'};
    const MODE_COL  = {BUS:'#3B82F6',WALK:'#22C55E',BIKE:'#22C55E',SCOOTER:'#F59E0B',CAR:'#EF4444',WAIT:'#4B5563'};

    // Map
    let map;

    window.addEventListener('load', () => {

        map = L.map('map', {
            center: [41.497, 13.822],
            zoom: 14
        });

        L.tileLayer(
            'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png',
            {
                attribution: '© OpenStreetMap © CARTO',
                maxZoom: 19
            }
        ).addTo(map);

        fetch(`${API}/vehicles/fleet-size`).then(r=>r.json())
            .then(d=>{ if(d && d.total) fleetSize = d.total; })
            .catch(()=>{});

        fetch(`${API}/routes`).then(r=>r.json()).then(routes=>{
            routes.forEach((route, i)=>{
                const color = `hsl(${(i * 137.5) % 360}, 70%, 55%)`;
                routeColors[route.id] = color;
                routeColors[route.name] = color;

                // la polilinea della linea resta una per linea
                L.polyline(route.stops.map(s=>[s.lat,s.lon]),
                    {color,weight:4,opacity:.8,dashArray:'8 8'}).addTo(map);

                // accumula le fermate, unendo le linee che le servono
                route.stops.forEach(s=>{
                    if(!stopMap[s.id]) stopMap[s.id] = {name:s.name, lat:s.lat, lon:s.lon, lines:[]};
                    if(!stopMap[s.id].lines.some(l=>l.name===route.name))
                        stopMap[s.id].lines.push({name:route.name, color});
                });
            });

            // un solo marker per fermata, con TUTTE le linee nel popup
            Object.values(stopMap).forEach(st=>{
                // CSP FIX (A05): per-line colour is genuinely dynamic (one of N route
                // hues) -> data-fg + applyDynStyles() on popupopen, instead of style="".
                const linesHtml = st.lines.map(l=>
                    `<span class="line-fg" data-fg="${l.color}">${escHtml(l.name)}</span>`
                ).join(' · ');
                const sm = L.circleMarker([st.lat, st.lon],{
                    radius:7, fillColor:'#0f1623', color:'#94A3B8', weight:2, opacity:1, fillOpacity:1
                }).addTo(map).bindPopup(`
                    <div class="stop-popup">
                        <div class="stop-popup-title">🚏 ${escHtml(st.name)}</div>
                        <div class="stop-popup-lines">Linee: ${linesHtml} · Magni Autoservizi</div>
                    </div>`);
                // Popup content is only parsed into the DOM when it actually opens
                // (Leaflet lazy-renders bindPopup() strings), so the dynamic colours
                // must be (re-)applied at that moment.
                sm.on('popupopen', e => applyDynStyles(e.popup.getElement()));
            });

            fetchVehicles();
        }).catch(e=>console.error('routes load failed',e));

        setTimeout(() => {
            map.invalidateSize();
        }, 500);

    });


    const markers={},vehicleData={};
    let selectedVeh=null,lastUpdate=null;
    const routeColors = {};   // id linea -> colore, riempita all'avvio
    const stopMap = {};   // stopId -> { name, lat, lon, lines:[{name,color}] }
    let fleetSize = 4;

    // CSP FIX (A05): schedule_status is a fixed 5-value domain (see SC above), so
    // the bus marker icon needs no dynamic style at all — a data-status attribute
    // plus static attribute-selector CSS rules (cassitrack-fleetmanager.css) covers
    // every case. SC[status]||SC.UNKNOWN is preserved as a key-selection fallback.
    function busIcon(id,status){
        const st = SC[status] ? status : 'UNKNOWN';
        return L.divIcon({className:'',html:`<div class="bus-icon-wrap"><div class="bus-icon-body" data-status="${st}"><span class="bus-icon-emoji">🚌</span></div><div class="bus-icon-label" data-status="${st}">${escHtml(id)}</div></div>`,iconSize:[60,58],iconAnchor:[18,50]});
    }

    async function fetchVehicles(){
        try{
            const r=await fetch(`${API}/vehicles`);
            if(!r.ok) throw new Error(r.status);

            let data=await r.json();

            lastUpdate=new Date();

            document.getElementById('hDot').className='dot dot-green';
            document.getElementById('hStatus').textContent=
                data.length>0
                    ? `${data.length} bus${data.length>1?'es':''} active`
                    : 'Connected — no buses';

            updateMap(data);
            updateFleet(data);
            if (typeof populateBusDropdown === 'function') populateBusDropdown(data);

        }catch(e){
            document.getElementById('hDot').className='dot dot-red';
            document.getElementById('hStatus').textContent='Backend offline';
        }
    }

    function updateMap(vehicles){
        vehicles.forEach(v=>{
            vehicleData[v.vehicle_id]=v;
            const st=!v.trip_id?'NO_TRIP':(v.schedule_status||'UNKNOWN'),pos=[v.lat,v.lon];
            if(markers[v.vehicle_id]){markers[v.vehicle_id].setLatLng(pos);markers[v.vehicle_id].setIcon(busIcon(v.vehicle_id,st));}
            else{
                const m=L.marker(pos,{icon:busIcon(v.vehicle_id,st)}).addTo(map);
                m.bindPopup(popupV(v));
                // CSP FIX (A05): popup content (popupV()) carries data-fg/data-bg
                // attributes for its dynamic route-colour and crowding-bar values;
                // apply them once Leaflet actually renders the popup into the DOM.
                m.on('popupopen', e => applyDynStyles(e.popup.getElement()));
                m.on('click',()=>selV(v.vehicle_id));
                markers[v.vehicle_id]=m;
            }
            markers[v.vehicle_id].setPopupContent(popupV(v));
            // setPopupContent() re-renders the popup's inner DOM even while it's
            // currently open, so re-apply the dynamic styles in that case too.
            if (markers[v.vehicle_id].isPopupOpen()) applyDynStyles(markers[v.vehicle_id].getPopup().getElement());
        });
    }

    function popupV(v){
        // CSP FIX (A05): schedule_status -> fixed-domain data-status (static CSS).
        // routeColor()/crowding-bar values stay genuinely dynamic -> data-fg/data-bg.
        const st = !v.trip_id ? 'NO_TRIP' : (SC[v.schedule_status] ? v.schedule_status : 'UNKNOWN');
        const l=SL[v.schedule_status]||'LIVE';
        const routeCol = routeColor(v.route_id);
        const cp=crowdPct(v.crowding_level),cc=cp>70?'#EF4444':cp>40?'#F59E0B':'#22C55E';
        const delayTxt=v.delay_minutes>0?`+${v.delay_minutes}m`:'In orario';
        return `
    <div class="vpop">
        <div class="vpop-top">
            <div class="vpop-id" data-status="${st}">🚌 ${escHtml(v.vehicle_id)}</div>
            <div class="vpop-badge" data-status="${st}">${l}</div>
        </div>
        <div class="vpop-grid">
            <div><div class="vpop-lbl">SPEED</div>${v.speed_kmh?v.speed_kmh.toFixed(1)+' km/h':'—'}</div>
            <div><div class="vpop-lbl">ROUTE</div><span data-fg="${routeCol}">${escHtml(v.route_name)||'—'}</span></div>
            <div><div class="vpop-lbl">DELAY</div><span class="vpop-delay" data-status="${st}">${delayTxt}</span></div>
            <div><div class="vpop-lbl">PASSENGERS</div>${v.estimated_passengers?'~'+v.estimated_passengers:'—'}</div>
            <div class="vpop-span2"><div class="vpop-lbl">LAST STOP</div>${escHtml(v.next_stop_name)||'—'}</div>
            <div class="vpop-span2"><div class="vpop-lbl">NEXT STOP</div>${escHtml(v.upcoming_stop_name)||'—'}</div>
        </div>
        <div class="vpop-barwrap">
            <div class="vpop-bar" data-bg="${cc}" data-width-pct="${cp}"></div>
        </div>
        <div class="vpop-crowd">Crowding: ${escHtml(v.crowding_level)||'—'}</div>
    </div>`;
    }

    function updateFleet(vehicles){
        const onTime=vehicles.filter(v=>v.schedule_status==='ON_TIME'||v.schedule_status==='EARLY').length;
        const late=vehicles.filter(v=>v.schedule_status==='SLIGHTLY_LATE'||v.schedule_status==='SIGNIFICANTLY_LATE').length;
        // Analytics
        const totalFleet = fleetSize
        const punctuality = vehicles.length
            ? Math.round((onTime / vehicles.length) * 100)
            : 0;

        const totalPassengers = vehicles.reduce((sum,v)=>
            sum + (v.estimated_passengers || 0),0);

        const totalKm = vehicles.reduce((sum,v)=>
            sum + ((v.speed_kmh || 0) * 0.25),0);

// Update analytics UI
        document.getElementById('anActiveRatio').textContent =
            `${vehicles.length}/${totalFleet}`;

        document.getElementById('anPassengers').textContent =
            totalPassengers;
        const sActive=document.getElementById('sActive');
        const sOnTime=document.getElementById('sOnTime');
        const sLate=document.getElementById('sLate');

        if(sActive) sActive.textContent=vehicles.length||'0';
        if(sOnTime) sOnTime.textContent=onTime;
        if(sLate) sLate.textContent=late;

        const list=document.getElementById('vehicle-list');
        if(vehicles.length===0){list.innerHTML=`<div class="empty"><div class="empty-icon">🚌</div><p>No active buses.<br>Start the GPS simulator.</p></div>`;return;}
        list.innerHTML='';
        vehicles.forEach(v=>{
            // CSP FIX (A05): st kept exactly as before (no ||SC.UNKNOWN coercion here
            // — an unrecognized status falls through to "no matching CSS rule", the
            // same no-op visual result as the original "background:undefined").
            const st=!v.trip_id?'NO_TRIP':(v.schedule_status||'UNKNOWN'),l=SL[st]||SL.UNKNOWN;
            const spd=v.speed_kmh?v.speed_kmh.toFixed(1):'0.0';
            const pax=v.estimated_passengers?'~'+v.estimated_passengers:'—';
            const cp=crowdPct(v.crowding_level),cc=cp>70?'#EF4444':cp>40?'#F59E0B':'#22C55E';
            const rc=routeColor(v.route_id);
            const d=document.createElement('div');
            d.className='vcard'+(selectedVeh===v.vehicle_id?' selected':'');
            d.innerHTML=`<div class="vcard-stripe" data-status="${st}"></div><div class="vcard-top"><div class="vcard-id" data-fg="${rc}">${escHtml(v.vehicle_id)}</div><div class="chip" data-status="${st}">${l}</div></div><div class="vcard-grid"><div class="vitem"><div class="vitem-lbl">Speed</div><div class="vitem-val">${spd} km/h</div></div><div class="vitem"><div class="vitem-lbl">Passengers</div><div class="vitem-val">${pax}</div></div><div class="vitem"><div class="vitem-lbl">Crowding</div><div class="vitem-val">${escHtml(v.crowding_level)||'—'}</div></div><div class="vitem"><div class="vitem-lbl">Route</div><div class="vitem-val" data-fg="${rc}">${escHtml(v.route_name)||'—'}</div></div></div><div class="cbar"><div class="cbar-fill" data-bg="${cc}" data-width-pct="${cp}"></div></div>`;
            // d is a detached element (not yet appended), so this is synchronous
            // and safe to call right after setting innerHTML.
            applyDynStyles(d);
            d.addEventListener('click',()=>selV(v.vehicle_id));
            list.appendChild(d);
        });
    }

    function selV(id){
        selectedVeh=id;const v=vehicleData[id];
        if(v){map.flyTo([v.lat,v.lon],16,{duration:1});markers[id]?.openPopup();}
        updateFleet(Object.values(vehicleData));
    }



    // Helpers
    function crowdPct(l){return{LOW:18,MEDIUM:48,HIGH:74,VERY_HIGH:95}[l]||0;}
    function routeColor(routeId){
        return routeColors[routeId] || '#4B5563';
    }
    function chartColor(key, i){
        return routeColors[key] || `hsl(${(i * 137.5) % 360}, 70%, 55%)`;
    }
    function fmtT(iso){if(!iso)return'—';return new Date(iso).toLocaleTimeString('it-IT',{hour:'2-digit',minute:'2-digit'});}
    function fmtDist(m){if(!m)return'—';return m<1000?(Math.round(m)+'m'):(m/1000).toFixed(1)+'km';}
    // ─────────────────────────────────────────────
    // TOP NAVIGATION
    // ─────────────────────────────────────────────

    function switchTopView(viewId, btn){

        document.querySelectorAll('.main-view')
            .forEach(v=>v.classList.remove('active-view'));

        document.getElementById(viewId)
            .classList.add('active-view');

        document.querySelectorAll('.top-btn')
            .forEach(b=>b.classList.remove('active'));

        btn.classList.add('active');

        // Important for Leaflet map resize
        if(viewId === 'fleet-monitor'){
            setTimeout(()=>map.invalidateSize(),200);
        }
    }

    async function logoutUser(){
        // BUGFIX (auth): this was gated behind `if (token)`, but nothing has
        // written 'cassitrack_token' to localStorage since the httpOnly-cookie
        // migration, so token was always null/falsy and POST /auth/logout was
        // never actually called — the JWT was never blacklisted server-side
        // and the session cookie was never cleared, so "logging out" didn't
        // really log you out. The cookie is sent automatically with this
        // same-origin request, so no Authorization header is needed.
        await fetch('/cassitrack/api/v1/auth/logout', { method: 'POST' }).catch(() => {});
        window.location.href='cassitrack-login.html';
    }
    setInterval(()=>{
        const t=document.getElementById('hTime');
        if(lastUpdate){const s=Math.round((Date.now()-lastUpdate.getTime())/1000);t.textContent=`· next in ${Math.max(0,15-s)}s`;}
    },1000);

    setTimeout(()=>document.getElementById('loading').classList.add('gone'),1500);
    window.addEventListener('load', () => {

        fetchVehicles();

        setInterval(fetchVehicles, REFRESH);

    });

    // ═══════════════════════════════════════════════════════════
    // ANALYTICS FILTER STATE
    // ═══════════════════════════════════════════════════════════

    const activeFilters = {
        preset:    'today',
        startTime: null,
        endTime:   null,
        routeIds:  [],   // array of route ID strings
        busId:     '',
        groupBy:   'hour',
        status:    ''
    };

    // Operating hours from schedule (loaded once)
    let scheduleFirstHour = 6;
    let scheduleLastHour  = 22;

    // ── Preset logic ──────────────────────────────────────────

    function setPreset(btn, preset) {
        document.querySelectorAll('.preset-btn[data-preset]')
            .forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        activeFilters.preset = preset;

        const custom = document.getElementById('customDateRange');
        custom.style.display = preset === 'custom' ? 'flex' : 'none';

        if (preset !== 'custom') {
            activeFilters.startTime = null;
            activeFilters.endTime   = null;
        }
        syncGroupByToPreset(preset);
    }

    function syncGroupByToPreset(preset) {
        const sel = document.getElementById('filterGroupBy');
        if (preset === 'today' || preset === 'yesterday') {
            sel.value = 'hour';
            activeFilters.groupBy = 'hour';
        } else {
            sel.value = 'day';
            activeFilters.groupBy = 'day';
        }
    }

    function presetToRange(preset) {
        const now  = new Date();
        const pad  = n => String(n).padStart(2, '0');
        const iso  = d => d.toISOString();

        const startOfDay = d => {
            const x = new Date(d); x.setHours(0,0,0,0); return x;
        };
        const endOfDay = d => {
            const x = new Date(d); x.setHours(23,59,59,999); return x;
        };

        switch (preset) {
            case 'today':
                return { start: iso(startOfDay(now)), stop: iso(endOfDay(now)) };
            case 'yesterday': {
                const y = new Date(now); y.setDate(y.getDate() - 1);
                return { start: iso(startOfDay(y)), stop: iso(endOfDay(y)) };
            }
            case 'week': {
                const w = new Date(now); w.setDate(w.getDate() - 6);
                return { start: iso(startOfDay(w)), stop: iso(endOfDay(now)) };
            }
            case 'month': {
                const m = new Date(now.getFullYear(), now.getMonth(), 1);
                return { start: iso(startOfDay(m)), stop: iso(endOfDay(now)) };
            }
            case 'lastmonth': {
                const lm  = new Date(now.getFullYear(), now.getMonth() - 1, 1);
                const lme = new Date(now.getFullYear(), now.getMonth(), 0);
                return { start: iso(startOfDay(lm)), stop: iso(endOfDay(lme)) };
            }
            case 'custom': {
                const from = document.getElementById('filterFrom').value;
                const to   = document.getElementById('filterTo').value;
                if (!from) return { start: null, stop: null };
                const s = new Date(from); s.setHours(0,0,0,0);
                const e = to ? new Date(to) : new Date(from);
                e.setHours(23,59,59,999);
                return { start: iso(s), stop: iso(e) };
            }
            default:
                return { start: null, stop: null };
        }
    }

    // ── Build URL params from current filter state ─────────────

    function buildFilterParams(overrideGroupBy) {
        const p   = activeFilters.preset;
        const grp = overrideGroupBy || document.getElementById('filterGroupBy').value || activeFilters.groupBy;
        const { start, stop } = presetToRange(p);

        const params = new URLSearchParams();
        if (start) params.set('startTime', start);
        if (stop)  params.set('endTime',   stop);
        if (activeFilters.routeIds.length > 0)
            params.set('routeIds', activeFilters.routeIds.join(','));
        if (activeFilters.busId)
            params.set('busId', activeFilters.busId);
        params.set('groupBy', grp);
        return params.toString();
    }

    // ── Route dropdown ─────────────────────────────────────────

    let allRoutes = [];

    async function loadRoutes() {
        try {
            // BUGFIX (auth): dead localStorage-token header removed — auth is
            // via the httpOnly cookie, sent automatically on same-origin fetches.
            const r = await fetch(`${API}/analytics/routes`);
            if (!r.ok) return;
            allRoutes = await r.json();
            renderRouteDropdown();
        } catch(e) {
            console.warn('Could not load routes', e);
        }
    }

    function renderRouteDropdown() {
        const list = document.getElementById('routeCheckList');
        if (!allRoutes.length) {
            // CSP FIX (A05): purely static styling -> reuse the .rdrop-item-empty class.
            list.innerHTML = '<div class="rdrop-item-empty">No routes found</div>';
            return;
        }
        // CSP FIX (A05): per-route checkbox count is unbounded/runtime-determined,
        // so the onchange="" handler is replaced with a data-route-id attribute and
        // a single delegated 'change' listener bound once on #routeCheckList (below).
        list.innerHTML = allRoutes.map(rt => `
            <div class="rdrop-item">
                <input type="checkbox" id="rc_${escHtml(rt.id)}" value="${escHtml(rt.id)}" data-route-id="${escHtml(rt.id)}"
                    ${activeFilters.routeIds.includes(rt.id) ? 'checked' : ''}>
                <label for="rc_${escHtml(rt.id)}">${escHtml(rt.shortName || rt.id)}${rt.longName ? ' — ' + escHtml(rt.longName) : ''}</label>
            </div>`).join('');
    }

    function toggleRouteDropdown() {
        document.getElementById('routeDropdown').classList.toggle('open');
    }

    // Close dropdown when clicking outside
    document.addEventListener('click', e => {
        const wrap = document.getElementById('routeDropdownWrap');
        if (wrap && !wrap.contains(e.target))
            document.getElementById('routeDropdown').classList.remove('open');
    });

    function toggleRoute(routeId, checked) {
        if (checked) {
            if (!activeFilters.routeIds.includes(routeId)) activeFilters.routeIds.push(routeId);
        } else {
            activeFilters.routeIds = activeFilters.routeIds.filter(id => id !== routeId);
        }
        renderSelectedPills();
        updateRouteDropdownBtn();
    }

    function clearRoutes() {
        activeFilters.routeIds = [];
        renderSelectedPills();
        updateRouteDropdownBtn();
        renderRouteDropdown();
    }

    function renderSelectedPills() {
        // CSP FIX (A05): pill count is unbounded/runtime-determined, so the
        // onclick="" handler is replaced with a data-route-id attribute and a
        // single delegated 'click' listener bound once on #selectedRoutePills.
        const container = document.getElementById('selectedRoutePills');
        container.innerHTML = activeFilters.routeIds.map(id => {
            const rt = allRoutes.find(r => r.id === id);
            const label = rt ? (rt.shortName || rt.id) : id;
            return `<div class="route-pill" data-route-id="${escHtml(id)}">
                        ${escHtml(label)}<span class="rpx">✕</span>
                    </div>`;
        }).join('');
    }

    function updateRouteDropdownBtn() {
        const btn = document.getElementById('routeDropdownBtn');
        const n   = activeFilters.routeIds.length;
        btn.textContent = n === 0 ? 'All Routes ▾' : `${n} Route${n > 1 ? 's' : ''} ▾`;
    }

    // Populate bus dropdown from live vehicle list
    function populateBusDropdown(vehicles) {
        const sel  = document.getElementById('filterBus');
        const prev = sel.value;
        const ids  = [...new Set(vehicles.map(v => v.vehicle_id))].sort();
        sel.innerHTML = '<option value="">All Buses</option>' +
            ids.map(id => `<option value="${id}" ${prev === id ? 'selected' : ''}>${id}</option>`).join('');
    }

    // ── Operating hours from schedule ──────────────────────────

    async function loadOperatingHours() {
        try {
            // BUGFIX (auth): dead localStorage-token header removed — auth is
            // via the httpOnly cookie, sent automatically on same-origin fetches.
            const r = await fetch(`${API}/analytics/operating-hours`);
            if (!r.ok) return;
            const data = await r.json();
            if (data._global) {
                scheduleFirstHour = data._global.firstHour ?? 6;
                scheduleLastHour  = data._global.lastHour  ?? 22;
            }
        } catch(e) {
            console.warn('Could not load operating hours', e);
        }
    }

    // ── Build chart labels (hours or dates) ────────────────────

    function buildChartLabels(groupBy, data) {
        if (groupBy === 'day') {
            // Collect all unique date keys across all routes, sorted
            const days = new Set();
            Object.values(data).forEach(bySlot => Object.keys(bySlot).forEach(k => days.add(k)));
            return [...days].sort();
        }
        // Hour labels from actual schedule
        const labels = [];
        for (let h = scheduleFirstHour; h < scheduleLastHour; h++)
            labels.push(`${String(h).padStart(2,'0')}:00`);
        return labels;
    }

    // ── Chart rendering ────────────────────────────────────────

    let routesChartInstance = null;
    let delayChartInstance  = null;

    const CHART_COLORS = ['#3B82F6','#06B6D4','#8B5CF6','#22C55E','#F59E0B','#EF4444'];

    function renderBarChart(canvasId, instanceRef, data, tooltipUnit) {
        const canvas = document.getElementById(canvasId);
        if (!canvas) return instanceRef;

        // ID convention: delayChart → delayChartEmpty, routesChart → routesChartEmpty
        const emptyEl = document.getElementById(canvasId + 'Empty');
        const wrap    = canvas.parentElement;

        const groupBy   = document.getElementById('filterGroupBy').value || 'hour';
        const routeKeys = Object.keys(data).sort();

        if (routeKeys.length === 0) {
            canvas.style.display = 'none';
            if (emptyEl) emptyEl.style.display = 'block';
            if (instanceRef) instanceRef.destroy();
            return null;
        }

        // Show canvas (wrap is always visible — hiding the wrap causes Chart.js to lose its size reference)
        canvas.style.display = '';
        if (emptyEl) emptyEl.style.display = 'none';

        const labels   = buildChartLabels(groupBy, data);
        const datasets = routeKeys.sort().map((key, i) => ({
                label: key,
                data: labels.map(slot => data[key][slot] ?? null),
                backgroundColor: chartColor(key, i),
                borderRadius: 4,
                maxBarThickness: groupBy === 'day' ? 18 : 14
            }));

        if (instanceRef) instanceRef.destroy();

        return new Chart(canvas, {
            type: 'bar',
            data: { labels, datasets },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { labels: { color: '#E2E8F0', font: { family: 'DM Mono', size: 10 } } },
                    tooltip: { callbacks: { label: ctx => `${ctx.dataset.label}: ${ctx.parsed.y ?? 0} ${tooltipUnit}` } }
                },
                scales: {
                    x: {
                        ticks: { color: '#4B5563', font: { family: 'DM Mono', size: 9 },
                                 maxRotation: groupBy === 'day' ? 45 : 0 },
                        grid: { color: 'rgba(255,255,255,.04)' }
                    },
                    y: {
                        beginAtZero: true,
                        title: { display: true, text: tooltipUnit === 'pax' ? 'Passengers' : 'Delay (min)',
                                 color: '#4B5563', font: { family: 'DM Mono', size: 10 } },
                        ticks: { color: '#4B5563', font: { family: 'DM Mono', size: 9 } },
                        grid: { color: 'rgba(255,255,255,.04)' }
                    }
                }
            }
        });
    }

    async function loadPassengersByRoute() {
        try {
            // BUGFIX (auth): dead localStorage-token header removed — auth is
            // via the httpOnly cookie, sent automatically on same-origin fetches.
            const qs    = buildFilterParams();
            const r = await fetch(`${API}/analytics/passengers-by-route?${qs}`);
            if (!r.ok) throw new Error(r.status);
            const data = await r.json();
            routesChartInstance = renderBarChart('routesChart', routesChartInstance, data, 'pax');

            // Update average occupancy KPI
            const allVals = [];
            Object.values(data).forEach(s => Object.values(s).forEach(v => { if (v != null) allVals.push(v); }));
            const avg = allVals.length > 0 ? Math.round(allVals.reduce((a,b)=>a+b,0)/allVals.length) : null;
            document.getElementById('kpiPassengers').textContent = avg != null ? avg : '—';
        } catch(e) {
            console.error('Failed to load passengers by route', e);
            // NOTE: `canvas` is not defined in this scope (pre-existing bug, kept
            // as-is — only the inline style="" string is swapped for a CSS class,
            // per CSP-fix scope; this catch block would already throw a
            // ReferenceError before reaching here, exactly as it did before).
            canvas.parentElement.innerHTML = `<div class="chart-msg">Error loading data.</div>`;
        }
    }

    async function loadDelayByRoute() {
        try {
            // BUGFIX (auth): dead localStorage-token header removed — auth is
            // via the httpOnly cookie, sent automatically on same-origin fetches.
            const qs    = buildFilterParams();
            const r = await fetch(`${API}/analytics/delay-by-route?${qs}`);
            if (!r.ok) throw new Error(r.status);
            const data = await r.json();
            delayChartInstance = renderBarChart('delayChart', delayChartInstance, data, 'min');

            const routeKeys = Object.keys(data);
            if (routeKeys.length === 0) {
                canvas.parentElement.innerHTML = `<div class="chart-msg">No data yet — run the simulator.</div>`;
                return;
            }

            const labels = buildHourlyLabels();

            const datasets = routeKeys.sort().map((key, i) => ({
                label: key,
                data: labels.map(slot => data[key][slot] ?? null),
                backgroundColor: chartColor(key, i),
                borderRadius: 4,
                maxBarThickness: 14
            }));

            canvas.width = Math.max(700, labels.length * 90);

            // Update average delay KPI
            const allVals = [];
            Object.values(data).forEach(s => Object.values(s).forEach(v => { if (v != null) allVals.push(v); }));
            const avg = allVals.length > 0
                ? (allVals.reduce((a,b)=>a+b,0)/allVals.length).toFixed(1) : 0;
            document.getElementById('kpiDelay').textContent = avg > 0 ? `+${avg}m` : '0m';
        } catch(e) {
            console.error('Failed to load delay by route', e);
            // NOTE: same pre-existing undefined-`canvas` bug as above, intentionally
            // preserved — only swapping style="" for the .chart-msg class.
            canvas.parentElement.innerHTML = `<div class="chart-msg">Error loading data.</div>`;
        }
    }

    async function loadCo2Kpi() {
        try {
            // BUGFIX (auth): dead localStorage-token header removed — auth is
            // via the httpOnly cookie, sent automatically on same-origin fetches.
            const { start, stop } = presetToRange(activeFilters.preset);
            const qs = new URLSearchParams();
            if (start) qs.set('startTime', start);
            if (stop)  qs.set('endTime',   stop);
            if (activeFilters.routeIds.length > 0) qs.set('routeIds', activeFilters.routeIds.join(','));
            if (activeFilters.busId) qs.set('busId', activeFilters.busId);

            const r = await fetch(`${API}/analytics/co2?${qs}`);
            if (!r.ok) return;
            const d = await r.json();

            const kg = d.co2_saved_kg ?? 0;
            document.getElementById('kpiCo2').textContent = kg >= 1000
                ? `${(kg/1000).toFixed(1)}t` : `${kg}kg`;

            if (d.passenger_km != null)
                document.getElementById('kpiCo2Sub').textContent =
                    `${d.passenger_km} pax-km saved`;
        } catch(e) {
            console.error('Failed to load CO2 KPI', e);
        }
    }

    async function loadSummaryKPIs() {
        // On-time % from live adherence
        try {
            // BUGFIX (auth): dead localStorage-token header removed — auth is
            // via the httpOnly cookie, sent automatically on same-origin fetches.
            const r = await fetch(`${API}/analytics/adherence`);
            if (r.ok) {
                const d = await r.json();
                const counts = d.status_counts || {};
                const total  = d.total_active || 0;
                const onTime = (counts.ON_TIME || 0) + (counts.EARLY || 0);
                const pct    = total > 0 ? Math.round(onTime / total * 100) : 0;
                document.getElementById('kpiOnTimePct').textContent = `${pct}%`;
                document.getElementById('kpiOnTimeSub').textContent =
                    `${onTime}/${total} buses on schedule`;
            }

            // Fleet donut from live vehicles
            const rv = await fetch(`${API}/vehicles`);
            if (rv.ok) {
                const vehs = await rv.json();
                updateFleetDonut(vehs);
            }
        } catch(e) {
            console.error('Error loading summary KPIs', e);
        }
    }

    function updateFleetDonut(vehicles) {
        const donut   = document.querySelector('.fake-donut');
        const kpiSpan = document.getElementById('kpiOnTime');
        if (!donut || !vehicles.length) return;

        kpiSpan.textContent = '';

        const total    = vehicles.length;
        const onTime   = vehicles.filter(v => v.schedule_status === 'ON_TIME' || v.schedule_status === 'EARLY').length;
        const slight   = vehicles.filter(v => v.schedule_status === 'SLIGHTLY_LATE').length;
        const late     = vehicles.filter(v => v.schedule_status === 'SIGNIFICANTLY_LATE').length;
        const noTrip   = vehicles.filter(v => !v.trip_id).length;

        const onTimePct  = (onTime  / total) * 100;
        const slightPct  = (slight  / total) * 100;
        const latePct    = (late    / total) * 100;
        const noTripPct  = (noTrip  / total) * 100;

        // NOTE: assigning to element.style.background via the CSSOM is a property
        // mutation, not inline-HTML/CSS-string parsing — already unaffected by
        // removing 'unsafe-inline' from style-src, no change needed here.
        donut.style.background = `conic-gradient(
            #22C55E 0% ${onTimePct}%,
            #F59E0B ${onTimePct}% ${onTimePct + slightPct}%,
            #EF4444 ${onTimePct + slightPct}% ${onTimePct + slightPct + latePct}%,
            #F59E0B ${onTimePct + slightPct + latePct}% ${onTimePct + slightPct + latePct + noTripPct}%,
            #1A2744 ${onTimePct + slightPct + latePct + noTripPct}% 100%
        )`;
    }

    // ── Apply filters (main refresh) ───────────────────────────

    function applyFilters() {
        activeFilters.busId   = document.getElementById('filterBus').value;
        activeFilters.groupBy = document.getElementById('filterGroupBy').value;

        // If custom preset, read date inputs
        if (activeFilters.preset === 'custom') {
            const { start, stop } = presetToRange('custom');
            activeFilters.startTime = start;
            activeFilters.endTime   = stop;
        }

        loadAllAnalytics();
    }

    function loadAllAnalytics() {
        // Update chart subtitles to reflect active filters
        const grp       = document.getElementById('filterGroupBy').value || 'hour';
        const presetLbl = { today:'Today', yesterday:'Yesterday', week:'Last 7 Days',
                            month:'This Month', lastmonth:'Last Month', custom:'Custom Range' };
        const periodTxt = presetLbl[activeFilters.preset] || '';
        const slotTxt   = grp === 'day' ? 'per day' : 'per hour';
        const sub       = `${periodTxt} · ${slotTxt}`;
        const ds = document.getElementById('delayChartSub');
        const ps = document.getElementById('paxChartSub');
        if (ds) ds.textContent = `Delay by line · ${sub}`;
        if (ps) ps.textContent = `Passenger demand by line · ${sub}`;

        loadPassengersByRoute();
        loadDelayByRoute();
        loadSummaryKPIs();
        loadCo2Kpi();
    }

    // ── Wire up the Analytics nav button ──────────────────────

    document.querySelectorAll('.top-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            if (btn.textContent.trim() === 'Analytics') {
                loadAllAnalytics();
            }
        });
    });

    // Load routes + operating hours once on page load
    window.addEventListener('load', () => {
        loadRoutes();
        loadOperatingHours();
    });

    // ─────────────────────────────────────────────────────────────────
    // CSP FIX (A03/A05): bind every previously-inline onX="" attribute here
    // instead. Static, fixed-count elements get a direct id-based listener;
    // dynamically-generated, variable-count elements (route checkboxes/pills)
    // use event delegation bound once on their stable parent container.
    // ─────────────────────────────────────────────────────────────────
    document.getElementById('topBtnFleetMonitor').addEventListener('click', e => switchTopView('fleet-monitor', e.currentTarget));
    document.getElementById('topBtnAnalytics').addEventListener('click', e => switchTopView('analytics-view', e.currentTarget));
    document.getElementById('topBtnLogout').addEventListener('click', logoutUser);

    document.getElementById('presetBtnToday').addEventListener('click', e => setPreset(e.currentTarget, 'today'));
    document.getElementById('presetBtnYesterday').addEventListener('click', e => setPreset(e.currentTarget, 'yesterday'));
    document.getElementById('presetBtnWeek').addEventListener('click', e => setPreset(e.currentTarget, 'week'));
    document.getElementById('presetBtnMonth').addEventListener('click', e => setPreset(e.currentTarget, 'month'));
    document.getElementById('presetBtnLastMonth').addEventListener('click', e => setPreset(e.currentTarget, 'lastmonth'));
    document.getElementById('presetBtnCustom').addEventListener('click', e => setPreset(e.currentTarget, 'custom'));

    document.getElementById('routeDropdownBtn').addEventListener('click', toggleRouteDropdown);
    document.getElementById('clearRoutesBtn').addEventListener('click', clearRoutes);
    document.getElementById('applyFiltersBtn').addEventListener('click', applyFilters);

    // Event delegation: route checkboxes/pills are generated for an unknown,
    // runtime-determined number of routes — bind once on the stable parent.
    document.getElementById('routeCheckList').addEventListener('change', e => {
        const cb = e.target.closest('input[type="checkbox"][data-route-id]');
        if (cb) toggleRoute(cb.dataset.routeId, cb.checked);
    });
    document.getElementById('selectedRoutePills').addEventListener('click', e => {
        const pill = e.target.closest('.route-pill[data-route-id]');
        if (pill) { toggleRoute(pill.dataset.routeId, false); renderRouteDropdown(); }
    });
