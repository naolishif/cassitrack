/**
 * CASSITRACK Service Worker
 *
 * What this does:
 * 1. Caches all app files on first install
 * 2. Serves them from cache on next visit
 *    (app loads instantly, even offline)
 * 3. Tries to fetch fresh data from network
 *    for API calls (live bus positions)
 * 4. Falls back to cache if network fails
 *
 * Analogy from Telecom:
 * This is like a local base station cache.
 * Instead of routing every request to the
 * core network, the BSS serves common data
 * locally. Much faster, works when network
 * is degraded.
 */

const CACHE_NAME = 'cassitrack-v1';

// Files to cache on install
// These make the app work offline
const STATIC_ASSETS = [
  './',
  './index.html',
  './style.css',
  './app.js',
  './manifest.json',
  'https://unpkg.com/leaflet@1.9.4/dist/leaflet.css',
  'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js',
  'https://fonts.googleapis.com/css2?family=DM+Mono:wght@400;500&family=Syne:wght@600;700;800&display=swap',
];

// ── Install: cache all static assets ─────────────────────────
self.addEventListener('install', event => {
  console.log('[SW] Installing CASSITRACK service worker');
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => {
        console.log('[SW] Caching static assets');
        // Cache what we can — ignore failures
        // (some CDN URLs may have CORS restrictions)
        return Promise.allSettled(
          STATIC_ASSETS.map(url =>
            cache.add(url).catch(e =>
              console.warn('[SW] Could not cache:', url, e)
            )
          )
        );
      })
      .then(() => self.skipWaiting())
  );
});

// ── Activate: clean up old caches ────────────────────────────
self.addEventListener('activate', event => {
  console.log('[SW] Activating service worker');
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(
        keys
          .filter(key => key !== CACHE_NAME)
          .map(key => {
            console.log('[SW] Deleting old cache:', key);
            return caches.delete(key);
          })
      )
    ).then(() => self.clients.claim())
  );
});

// ── Fetch: serve from cache or network ───────────────────────
self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);

  // API calls (live bus data) → network first
  // If network fails, show cached data
  if (url.hostname === 'localhost' ||
      url.pathname.startsWith('/api/')) {
    event.respondWith(networkFirst(event.request));
    return;
  }

  // Static assets → cache first
  // (fast load, background update)
  event.respondWith(cacheFirst(event.request));
});

// Network first strategy — for live API data
async function networkFirst(request) {
  try {
    const response = await fetch(request);
    // Cache successful responses
    if (response.ok) {
      const cache = await caches.open(CACHE_NAME);
      cache.put(request, response.clone());
    }
    return response;
  } catch (error) {
    // Network failed — try cache
    const cached = await caches.match(request);
    if (cached) return cached;
    // Nothing in cache either
    return new Response(
      JSON.stringify({ error: 'Offline', cached: false }),
      { headers: { 'Content-Type': 'application/json' } }
    );
  }
}

// Cache first strategy — for static files
async function cacheFirst(request) {
  const cached = await caches.match(request);
  if (cached) return cached;
  // Not in cache — fetch from network
  try {
    const response = await fetch(request);
    if (response.ok) {
      const cache = await caches.open(CACHE_NAME);
      cache.put(request, response.clone());
    }
    return response;
  } catch (error) {
    return new Response('Offline', { status: 503 });
  }
}