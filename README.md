# 东南大学九龙湖校区电动车充电桩小组件 (Android Widget + PWA)

> 针对东南大学（九龙湖校区）量身定制的**轻量原生桌面卡片（4×2）**与**免安装 PWA 网页全景看板**。
> 参考经典接驳车小组件极简、低功耗、免配置 CI 的成熟架构开发。

---

## 📱 一、产品形态与核心特性

### 1. 轻量原生 Android 桌面卡片 (AppWidget 4×2)
- **现代磨砂黑设计**：采用 Deep Slate (`#0f172a`) 磨砂卡片质感，大圆角搭配微发光边框，完美贴合 Android 12+ 现代审美。
- **核心数据大字**：醒目展示当前车棚**空闲插座大字（如 `18 / 60 空闲`）**，附带快充/慢充细分（`⚡ 快充 8/20 · 慢充 10/40`）与高峰趋势预测。
- **三色动态状态徽章**：
  - 🟢 **充裕**（空闲率 ≥ 35%）：随到随充；
  - 🟡 **紧张**（10% ≤ 空闲率 < 35%）：插座趋紧；
  - 🔴 **满位**（空闲率 < 10%）：排队中。
- **全生态闭环**：
  - 右上角直达按钮：`全校电桩 🌐`，一键拉起全校 Web 版或地图导航；
  - 底部操作栏：`🔄 换车棚`（循环轮换）、`📍 离我最近`（智能感知定位）、`⚡ 刷新`。
- **零后台常驻、零跑电**：
  - `updatePeriodMillis="0"`，不在后台启动任何持续运行的 Service、无定时闹钟轮询唤醒；
  - 仅在**手机亮屏解锁（`ACTION_USER_PRESENT`）**或**用户主动点击按键**时毫秒级被动计算，彻底告别后台耗电问题。

### 2. 免安装 PWA 网页看板 (GitHub Pages)
- **独立运行模式（Standalone）**：支持添加到手机主屏幕，全屏运行无浏览器顶部地址栏与底部导航条。
- **严格 Network-First（网络优先）缓存策略**：
  - 优先通过网络拉取最新状态与代码，成功后平滑刷新本地 Cache；
  - 仅在离线/无网络时降级加载本地缓存，**彻底规避传统 PWA 手机端被旧缓存卡死的致命痛点**。
- **全景车棚监控与导航**：
  - 支持按“宿舍生活区”、“教学科研区”、“余位充裕”、“离我最近”智能分类过滤；
  - 点击车棚卡片可查看插座配比、峰谷预测、校园消防安全指引，并支持一键调用高德/百度地图路线规划。

### 3. GitHub Actions 云端全自动流水线
- 本地**无需安装 Android Studio、无需配置 JDK/Android SDK**；
- 只要通过 Git 将代码 push 到 GitHub，云端 Ubuntu 容器全自动编译出 APK，并生成名为 `seu-charging-widget-v1.0.apk` 的构件供手机直接扫码/点击下载。

---

## 🗺️ 二、九龙湖校区内置电桩数据规划

系统内置了东南大学九龙湖校区 8 大核心电动自行车充电区域的经纬度基准与插座配比模型：

| 车棚名称 | 区域类型 | 经纬度基准 | 总插座数 | 快充/慢充 | 覆盖主要楼栋与特点 |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **桃园生活区车棚** | 宿舍区 | 31.8892° N, 118.8235° E | 140 | 40 快 / 100 慢 | 桃园 1~8 舍、桃北食堂西侧，学生最密集区 |
| **橘园生活区车棚** | 宿舍区 | 31.8848° N, 118.8142° E | 160 | 40 快 / 120 慢 | 橘园 1~10 舍、橘园食堂，全校最大集中充电点 |
| **梅园生活区车棚** | 宿舍区 | 31.8925° N, 118.8208° E | 110 | 30 快 / 80 慢 | 梅园宿舍区、留学生公寓，靠近北门 |
| **兰台研究生公寓充电区** | 宿舍区 | 31.8950° N, 118.8180° E | 90 | 20 快 / 70 慢 | 兰台 1~4 舍研究生公寓、校医院北侧 |
| **纪忠楼/文科综合楼充电点** | 教学区 | 31.8872° N, 118.8256° E | 60 | 15 快 / 45 慢 | 人文/经管/法学大楼，近南门与东门主干道 |
| **教学八线/工科大楼充电点** | 教学区 | 31.8845° N, 118.8215° E | 75 | 15 快 / 60 慢 | 教八、工科实验综合大楼、机械土木大楼 |
| **大学生活动中心/焦廷标馆**| 综合区 | 31.8875° N, 118.8188° E | 50 | 15 快 / 35 慢 | 大活中心广场西侧、圆楼行政楼旁 |
| **计算机/人工智能学院大楼**| 科研区 | 31.8825° N, 118.8185° E | 45 | 15 快 / 30 慢 | 计算机学院、人工智能学院大楼北侧车棚 |

