/*
 * Service worker of the urlaubsverwaltung (LI fork).
 *
 * - pages (navigations) are always loaded from the network and are never cached,
 *   only if the network is not available the offline page is shown
 * - static assets (hashed js/css, fonts, images, favicons) are served cache first
 * - everything else (api, login, logout, non GET requests, ...) is not touched
 */
const CACHE_VERSION = "__UV_CACHE_VERSION__";
const CACHE_PREFIX = "uv-";
const STATIC_CACHE = `${CACHE_PREFIX}static-${CACHE_VERSION}`;
const OFFLINE_CACHE = `${CACHE_PREFIX}offline-${CACHE_VERSION}`;
const OFFLINE_URL = new URL("offline", self.registration.scope).pathname;
const SCOPE_PATH = new URL(self.registration.scope).pathname;
const OFFLINE_ICON_URL = SCOPE_PATH + "favicons/android-chrome-192x192.png";

const STATIC_PATHS = ["assets/", "css/", "fonts/", "images/", "favicons/"].map((path) => SCOPE_PATH + path);
const BYPASS_PATHS = ["api", "oauth2", "login", "logout", "actuator", "sw.js"].map((path) => SCOPE_PATH + path);

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches
      .open(OFFLINE_CACHE)
      .then((cache) =>
        cache.addAll([
          new Request(OFFLINE_URL, { cache: "reload", credentials: "omit" }),
          new Request(OFFLINE_ICON_URL, { credentials: "omit" }),
        ]),
      )
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    (async () => {
      const keys = await caches.keys();
      await Promise.all(
        keys
          .filter((key) => key.startsWith(CACHE_PREFIX) && key !== STATIC_CACHE && key !== OFFLINE_CACHE)
          .map((key) => caches.delete(key)),
      );
      // no navigation preload: it does not work reliably with the redirects of the oidc login
      if (self.registration.navigationPreload) {
        await self.registration.navigationPreload.disable();
      }
      await self.clients.claim();
    })(),
  );
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  if (request.method !== "GET") {
    return;
  }

  const url = new URL(request.url);
  if (url.origin !== self.location.origin || BYPASS_PATHS.some((path) => url.pathname.startsWith(path))) {
    return;
  }

  if (request.mode === "navigate") {
    event.respondWith(networkOnlyWithOfflineFallback(request));
  } else if (STATIC_PATHS.some((path) => url.pathname.startsWith(path))) {
    event.respondWith(cacheFirst(request));
  }
});

async function networkOnlyWithOfflineFallback(request) {
  try {
    return await fetch(request);
  } catch {
    const cache = await caches.open(OFFLINE_CACHE);
    const offlineResponse = await cache.match(OFFLINE_URL);
    return offlineResponse || Response.error();
  }
}

async function cacheFirst(request) {
  const cache = await caches.open(STATIC_CACHE);
  const cachedResponse = await caches.match(request);
  if (cachedResponse) {
    return cachedResponse;
  }

  const response = await fetch(request);
  if (response.ok && response.type === "basic") {
    await cache.put(request, response.clone());
  }
  return response;
}
