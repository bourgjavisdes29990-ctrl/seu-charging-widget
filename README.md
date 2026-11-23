# 东南大学校内电动车充电桩小组件 (Android Widget + PWA)

> **集成 [Golevka2001/Where-Are-Available-Chargers](https://github.com/Golevka2001/Where-Are-Available-Chargers) 官方 JSON API**
> 为东南大学**九龙湖校区**量身打造的轻量原生手机桌面卡片（4×2）与免安装 PWA 网页看板。
> 数据直接来自官方实时接口 `https://chargers.dpdns.org/api/get_status/jiulonghu`，**无任何伪造仿真**。

---

## 📱 一、产品形态与核心特性

### 1. 轻量原生 Android 桌面卡片 (AppWidget 4×2)
- **默认锁定校医院**：卡片永久默认展示你常用的**校医院充电桩**，并单独高亮 **1# 桩与 9# 桩**（各 10 孔，共 20 孔）的实时空闲数与空闲孔位号。
- **真实实时数据**：通过官方 JSON API 拉取每个充电站（总插座 / 空闲数）以及每台桩号的实时空闲插座，更新时间显示真实同步时刻；离线时回退到最近一次成功同步的缓存。
- **零后台常驻、零跑电**：`updatePeriodMillis="0"`，仅在手机亮屏解锁（`ACTION_USER_PRESENT`）或用户主动点击按钮时被动计算更新。
- **底部操作栏**：`🔄 换车棚`（轮换车棚，转一圈自动回校医院）、`📍 离我最近` / `⭐ 回校医院`、`⚡ 刷新`；右上角 `全校看板 🌐` 一键打开官方网页。
- **45 分钟防跳变保护期**：手动切换车棚后维持 45 分钟锁定，避免校区边界移动时频繁跳变。

### 2. 免安装 PWA 网页看板 (GitHub Pages)
- 同样接入官方 JSON API，实时展示九龙湖校区 10 个真实充电站与校医院 1# / 9# 重点桩位。
- 支持筛选（校医院 / 全部 / 宿舍区 / 教学科研区 / 余位充裕 / 离我最近）与高德一键导航。

### 3. GitHub Actions 云端全自动打包与部署
- 本地**无需安装 Android Studio / JDK / SDK**；push 到 GitHub 后云端自动编译出 APK，并自动部署 PWA 到 GitHub Pages。

---

## 🗺️ 二、九龙湖校区电桩数据覆盖（10 处真实站点）

| 站点 | 总插座 | 说明 |
| :--- | :---: | :--- |
| **校医院** ⭐ | 90 | 9 台桩；重点常充 1# 与 9# 桩（各 10 孔） |
| 东门北侧 | 320 | 全校最大集中充电区 |
| 东门南侧 | 20 | 37#、38# 桩 |
| 北门东侧 | 180 | 多台桩位 |
| 北门外卖柜旁 | 20 | 104#、105# 桩 |
| 北门西侧 | 50 | — |
| 南门外东侧 | 20 | 43#、44# 桩 |
| 南门外西侧 | 30 | 40#、41#、42# 桩 |
| 橘园北侧 | 130 | 宿舍区夜间充电集中 |
| 青教公寓 | 100 | — |

> 总插座与空闲数均为官方 API 提供的实时值；上表总数为当前 API 基准，会随官方数据变化。

---

## ⚡ 三、与 Where-Are-Available-Chargers 的数据对接

- **官方 JSON API**：`ChargingDataManager` 异步请求
  `https://chargers.dpdns.org/api/get_status/jiulonghu`，响应包含：
  - 每个充电站：`name` / `total_count` / `available_count` / `lat` / `lng` / `addr`；
  - 每台桩：`name`（如 `1#`、`9#`）/ `available_count` / `sockets[]`（逐孔 0=占用 / 1=空闲 / 2=故障）。
- **精确到孔位**：校医院 1# 与 9# 桩的空闲孔位号直接由 `sockets[]` 解析得出。
- **离线兜底**：最近一次成功同步的 JSON 原样缓存到本地文件，断网时回退，卡片永远显示真实数据（而非随机仿真数）。
- **网页直达**：卡片一键直达 `https://chargers.dpdns.org/status` 原项目看板。

---

## 🚀 四、快速部署与使用指南

### 1. 推送到自己的 GitHub 仓库
```bash
git branch -M main
git remote add origin https://github.com/<你的用户名>/seu-charging-widget.git
git push -u origin main
```

### 2. 下载 Android 安装包 (APK)
1. 推送代码后，打开你的 GitHub 仓库，点击顶部 **Actions** 标签；
2. 点击最新的 **Build SEU Charging Widget APK** 流水线运行记录；
3. 在页面底部的 **Artifacts** 区域，点击下载 `seu-charging-widget-v1.5`；
4. 解压后将 APK 发送到 Android 手机上安装。

### 3. 添加到手机桌面 (Widget 4×2)
1. 手机打开一次「东大电桩」App，授权定位权限（用于计算最近车棚）；
2. 返回手机主屏幕，长按空白处，选择**微件 / 小部件**；
3. 找到「东大电桩」，选择 **4×2** 尺寸卡片拖至桌面。

### 4. 开启 GitHub Pages 网页版 (PWA)
1. 在 GitHub 仓库设置中打开 **Settings** -> **Pages**；
2. Source 选择 **Deploy from a branch**，指定 `main` 分支的 `/docs` 目录并保存；
3. 部署完成后用手机浏览器访问，点击「**添加到主屏幕**」即可全屏运行。

---

## 🐛 已修复问题（v1.5+）

- 数据来源改为官方 JSON API，移除原先抓取 `/classic` HTML 正则解析与伪造仿真算法，解决「数据始终不更新且对不上」。
- 修复点击「换车棚」后无法切回校医院：补注册 `ACTION_RESET_HOSPITAL` 广播，并确保「⭐ 回校医院」按钮可用。
