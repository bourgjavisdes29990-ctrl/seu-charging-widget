# 项目约定（Agent 必读）

## 版本号约定（重要）

**每次改动后，必须同步提升安装包版本号**，用户已明确要求。

需要同时更新以下几处，保持版本号一致：

1. `app/build.gradle` —— `versionCode`（+1）和 `versionName`
2. `.github/workflows/build-apk.yml` —— APK 产物名 `seu-charging-widget-vX.Y.apk` 与 artifact name（两处）
3. `app/src/main/java/cn/edu/seu/chargingwidget/ChargingDataManager.java` —— 网络请求 `User-Agent` 里的版本号
4. `README.md` —— 「下载 APK」处的文件名、以及「已修复问题（vX.Y）」标题
5. `docs/sw.js` —— PWA 的 `CACHE_VERSION`（`seu-charging-vX.Y.0`）

版本命名规则：`versionName` 用 `X.Y`，`versionCode` 用递增整数（当前两者末位对应：1.6 ↔ 7）。
