const CACHE_NAME = 'nexa-v1';
const STATIC_ASSETS = [
    '/',
    '/manifest.json',
];

// Instalar
self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE_NAME).then((cache) => {
            return cache.addAll(STATIC_ASSETS);
        })
    );
    self.skipWaiting();
});

// Activar
self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys().then((keys) => {
            return Promise.all(
                keys
                    .filter((key) => key !== CACHE_NAME)
                    .map((key) => caches.delete(key))
            );
        })
    );
    self.clients.claim();
});

// Fetch con Network First (para API) y Cache First (para assets)
self.addEventListener('fetch', (event) => {
    const { request } = event;
    const url = new URL(request.url);

    // API calls → Network First
    if (url.pathname.startsWith('/api/')) {
        event.respondWith(
            fetch(request)
                .then((response) => {
                    const cloned = response.clone();
                    caches.open(CACHE_NAME).then((cache) => {
                        cache.put(request, cloned);
                    });
                    return response;
                })
                .catch(() => caches.match(request))
        );
        return;
    }

    // Assets → Cache First
    event.respondWith(
        caches.match(request).then((cached) => {
            return cached || fetch(request).then((response) => {
                const cloned = response.clone();
                caches.open(CACHE_NAME).then((cache) => {
                    cache.put(request, cloned);
                });
                return response;
            });
        })
    );
});

// Push notifications
self.addEventListener('push', (event) => {
    const data = event.data?.json() || {};
    const options = {
        body: data.body || 'Tienes una nueva notificación de NEXA',
        icon: '/icons/icon-192.png',
        badge: '/icons/icon-192.png',
        vibrate: [200, 100, 200],
        data: {
            url: data.url || '/',
        },
        actions: [
            { action: 'open', title: 'Abrir NEXA' },
            { action: 'dismiss', title: 'Descartar' },
        ],
    };

    event.waitUntil(
        self.registration.showNotification(data.title || 'NEXA AI', options)
    );
});

// Click en notificación
self.addEventListener('notificationclick', (event) => {
    event.notification.close();

    if (event.action === 'dismiss') return;

    event.waitUntil(
        clients.openWindow(event.notification.data.url || '/')
    );
});