---

## ⚡ 三、核心技术与避坑实践规范

### 1. 经典 Android 原生工程规范
- **AGP 与 Gradle 版本**：采用最经典最稳定的 Groovy DSL，根目录 `build.gradle` 锁定 `com.android.tools.build:gradle:8.1.4`，配合 Gradle 8.2 Wrapper。
- **SDK 锁定 `compileSdk 33` / `targetSdk 33`**：避开 Android 34 刚上线时 GitHub Actions CI 容器由于 license 协议未更新导致的崩溃。
- **CI 避坑**：禁用第三方 `setup-android` 插件，在 `.github/workflows/build-apk.yml` 中直接使用 Ubuntu 内置环境并通过 `yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses || true` 自动签署许可。
- **产物命名规范**：通过 `applicationVariants.all` 自动生成带版本标识的安装包 `seu-charging-widget-v1.0.apk`。

### 2. 智能地理围栏与 45 分钟覆盖保护期（Location-Awareness）
- **极简初次引导**：`MainActivity` 仅在初次进入时提示并请求粗略/精确定位权限；
- **被动缓存读取**：在 `AppWidgetProvider` 中仅调用 `LocationManager.getLastKnownLocation()` 读取系统现存定位缓存，严禁后台持续扫描 GPS；
- **距离计算**：通过 `Location.distanceBetween()` 实时计算离 8 个地标的距离，智能判定当前离哪个车棚最近；
- **45 分钟防跳变保护期**：当学生手动点击 `🔄 换车棚` 切换到指定车棚时，系统在 `SharedPreferences` 记录手动操作时间戳。45 分钟内维持手动选定的车棚，防止在校园边缘走动时小组件频繁跳变；45 分钟过后自动恢复智能跟随。

### 3. RemoteViews 交互规范
- **单行按钮防截断**：小组件采用 4 层垂直阶梯结构（Header 顶部栏 -> 车棚与徽章 -> 核心空位大数与细分 -> 底部操作栏），彻底解决桌面拉伸或多语言字号导致的文字截断。
- **PendingIntent 规范**：按钮点击采用 `PendingIntent.getBroadcast` 并设置 `FLAG_IMMUTABLE`；跳转网页按钮必须携带 `Intent.FLAG_ACTIVITY_NEW_TASK`。

---

## 🚀 四、快速部署与使用指南

### 1. 部署到自己的 GitHub 仓库
```bash
# 1. 在当前项目目录下初始化 Git
git init
git add .
git commit -m "feat: initial commit for seu charging widget & pwa"

# 2. 关联你的 GitHub 远程仓库并推送
git branch -M main
git remote add origin https://github.com/<你的用户名>/seu-charging-widget.git
git push -u origin main
```

### 2. 下载与安装 Android APK
1. 打开你的 GitHub 仓库页面，点击顶部 **Actions** 标签；
2. 点击最新的 **Build SEU Charging Widget APK** 流水线运行记录；
3. 在页面底部的 **Artifacts** 区域，直接点击下载 `seu-charging-widget-v1.0`；
4. 解压后将 `seu-charging-widget-v1.0.apk` 发送到 Android 手机上安装。

### 3. 启用桌面卡片 (Widget)
1. 在手机上打开一次「东大电桩」App，授权定位权限（用于智能匹配最近车棚）；
2. 返回手机主屏幕，长按空白处，选择**微件 / 小部件**；
3. 搜索找到「东大电桩」，选择 **4×2** 尺寸卡片拖动到桌面即可！

### 4. 开启 GitHub Pages 网页版 (PWA)
1. 在 GitHub 仓库的 **Settings** -> **Pages** 中；
2. **Build and deployment** -> Source 选择 **Deploy from a branch** 或 **GitHub Actions**；
3. 若选择 Branch，分支设为 `main`，目录设为 `/docs`；
4. 部署成功后，手机浏览器（Safari / Chrome / Edge）访问分配的域名，点击菜单中的「**添加到主屏幕**」，即可享受与原生 App 无异的 PWA 体验。

---

## 🔌 五、与学校实际后端接口对接说明（可选扩展）

本工程中 `ChargingDataManager.java`（Android）与 `app.js`（PWA）预留了标准化数据解耦层。若后续获取到了东大后勤、易充网或校园小程序的官方实时 API：
只需在 `ChargingDataManager` 中将 `updateSimulationData()` 替换为 HTTP GET 请求解析对应 JSON，返回字段映射至 `ChargingStation` 模型（`freeSockets`, `freeFastSockets`, `freeSlowSockets`），Android 桌面小组件与 PWA 看板即可无缝切换为全校官方实时数据源！
