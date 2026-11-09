/* Block Boom web game — offline shell for the /play/ PWA. */
const CACHE = "blockboom-play-v1.1.0-r1";
const CORE = [
  "/play/",
  "/play/manifest.webmanifest",
  "/assets/icons/icon-192.png",
  "/assets/icons/icon-512.png",
  "/assets/icons/icon-maskable-512.png",
  "/assets/icons/apple-touch-icon.png",
  "/assets/fonts/vazirmatn_regular.ttf",
  "/assets/fonts/vazirmatn_medium.ttf",
  "/assets/fonts/vazirmatn_semibold.ttf",
  "/assets/fonts/vazirmatn_bold.ttf",
  "/assets/fonts/vazirmatn_black.ttf"
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE)
      .then((cache) => Promise.allSettled(CORE.map((url) => cache.add(url))))
      .catch(() => undefined)
  );
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((key) => key.startsWith("blockboom-play-") && key !== CACHE).map((key) => caches.delete(key)))
    )
  );
  self.clients.claim();
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  if (request.method !== "GET") return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  if (url.pathname.startsWith("/api/") || url.pathname.includes("/api.")) return;
  if (url.pathname.startsWith("/download/")) return;

  if (request.mode === "navigate") {
    event.respondWith(
      fetch(request)
        .then((response) => {
          if (response && response.ok) {
            const copy = response.clone();
            caches.open(CACHE).then((cache) => cache.put("/play/", copy)).catch(() => undefined);
          }
          return response;
        })
        .catch(async () => (await caches.match("/play/")) || (await caches.match(request)) || Response.error())
    );
    return;
  }

  event.respondWith(
    caches.match(request).then((cached) => {
      if (cached) return cached;
      return fetch(request)
        .then((response) => {
          if (response && response.ok && (url.pathname.startsWith("/_next/") || url.pathname.startsWith("/assets/"))) {
            const copy = response.clone();
            caches.open(CACHE).then((cache) => cache.put(request, copy)).catch(() => undefined);
          }
          return response;
        })
        .catch(() => cached || Response.error());
    })
  );
});
