// 东南大学九龙湖校区电动车充电桩 PWA
// 数据来源：Where-Are-Available-Chargers 官方 JSON API
//   https://chargers.dpdns.org/api/get_status/jiulonghu
// 联网时拉取实时数据；离线/失败时回退到下方 STATIC_FALLBACK 快照。

const STATUS_API = 'https://chargers.dpdns.org/api/get_status/jiulonghu';

// 静态兜底快照（坐标/总数为官方值，空闲数仅为最后已知值，联网后会被覆盖）
const STATIC_FALLBACK = [
  { id: "hospital", name: "校医院", shortName: "校医院", campus: "jiulonghu",
    zone: "dorm", lat: 31.892883, lng: 118.82438, total: 90, isFavorite: true,
    pile1FreeCount: 3, pile9FreeCount: 8, pile1FreePorts: "2, 3, 6", pile9FreePorts: "2, 3, 4, 5, 6, 7, 8, 9",
    desc: "全场共9台桩(90孔)，常充关注1#与9#桩(各10孔，合计20孔)。" },
  { id: "east_north", name: "东门北侧", shortName: "东门北侧", campus: "jiulonghu",
    zone: "academic", lat: 31.887383, lng: 118.82984, total: 320, isFavorite: false, desc: "近东门北侧主干道，全校最大集中充电区" },
  { id: "east_south", name: "东门南侧", shortName: "东门南侧", campus: "jiulonghu",
    zone: "academic", lat: 31.886993, lng: 118.830055, total: 20, isFavorite: false, desc: "东门南侧车棚(37#、38#)" },
  { id: "north_east", name: "北门东侧", shortName: "北门东侧", campus: "jiulonghu",
    zone: "academic", lat: 31.893595, lng: 118.8246, total: 180, isFavorite: false, desc: "北门东侧车棚，多台桩位" },
  { id: "north_delivery", name: "北门外卖柜旁", shortName: "外卖柜旁", campus: "jiulonghu",
    zone: "academic", lat: 31.887253, lng: 118.82091, total: 20, isFavorite: false, desc: "北门外卖柜单台桩(104#、105#)" },
  { id: "north_west", name: "北门西侧", shortName: "北门西侧", campus: "jiulonghu",
    zone: "academic", lat: 31.893429, lng: 118.82249, total: 50, isFavorite: false, desc: "北门西侧车棚" },
  { id: "south_east", name: "南门外东侧", shortName: "南门外东", campus: "jiulonghu",
    zone: "academic", lat: 31.881042, lng: 118.819916, total: 20, isFavorite: false, desc: "南门外东侧(43#、44#桩)" },
  { id: "south_west", name: "南门外西侧", shortName: "南门外西", campus: "jiulonghu",
    zone: "academic", lat: 31.880817, lng: 118.82094, total: 30, isFavorite: false, desc: "南门外西侧(40#、41#、42#桩)" },
  { id: "juyuan_north", name: "橘园北侧", shortName: "橘园北侧", campus: "jiulonghu",
    zone: "dorm", lat: 31.885979, lng: 118.81205, total: 130, isFavorite: false, desc: "橘园宿舍区北侧车棚，夜间充电集中" },
  { id: "qingjiao", name: "青教公寓", shortName: "青教公寓", campus: "jiulonghu",
    zone: "dorm", lat: 31.893145, lng: 118.8271, total: 100, isFavorite: false, desc: "青年教师公寓车棚" },
];

let liveStations = STATIC_FALLBACK.map(cloneFallback);
let userLocation = null;
let currentCategory = 'all';
let lastSyncTime = null;
let deferredInstallPrompt = null;

function cloneFallback(s) {
  return {
    ...s,
    free: 0,
    pile1FreeCount: s.pile1FreeCount || 0,
    pile9FreeCount: s.pile9FreeCount || 0,
    pile1FreePorts: s.pile1FreePorts || "",
    pile9FreePorts: s.pile9FreePorts || "",
    pilesRemaining: "",
    prediction: s.desc || "",
  };
}

