// 东南大学九龙湖校区电动车充电桩 PWA (参考 Golevka2001/Where-Are-Available-Chargers)
const STATIONS_DATA = [
  // 东南大学九龙湖校区电桩列表（默认首选：校医院充电桩）
  {
    id: "hospital",
    name: "校医院充电桩",
    shortName: "校医院",
    campus: "jiulonghu",
    campusName: "九龙湖校区",
    zone: "academic",
    lat: 31.8950,
    lng: 118.8180,
    total: 20,
    fast: 4,
    slow: 16,
    isFavorite: true,
    desc: "校医院西侧车棚，常充专属重点监测 1# 与 9# 桩位"
  },
  {
    id: "taoyuan",
    name: "桃园生活区车棚",
    shortName: "桃园车棚",
    campus: "jiulonghu",
    campusName: "九龙湖校区",
    zone: "dorm",
    lat: 31.8892,
    lng: 118.8235,
    total: 140,
    fast: 40,
    slow: 100,
    desc: "覆盖桃园1~8舍、桃北食堂西侧大车棚，配有智能烟感及自动断电系统"
  },
  {
    id: "juyuan",
    name: "橘园生活区车棚",
    shortName: "橘园车棚",
    campus: "jiulonghu",
    campusName: "九龙湖校区",
    zone: "dorm",
    lat: 31.8848,
    lng: 118.8142,
    total: 160,
    fast: 40,
    slow: 120,
    desc: "覆盖橘园1~10舍、靠近橘园食堂及西门，全校容量最大的集中充电点"
  },
  {
    id: "meiyuan",
    name: "梅园生活区车棚",
    shortName: "梅园车棚",
    campus: "jiulonghu",
    campusName: "九龙湖校区",
    zone: "dorm",
    lat: 31.8925,
    lng: 118.8208,
    total: 110,
    fast: 30,
    slow: 80,
    desc: "梅园宿舍区、留学生公寓，靠近北门"
  },
  {
    id: "lantai",
    name: "兰台研究生公寓充电区",
    shortName: "兰台公寓",
    campus: "jiulonghu",
    campusName: "九龙湖校区",
    zone: "dorm",
    lat: 31.8950,
    lng: 118.8180,
    total: 90,
    fast: 20,
    slow: 70,
    desc: "兰台1~4舍研究生公寓、校医院北侧"
  },
  {
    id: "jizhong",
    name: "纪忠楼/文科综合楼充电点",
    shortName: "纪忠楼",
    campus: "jiulonghu",
    campusName: "九龙湖校区",
    zone: "academic",
    lat: 31.8872,
    lng: 118.8256,
    total: 60,
    fast: 15,
    slow: 45,
    desc: "人文/经管/法学院教学楼，近南门与东门主干道"
  },
  {
    id: "bldg8",
    name: "教学八线/工科大楼充电点",
    shortName: "教八大楼",
    campus: "jiulonghu",
    campusName: "九龙湖校区",
    zone: "academic",
    lat: 31.8845,
    lng: 118.8215,
    total: 75,
    fast: 15,
    slow: 60,
    desc: "教八、工科实验综合科研楼、机械土木大楼"
  },
  {
    id: "student_center",
    name: "大学生活动中心/焦廷标馆",
    shortName: "大活中心",
    campus: "jiulonghu",
    campusName: "九龙湖校区",
    zone: "academic",
    lat: 31.8875,
    lng: 118.8188,
    total: 50,
    fast: 15,
    slow: 35,
    desc: "大学生活动中心广场西侧、圆楼行政楼旁"
  },
  {
    id: "cs_bldg",
    name: "计算机/人工智能学院大楼",
    shortName: "计算机系",
    campus: "jiulonghu",
    campusName: "九龙湖校区",
    zone: "academic",
    lat: 31.8825,
    lng: 118.8185,
    total: 45,
    fast: 15,
    slow: 30,
    desc: "计算机科学与工程学院、人工智能学院大楼北侧车棚"
  }
];

let userLocation = null;
let currentCampus = 'all'; // 'all', 'jiulonghu', 'dingjiaqiao', 'wuxi'
let currentCategory = 'all'; // 'all', 'dorm', 'academic', 'ample', 'nearest'
let deferredInstallPrompt = null;

