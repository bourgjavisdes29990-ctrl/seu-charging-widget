// 东南大学九龙湖校区电动车充电桩 PWA 核心业务逻辑
const STATIONS_DATA = [
  {
    id: "taoyuan",
    name: "桃园生活区车棚",
    shortName: "桃园车棚",
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
let currentFilter = 'all';
let deferredInstallPrompt = null;

// 计算经纬度球面距离 (米)
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
      prediction = "夜间充电饱和，明晨7点陆续空出";
    } else if (hour >= 6 && hour < 9) {
      baseOccupancy = 0.70;
      prediction = "早课出发潮，插座正在逐步释放";
    } else if (hour >= 9 && hour < 17) {
      baseOccupancy = 0.40;
      prediction = "白天余位充足，随时可以充电";
    } else if (hour >= 17 && hour < 19) {
      baseOccupancy = 0.65;
      prediction = "晚饭高峰，晚自习前仍有空闲";
    } else {
      baseOccupancy = 0.85;
      const nextH = (hour + 1) % 24;
      const nextM = (minute + 15) % 60;
      prediction = `预计 ${String(nextH).padStart(2, '0')}:${String(nextM).padStart(2, '0')} 空出 ${Math.max(2, Math.floor(station.total / 25))} 个插座`;
    }
  } else {
    if (hour >= 8 && hour < 18) {
      baseOccupancy = 0.88;
      prediction = `下课潮预计 ${String(hour).padStart(2, '0')}:45 空出 ${Math.max(3, Math.floor(station.total / 20))} 个`;
    } else if (hour >= 18 && hour < 21) {
      baseOccupancy = 0.60;
      prediction = "晚自习时段，尚有部分插座空闲";
    } else {
      baseOccupancy = 0.15;
      prediction = "非上课时段，插座充裕随时可用";
    }
  }

  // 微调波动
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

  return {
    ...station,
    free,
    fastFree,
    slowFree,
    status,
    statusText,
    badgeClass,
    prediction,
    distanceMeters: dist
  };
}

// 渲染主列表与看板
function renderApp() {
  const liveStations = STATIONS_DATA.map(computeLiveStationStatus);

  // 全校统计
  const totalSlots = liveStations.reduce((sum, s) => sum + s.total, 0);
  const totalFree = liveStations.reduce((sum, s) => sum + s.free, 0);
  const freePercent = Math.round((totalFree / totalSlots) * 100);

  document.getElementById('total-free-count').innerText = totalFree;
  document.getElementById('total-capacity-count').innerText = `/ ${totalSlots} 插座`;
  document.getElementById('total-free-percent').innerText = `${freePercent}%`;
  document.getElementById('total-progress-bar').style.width = `${freePercent}%`;

  // 过滤与排序
  let filtered = [...liveStations];

  if (currentFilter === 'dorm') {
    filtered = filtered.filter(s => s.zone === 'dorm');
  } else if (currentFilter === 'academic') {
    filtered = filtered.filter(s => s.zone === 'academic');
  } else if (currentFilter === 'ample') {
    filtered = filtered.filter(s => s.status === 'ample');
  } else if (currentFilter === 'nearest') {
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

    card.innerHTML = `
      <div class="card-top">
        <div class="card-title-wrap">
          <div class="station-name">${s.name} ${distHtml}</div>
          <div class="station-subtitle">${s.desc}</div>
        </div>
        <span class="badge ${s.badgeClass}">${s.statusText}</span>
      </div>
      <div class="card-main-stats">
        <span class="card-free-num">${s.free}</span>
        <span class="card-total-desc">/ ${s.total} 空闲插座</span>
      </div>
      <div class="card-sub-info">
        <span class="card-breakdown">⚡ 快充 ${s.fastFree}/${s.fast} · 慢充 ${s.slowFree}/${s.slow}</span>
        <span class="card-prediction">${s.prediction}</span>
      </div>
    `;
    listContainer.appendChild(card);
  });
}

// 模态弹窗查看车棚详情与导航
function openStationModal(station) {
  const modal = document.getElementById('station-modal');
  document.getElementById('modal-title').innerText = station.name;
  document.getElementById('modal-badge').innerText = station.statusText;
  document.getElementById('modal-badge').className = `badge ${station.badgeClass}`;
  document.getElementById('modal-free-count').innerText = station.free;
  document.getElementById('modal-total-count').innerText = `/ ${station.total} 空闲插座`;
  document.getElementById('modal-desc').innerText = station.desc;
  document.getElementById('modal-prediction').innerText = station.prediction;
  document.getElementById('modal-fast').innerText = `快充插座：${station.fastFree} 个空闲 / 共 ${station.fast} 个`;
  document.getElementById('modal-slow').innerText = `慢充插座：${station.slowFree} 个空闲 / 共 ${station.slow} 个`;

  // 路线导航跳转 (高德/百度 Web 坐标导航)
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

// 定位获取
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
      // 自动高亮并切换到离我最近 tab
      setActiveFilter('nearest');
    },
    (err) => {
      locBtn.style.opacity = '1';
      alert("定位获取失败，请确保开启手机 GPS 与浏览器定位权限");
    },
    { enableHighAccuracy: true, timeout: 6000 }
  );
}

function setActiveFilter(filter) {
  currentFilter = filter;
  document.querySelectorAll('.filter-tab').forEach(tab => {
    tab.classList.toggle('active', tab.dataset.filter === filter);
  });
  renderApp();
}

// 初始化交互事件
document.addEventListener('DOMContentLoaded', () => {
  renderApp();

  // 刷新按钮
  document.getElementById('btn-refresh').addEventListener('click', () => {
    renderApp();
  });

  // 定位按钮
  document.getElementById('btn-locate').addEventListener('click', triggerGeolocation);

  // Filter 切换
  document.querySelectorAll('.filter-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      setActiveFilter(tab.dataset.filter);
    });
  });

  // 模态弹窗点击遮罩关闭
  const modal = document.getElementById('station-modal');
  modal.addEventListener('click', (e) => {
    if (e.target === modal) {
      closeStationModal();
    }
  });

  // PWA 安装引导
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

  // 注册 Service Worker (Network-First)
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./sw.js').then((reg) => {
      console.log('SEU Charging PWA Service Worker Registered:', reg.scope);
    }).catch((err) => {
      console.warn('SW registration failed:', err);
    });
  }
});
