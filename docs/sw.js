// Service Worker: Network-First Cache Strategy
// 确保任何线上更新立即对手机生效，杜绝旧缓存卡住问题
const CACHE_VERSION = 'seu-charging-v1.0.0';
const CACHE_NAME = `seu-charging-${CACHE_VERSION}`;

const PRECACHE_ASSETS = [
  './',
  './index.html',
  './style.css',
  './app.js',
  './manifest.json',
  './icons/icon-192.svg',
  './icons/icon-512.svg'
];

// 1. Install: 预缓存基础离线资产，并立即接管当前页面
self.addEventListener('install', (event) => {
  self.skipWaiting();
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(PRECACHE_ASSETS).catch((err) => {
        console.warn('Pre-cache assets warning:', err);
      });
    })
  );
});

// 2. Activate: 清理旧版本缓存，保证缓存不过期累积
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys.map((key) => {
          if (key !== CACHE_NAME) {
            console.log('Purging obsolete cache:', key);
            return caches.delete(key);
          }
        })
      );
    }).then(() => self.clients.claim())
  );
});

// 3. Fetch: 严格采用 Network-First（网络优先）策略
// 每次优先发起真实网络请求；成功后更新本地缓存；仅在离线/弱网断连时降级使用本地缓存
self.addEventListener('fetch', (event) => {
  // 只处理 GET 请求
  if (event.request.method !== 'GET') return;

  const url = new URL(event.request.url);

  // 跨域或第三方扩展请求直接放行
  if (url.origin !== self.location.origin) {
    return;
  }

  event.respondWith(
    fetch(event.request)
      .then((networkResponse) => {
        // 请求成功：复制一份写入缓存备用，并直接返回最新鲜的数据
        if (networkResponse && networkResponse.status === 200) {
          const responseToCache = networkResponse.clone();
          caches.open(CACHE_NAME).then((cache) => {
            cache.put(event.request, responseToCache);
          });
        }
        return networkResponse;
      })
      .catch(() => {
        // 网络失败（手机断网或离线）：回退至本地缓存
        return caches.match(event.request).then((cachedResponse) => {
          if (cachedResponse) {
            return cachedResponse;
          }
          // 如果导航请求断网且没有单页缓存，返回根页面
          if (event.request.mode === 'navigate') {
            return caches.match('./index.html');
          }
          return new Response('Network offline and no cache available', {
            status: 503,
            statusText: 'Service Unavailable',
            headers: new Headers({ 'Content-Type': 'text/plain; charset=utf-8' })
          });
        });
      })
  );
});