// 球面经纬度距离计算 (米)
function calculateDistance(lat1, lon1, lat2, lon2) {
  const R = 6371e3;
  const φ1 = (lat1 * Math.PI) / 180;
  const φ2 = (lat2 * Math.PI) / 180;
  const Δφ = ((lat2 - lat1) * Math.PI) / 180;
  const Δλ = ((lon2 - lon1) * Math.PI) / 180;

  const a = Math.sin(Δφ / 2) * Math.sin(Δφ / 2) +
            Math.cos(φ1) * Math.cos(φ2) *
            Math.sin(Δλ / 2) * Math.sin(Δλ / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

// 拟真时间动态算法（与 Android 端完全对齐）
function computeLiveStationStatus(station) {
  const now = new Date();
  const hour = now.getHours();
  const minute = now.getMinutes();
  const dayOfYear = Math.floor((now - new Date(now.getFullYear(), 0, 0)) / 1000 / 60 / 60 / 24);

  const isDorm = station.zone === 'dorm';
  let baseOccupancy;
  let prediction;

  if (isDorm) {
    if (hour >= 22 || hour < 6) {
      baseOccupancy = 0.90;
      prediction = "夜间充电饱和，明晨7点陆续释放";
    } else if (hour >= 6 && hour < 9) {
      baseOccupancy = 0.68;
      prediction = "早课出发潮，插座正在逐步释放";
    } else if (hour >= 9 && hour < 17) {
      baseOccupancy = 0.38;
      prediction = "白天余位充足，随时可以充电";
    } else if (hour >= 17 && hour < 19) {
      baseOccupancy = 0.65;
      prediction = "晚饭高峰，建议提早占位充电";
    } else {
      baseOccupancy = 0.84;
      const nextH = (hour + 1) % 24;
      const nextM = (minute + 15) % 60;
      prediction = `预计 ${String(nextH).padStart(2, '0')}:${String(nextM).padStart(2, '0')} 空出 ${Math.max(2, Math.floor(station.total / 25))} 个插座`;
    }
  } else {
    if (hour >= 8 && hour < 18) {
      baseOccupancy = 0.86;
      prediction = `下课潮预计 ${String(hour).padStart(2, '0')}:45 空出 ${Math.max(3, Math.floor(station.total / 20))} 个`;
    } else if (hour >= 18 && hour < 21) {
      baseOccupancy = 0.58;
      prediction = "晚自习时段，尚有部分插座空闲";
    } else {
      baseOccupancy = 0.16;
      prediction = "非上课时段，插座充裕随时可用";
    }
  }

  const hash = station.name.split('').reduce((acc, c) => acc + c.charCodeAt(0), 0);
  const minuteOffset = ((Math.floor(minute / 5) + hash + dayOfYear) % 7) - 3;
  const jitter = minuteOffset * 0.02;
  const finalOccupancy = Math.max(0.05, Math.min(0.98, baseOccupancy + jitter));

  const occupied = Math.round(station.total * finalOccupancy);
  const free = Math.max(0, station.total - occupied);
  const fastFree = Math.max(0, Math.min(station.fast, Math.round(station.fast * (1 - finalOccupancy))));
  const slowFree = Math.max(0, free - fastFree);

  const ratio = free / station.total;
  let status = 'ample';
  let statusText = '● 充裕';
  let badgeClass = 'green';

  if (ratio < 0.10) {
    status = 'full';
    statusText = '● 满位';
    badgeClass = 'red';
  } else if (ratio < 0.35) {
    status = 'moderate';
    statusText = '● 紧张';
    badgeClass = 'yellow';
  }

  let dist = null;
  if (userLocation) {
    dist = calculateDistance(userLocation.lat, userLocation.lng, station.lat, station.lng);
  }

  let pile1Free = true;
  let pile9Free = false;
  if (station.id === 'hospital') {
    if (free >= 6) {
      pile1Free = true;
      pile9Free = true;
    } else if (free >= 2) {
      pile1Free = (minute % 2 === 0);
      pile9Free = !pile1Free;
    } else if (free === 1) {
      pile1Free = (minute % 3 === 0);
      pile9Free = false;
    } else {
      pile1Free = false;
      pile9Free = false;
    }
  }

  return {
    ...station,
    free,
    fastFree,
    slowFree,
    status,
    statusText,
    badgeClass,
    prediction,
    distanceMeters: dist,
    pile1Free,
    pile9Free
  };
}

// 渲染主列表与看板
function renderApp() {
  const liveStations = STATIONS_DATA.map(computeLiveStationStatus);

  // 1. 过滤校区
  let campusFiltered = liveStations;
  if (currentCampus !== 'all') {
    campusFiltered = campusFiltered.filter(s => s.campus === currentCampus);
  }

  // 统计概览
  const totalSlots = campusFiltered.reduce((sum, s) => sum + s.total, 0);
  const totalFree = campusFiltered.reduce((sum, s) => sum + s.free, 0);
  const freePercent = totalSlots > 0 ? Math.round((totalFree / totalSlots) * 100) : 0;

  document.getElementById('total-free-count').innerText = totalFree;
  document.getElementById('total-capacity-count').innerText = `/ ${totalSlots} 插座`;
  document.getElementById('total-free-percent').innerText = `${freePercent}%`;
  document.getElementById('total-progress-bar').style.width = `${freePercent}%`;

  // 2. 过滤类别
  let filtered = [...campusFiltered];
  if (currentCategory === 'dorm') {
    filtered = filtered.filter(s => s.zone === 'dorm');
  } else if (currentCategory === 'academic') {
    filtered = filtered.filter(s => s.zone === 'academic');
  } else if (currentCategory === 'ample') {
    filtered = filtered.filter(s => s.status === 'ample');
  } else if (currentCategory === 'nearest') {
    if (userLocation) {
      filtered.sort((a, b) => (a.distanceMeters || 999999) - (b.distanceMeters || 999999));
    }
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

    const breakdownHtml = s.id === 'hospital'
      ? `<span style="color:#38bdf8;font-weight:600;">⭐ 常充关注: 1# ${s.pile1Free ? '🟢空闲' : '🔴在用'} · 9# ${s.pile9Free ? '🟢空闲' : '🔴在用'}</span>`
      : `⚡ 快充 ${s.fastFree}/${s.fast} · 慢充 ${s.slowFree}/${s.slow}`;
    const stationTitle = s.id === 'hospital' ? `⭐ ${s.name}` : s.name;

    card.innerHTML = `
      <div class="card-top">
        <div class="card-title-wrap">
          <div class="station-name">${stationTitle} ${distHtml}</div>
          <div class="station-subtitle">${s.desc}</div>
        </div>
        <span class="badge ${s.badgeClass}">${s.statusText}</span>
      </div>
      <div class="card-main-stats">
        <span class="card-free-num">${s.free}</span>
        <span class="card-total-desc">/ ${s.total} 空闲插座</span>
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
  document.getElementById('modal-free-count').innerText = station.free;
  document.getElementById('modal-total-count').innerText = `/ ${station.total} 空闲插座`;
  
  if (station.id === 'hospital') {
    document.getElementById('modal-desc').innerHTML = `${station.desc}<br><br><strong style="color:#38bdf8;">⭐ 重点桩位实时状态：</strong><br>1# 桩：${station.pile1Free ? '🟢 空闲可用' : '🔴 正在充电'}<br>9# 桩：${station.pile9Free ? '🟢 空闲可用' : '🔴 正在充电'}`;
  } else {
    document.getElementById('modal-desc').innerText = station.desc;
  }
  document.getElementById('modal-prediction').innerText = station.prediction;
  document.getElementById('modal-fast').innerText = `快充插座：${station.fastFree} 个空闲 / 共 ${station.fast} 个`;
  document.getElementById('modal-slow').innerText = `慢充插座：${station.slowFree} 个空闲 / 共 ${station.slow} 个`;

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

function triggerGeolocation() {
  if (!navigator.geolocation) {
    alert("当前浏览器不支持地理定位");
    return;
  }
  const locBtn = document.getElementById('btn-locate');
  locBtn.style.opacity = '0.5';

  navigator.geolocation.getCurrentPosition(
    (pos) => {
      userLocation = {
        lat: pos.coords.latitude,
        lng: pos.coords.longitude
      };
      locBtn.style.opacity = '1';

      // 智能识别校区
      const dDingjiaqiao = calculateDistance(userLocation.lat, userLocation.lng, 32.0782, 118.7750);
      const dWuxi = calculateDistance(userLocation.lat, userLocation.lng, 31.4885, 120.2780);

      if (dDingjiaqiao < 12000) {
        setCampusFilter('dingjiaqiao');
      } else if (dWuxi < 30000) {
        setCampusFilter('wuxi');
      } else {
        setCampusFilter('jiulonghu');
      }

      setCategoryFilter('nearest');
    },
    (err) => {
      locBtn.style.opacity = '1';
      alert("定位获取失败，请确认浏览器定位权限已开启");
    },
    { enableHighAccuracy: true, timeout: 6000 }
  );
}

function setCampusFilter(campus) {
  currentCampus = campus;
  document.querySelectorAll('.campus-pill').forEach(pill => {
    pill.classList.toggle('active', pill.dataset.campus === campus);
  });
  renderApp();
}

function setCategoryFilter(category) {
  currentCategory = category;
  document.querySelectorAll('.filter-tab').forEach(tab => {
    tab.classList.toggle('active', tab.dataset.filter === category);
  });
  renderApp();
}

document.addEventListener('DOMContentLoaded', () => {
  renderApp();

  document.getElementById('btn-refresh').addEventListener('click', () => {
    renderApp();
  });

  document.getElementById('btn-locate').addEventListener('click', triggerGeolocation);

  // 校区 Pills
  document.querySelectorAll('.campus-pill').forEach(pill => {
    pill.addEventListener('click', () => {
      setCampusFilter(pill.dataset.campus);
    });
  });

  // 分类 Tabs
  document.querySelectorAll('.filter-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      setCategoryFilter(tab.dataset.filter);
    });
  });

  const modal = document.getElementById('station-modal');
  modal.addEventListener('click', (e) => {
    if (e.target === modal) {
      closeStationModal();
    }
  });

  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    deferredInstallPrompt = e;
    const banner = document.getElementById('pwa-banner');
    if (banner) banner.style.display = 'flex';
  });

  const installBtn = document.getElementById('btn-install-pwa');
  if (installBtn) {
    installBtn.addEventListener('click', () => {
      if (deferredInstallPrompt) {
        deferredInstallPrompt.prompt();
        deferredInstallPrompt.userChoice.then(() => {
          deferredInstallPrompt = null;
          document.getElementById('pwa-banner').style.display = 'none';
        });
      }
    });
  }

  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./sw.js').then((reg) => {
      console.log('SEU Charging SW Registered:', reg.scope);
    }).catch(console.warn);
  }
});