// 球面经纬度距离计算 (米)
function calculateDistance(lat1, lon1, lat2, lon2) {
  const R = 6371e3;
  const φ1 = (lat1 * Math.PI) / 180;
  const φ2 = (lat2 * Math.PI) / 180;
  const Δφ = ((lat2 - lat1) * Math.PI) / 180;
  const Δλ = ((lon2 - lon1) * Math.PI) / 180;
  const a = Math.sin(Δφ / 2) * Math.sin(Δφ / 2) +
            Math.cos(φ1) * Math.cos(φ2) * Math.sin(Δλ / 2) * Math.sin(Δλ / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

// 从官方 JSON 响应映射为本地站点列表
function applyApiData(api) {
  if (!api || api.code !== 200 || !api.status || !Array.isArray(api.status.stations)) {
    return false;
  }
  const byName = new Map();
  for (const st of api.status.stations) {
    byName.set(st.name, st);
  }
  let matched = 0;
  liveStations = STATIC_FALLBACK.map(fb => {
    const s = cloneFallback(fb);
    const st = byName.get(fb.name);
    if (!st) return s;
    matched++;
    s.total = st.total_count;
    s.free = st.available_count;
    s.lat = st.lat || fb.lat;
    s.lng = st.lng || fb.lng;
    if (st.chargers && Array.isArray(st.chargers)) {
      const parts = [];
      for (const ch of st.chargers) {
        parts.push(`${ch.name}:${ch.available_count}`);
        if (fb.isFavorite) {
          if (ch.name === '1#') {
            s.pile1FreeCount = ch.available_count;
            s.pile1FreePorts = freePortsFromSockets(ch.sockets);
          } else if (ch.name === '9#') {
            s.pile9FreeCount = ch.available_count;
            s.pile9FreePorts = freePortsFromSockets(ch.sockets);
          }
        }
      }
      s.pilesRemaining = parts.length ? `余量: ${parts.join(', ')}` : '';
    }
    if (fb.isFavorite) {
      s.prediction = `校医院全场${st.chargers ? st.chargers.length : 9}台桩共${s.total}孔 · 实时余 ${s.free} 可用`;
    } else {
      s.prediction = s.total > 0 && s.free > s.total / 3 ? '插座充足，随到随充' : '余量紧张，建议留意空孔';
    }
    return s;
  });
  if (api.last_update_time) lastSyncTime = new Date(api.last_update_time);
  return matched > 0;
}

function freePortsFromSockets(sockets) {
  if (!Array.isArray(sockets)) return '';
  return sockets.map((v, i) => (v === 1 ? i + 1 : null)).filter(Boolean).join(', ');
}

function computeStatus(station) {
  const ratio = station.total > 0 ? station.free / station.total : 0;
  let status = 'ample';
  let statusText = '● 充裕';
  let badgeClass = 'green';
  if (ratio < 0.10) {
    status = 'full'; statusText = '● 满位'; badgeClass = 'red';
  } else if (ratio < 0.35) {
    status = 'moderate'; statusText = '● 紧张'; badgeClass = 'yellow';
  }
  let dist = null;
  if (userLocation) {
    dist = calculateDistance(userLocation.lat, userLocation.lng, station.lat, station.lng);
  }
  return { ...station, status, statusText, badgeClass, distanceMeters: dist,
    favoritePilesFreeTotal: (station.pile1FreeCount || 0) + (station.pile9FreeCount || 0) };
}

// 渲染主列表与看板
function renderApp() {
  const live = liveStations.map(computeStatus);
  const totalSlots = live.reduce((sum, s) => sum + s.total, 0);
  const totalFree = live.reduce((sum, s) => sum + s.free, 0);
  const freePercent = totalSlots > 0 ? Math.round((totalFree / totalSlots) * 100) : 0;

  document.getElementById('total-free-count').innerText = totalFree;
  document.getElementById('total-capacity-count').innerText = `/ ${totalSlots} 插座`;
  document.getElementById('total-free-percent').innerText = `${freePercent}%`;
  document.getElementById('total-progress-bar').style.width = `${freePercent}%`;

  const syncTag = document.querySelector('.hero-header span[style]');
  if (syncTag && lastSyncTime) {
    const hh = String(lastSyncTime.getHours()).padStart(2, '0');
    const mm = String(lastSyncTime.getMinutes()).padStart(2, '0');
    syncTag.textContent = `${hh}:${mm} 更新`;
  }

  let filtered = [...live];
  if (currentCategory === 'hospital') {
    filtered = filtered.filter(s => s.id === 'hospital');
  } else if (currentCategory === 'dorm') {
    filtered = filtered.filter(s => s.zone === 'dorm');
  } else if (currentCategory === 'academic') {
    filtered = filtered.filter(s => s.zone === 'academic');
  } else if (currentCategory === 'ample') {
    filtered = filtered.filter(s => s.status === 'ample');
  } else if (currentCategory === 'nearest' && userLocation) {
    filtered.sort((a, b) => (a.distanceMeters || 999999) - (b.distanceMeters || 999999));
  }

  const listContainer = document.getElementById('stations-container');
  listContainer.innerHTML = '';

  filtered.forEach(s => {
    const card = document.createElement('div');
    card.className = 'station-card';
    card.onclick = () => openStationModal(s);

    let distHtml = '';
    if (s.distanceMeters !== null) {
      const d = Math.round(s.distanceMeters);
      distHtml = `<span style="color:#38bdf8;margin-left:4px;">· 距你 ${d < 1000 ? d + 'm' : (d / 1000).toFixed(1) + 'km'}</span>`;
    }

    const isHospital = s.id === 'hospital';
    const breakdownHtml = isHospital
      ? `<span style="color:#38bdf8;font-weight:600;">⭐ 1#桩: ${s.pile1FreeCount}/10空闲 · 9#桩: ${s.pile9FreeCount}/10空闲</span>`
      : `<span style="color:#cbd5e1;">${s.pilesRemaining}</span>`;
    const stationTitle = isHospital ? `⭐ ${s.name}` : s.name;
    const freeNum = isHospital ? s.favoritePilesFreeTotal : s.free;
    const totalDesc = isHospital ? `个可用 / 1#&9#共20孔` : `个可用 / 共 ${s.total} 孔`;

    card.innerHTML = `
      <div class="card-top">
        <div class="card-title-wrap">
          <div class="station-name">${stationTitle} ${distHtml}</div>
          <div class="station-subtitle">${s.desc}</div>
        </div>
        <span class="badge ${s.badgeClass}">${s.statusText}</span>
      </div>
      <div class="card-main-stats">
        <span class="card-free-num">${freeNum}</span>
        <span class="card-total-desc">${totalDesc}</span>
      </div>
      <div class="card-sub-info">
        <span class="card-breakdown">${breakdownHtml}</span>
        <span class="card-prediction">${s.prediction}</span>
      </div>
    `;
    listContainer.appendChild(card);
  });
}

function openStationModal(station) {
  const modal = document.getElementById('station-modal');
  document.getElementById('modal-title').innerText = station.id === 'hospital' ? `⭐ ${station.name}` : station.name;
  document.getElementById('modal-badge').innerText = station.statusText;
  document.getElementById('modal-badge').className = `badge ${station.badgeClass}`;

  if (station.id === 'hospital') {
    document.getElementById('modal-free-count').innerText = station.favoritePilesFreeTotal;
    document.getElementById('modal-total-count').innerText = `个可用 / 1#&9#共20孔`;
    document.getElementById('modal-desc').innerHTML = `${station.desc}<br><br><strong style="color:#38bdf8;">⭐ 重点桩位孔位状态：</strong><br>1# 桩：${station.pile1FreeCount}/10 空闲${station.pile1FreePorts ? `（孔 ${station.pile1FreePorts} 可用）` : ''}<br>9# 桩：${station.pile9FreeCount}/10 空闲${station.pile9FreePorts ? `（孔 ${station.pile9FreePorts} 可用）` : ''}<br><br><span style="color:#94a3b8;font-size:0.8rem;">校医院全场共 9 台桩，90 个插孔，全场空闲 ${station.free} 孔</span>`;
    document.getElementById('modal-fast').innerText = `各桩孔位：${station.pilesRemaining}`;
    document.getElementById('modal-slow').innerText = `规格：标准 220V 智能两轮电动车充电插座`;
  } else {
    document.getElementById('modal-free-count').innerText = station.free;
    document.getElementById('modal-total-count').innerText = `个可用 / 共 ${station.total} 孔`;
    document.getElementById('modal-desc').innerText = station.desc;
    document.getElementById('modal-fast').innerText = `各桩孔位：${station.pilesRemaining}`;
    document.getElementById('modal-slow').innerText = `规格：标准 220V 智能两轮电动车充电插座`;
  }
  document.getElementById('modal-prediction').innerText = station.prediction;

  const navBtn = document.getElementById('btn-navigate');
  navBtn.onclick = () => {
    const navUrl = `https://uri.amap.com/marker?position=${station.lng},${station.lat}&name=${encodeURIComponent('东南大学-' + station.name)}`;
    window.open(navUrl, '_blank');
  };

  modal.classList.add('active');
}

function closeStationModal() {
  document.getElementById('station-modal').classList.remove('active');
}

// 拉取真实数据
async function fetchStatus() {
  try {
    const res = await fetch(STATUS_API, { cache: 'no-store' });
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const data = await res.json();
    if (applyApiData(data)) {
      renderApp();
    }
  } catch (err) {
    console.warn('拉取实时数据失败，使用快照:', err.message);
    // 保持快照数据并渲染
    renderApp();
  }
}

// 自动定位
function requestUserLocation() {
  const locateBtn = document.getElementById('btn-locate');
  locateBtn.classList.add('loading');

  if (!navigator.geolocation) {
    alert('当前浏览器环境不支持获取地理位置');
    locateBtn.classList.remove('loading');
    return;
  }

  navigator.geolocation.getCurrentPosition(
    pos => {
      userLocation = { lat: pos.coords.latitude, lng: pos.coords.longitude };
      locateBtn.classList.remove('loading');
      renderApp();
    },
    err => {
      console.warn('获取位置受阻:', err.message);
      locateBtn.classList.remove('loading');
      userLocation = { lat: 31.8872, lng: 118.8190 };
      renderApp();
    },
    { enableHighAccuracy: false, timeout: 5000, maximumAge: 60000 }
  );
}

// 注册事件
document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('.filter-tab').forEach(tab => {
    tab.addEventListener('click', e => {
      document.querySelectorAll('.filter-tab').forEach(t => t.classList.remove('active'));
      e.target.classList.add('active');
      currentCategory = e.target.getAttribute('data-filter');
      if (currentCategory === 'nearest' && !userLocation) {
        requestUserLocation();
      } else {
        renderApp();
      }
    });
  });

  document.getElementById('btn-locate').addEventListener('click', requestUserLocation);
  document.getElementById('btn-refresh').addEventListener('click', () => {
    const rfBtn = document.getElementById('btn-refresh');
    rfBtn.classList.add('loading');
    fetchStatus().finally(() => {
      rfBtn.classList.remove('loading');
    });
  });

  const modalOverlay = document.getElementById('station-modal');
  modalOverlay.addEventListener('click', e => {
    if (e.target === modalOverlay) closeStationModal();
  });

  window.addEventListener('beforeinstallprompt', e => {
    e.preventDefault();
    deferredInstallPrompt = e;
    const pwaBanner = document.getElementById('pwa-banner');
    if (pwaBanner) pwaBanner.style.display = 'flex';
  });

  const installBtn = document.getElementById('btn-install-pwa');
  if (installBtn) {
    installBtn.addEventListener('click', async () => {
      if (deferredInstallPrompt) {
        deferredInstallPrompt.prompt();
        const { outcome } = await deferredInstallPrompt.userChoice;
        if (outcome === 'accepted') {
          document.getElementById('pwa-banner').style.display = 'none';
        }
        deferredInstallPrompt = null;
      }
    });
  }

  renderApp();
  fetchStatus();
  setInterval(fetchStatus, 60000);
});
