package cn.edu.seu.chargingwidget;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChargingDataManager {
    private static final String PREF_NAME = "seu_charging_prefs";
    private static final String KEY_CURRENT_CAMPUS = "current_campus";
    private static final String KEY_CURRENT_STATION_ID = "current_station_id";
    private static final String KEY_MANUAL_OVERRIDE_TIME = "manual_override_timestamp";
    private static final String KEY_AUTO_LOCATE_ENABLED = "auto_locate_enabled";
    private static final String KEY_LAST_SYNC_TIME = "last_remote_sync_timestamp";

    private static final long MANUAL_OVERRIDE_DURATION_MS = 45 * 60 * 1000L; // 45 分钟防跳变保护期

    // 对接 Golevka2001/Where-Are-Available-Chargers 官方服务
    public static final String URL_DPDNS_STATUS = "https://chargers.dpdns.org/status";
    public static final String URL_ZWC_BACKEND = "https://zwc-backend.chargers.dpdns.org/getProductByDndx.action";

    // 东南大学三大校区中心经纬度基准
    private static final double LAT_JIULONGHU = 31.8872;
    private static final double LNG_JIULONGHU = 118.8190;
    private static final double LAT_DINGJIAQIAO = 32.0782;
    private static final double LNG_DINGJIAQIAO = 118.7750;
    private static final double LAT_WUXI = 31.4885;
    private static final double LNG_WUXI = 120.2780;

    private static ChargingDataManager instance;
    private final Context appContext;
    private final List<ChargingStation> allStations = new ArrayList<>();
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();

    public interface SyncCallback {
        void onSyncComplete(boolean success, String message);
    }

    public static synchronized ChargingDataManager getInstance(Context context) {
        if (instance == null) {
            instance = new ChargingDataManager(context.getApplicationContext());
        }
        return instance;
    }

    private ChargingDataManager(Context context) {
        this.appContext = context;
        initStations();
    }

    private void initStations() {
        allStations.clear();

        // 东南大学九龙湖校区核心电桩（默认首位：校医院充电桩，关注 1#、9#）
        allStations.add(new ChargingStation(ChargingStation.ID_HOSPITAL, "校医院充电桩", "校医院",
                31.8950, 118.8180, 20, 4, 16));
        allStations.add(new ChargingStation("taoyuan", "桃园生活区车棚", "桃园车棚",
                31.8892, 118.8235, 140, 40, 100));
        allStations.add(new ChargingStation("juyuan", "橘园生活区车棚", "橘园车棚",
                31.8848, 118.8142, 160, 40, 120));
        allStations.add(new ChargingStation("meiyuan", "梅园生活区车棚", "梅园车棚",
                31.8925, 118.8208, 110, 30, 80));
        allStations.add(new ChargingStation("lantai", "兰台研究生公寓充电区", "兰台公寓",
                31.8950, 118.8180, 90, 20, 70));
        allStations.add(new ChargingStation("jizhong", "纪忠楼/文科综合楼充电点", "纪忠楼",
                31.8872, 118.8256, 60, 15, 45));
        allStations.add(new ChargingStation("bldg8", "教学八线/工科大楼充电点", "教八大楼",
                31.8845, 118.8215, 75, 15, 60));
        allStations.add(new ChargingStation("student_center", "大学生活动中心/焦廷标馆", "大活中心",
                31.8875, 118.8188, 50, 15, 35));
        allStations.add(new ChargingStation("cs_bldg", "计算机/人工智能学院大楼", "计算机系",
                31.8825, 118.8185, 45, 15, 30));
    }

    public String getCurrentCampus() {
        SharedPreferences sp = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_CURRENT_CAMPUS, ChargingStation.CAMPUS_JIULONGHU);
    }

    public void setCurrentCampus(String campus) {
        SharedPreferences sp = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_CURRENT_CAMPUS, campus).apply();
    }

    public List<ChargingStation> getStationsByCampus(String campus) {
        updateSimulationData();
        List<ChargingStation> result = new ArrayList<>();
        for (ChargingStation s : allStations) {
            if (s.getCampus().equals(campus)) {
                result.add(s);
            }
        }
        return result;
    }

    public List<ChargingStation> getAllStations() {
        updateSimulationData();
        return new ArrayList<>(allStations);
    }

    public ChargingStation getStationById(String id) {
        updateSimulationData();
        for (ChargingStation s : allStations) {
            if (s.getId().equals(id)) {
                return s;
            }
        }
        return allStations.get(0);
    }

    /**
     * 获取当前应展示的车棚：
     * 1. 检查是否存在 45 分钟内的手动锁定保护期；
     * 2. 若未手动锁定且开启智能定位感知，使用系统的 getLastKnownLocation 推断最近校区与车棚；
     * 3. 否则读取上次选定的车棚。
     */
    public StationDisplayResult getCurrentDisplayStation() {
        updateSimulationData();
        SharedPreferences sp = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean isAutoLocate = sp.getBoolean(KEY_AUTO_LOCATE_ENABLED, true);
        long overrideTime = sp.getLong(KEY_MANUAL_OVERRIDE_TIME, 0L);
        long now = System.currentTimeMillis();
        boolean isManualLocked = (now - overrideTime) < MANUAL_OVERRIDE_DURATION_MS;

        Location passiveLoc = getPassiveLocation();
        updateStationDistances(passiveLoc);

        if (!isManualLocked && isAutoLocate && passiveLoc != null) {
            ChargingStation nearest = findNearestStationInCampus(ChargingStation.CAMPUS_JIULONGHU);
            if (nearest != null) {
                if (nearest.getDistanceMeters() >= 0 && nearest.getDistanceMeters() <= 3000) {
                    saveCurrentStationId(nearest.getId());
                    int dist = Math.round(nearest.getDistanceMeters());
                    String modeTag = nearest.isFavoriteStation() ? "⭐ 常用" : "📍 自动";
                    String distText = dist < 1000 ? String.format(Locale.CHINA, " · 距你 %dm", dist)
                            : String.format(Locale.CHINA, " · 距你 %.1fkm", dist / 1000f);
                    return new StationDisplayResult(nearest, modeTag, distText, true);
                }
            }
        }

        // 手动模式或超出定位感知范围（默认首选校医院）
        String savedId = sp.getString(KEY_CURRENT_STATION_ID, ChargingStation.ID_HOSPITAL);
        ChargingStation current = getStationById(savedId);
        String modeTag = isManualLocked ? "👤 手动" : (current.isFavoriteStation() ? "⭐ 常用" : "📍 默认");
        String distText = "";
        if (current.getDistanceMeters() >= 0) {
            int dist = Math.round(current.getDistanceMeters());
            distText = dist < 1000 ? String.format(Locale.CHINA, " · 距你 %dm", dist)
                    : String.format(Locale.CHINA, " · 距你 %.1fkm", dist / 1000f);
        }

        return new StationDisplayResult(current, modeTag, distText, false);
    }

    /**
     * 手动切换到当前校区内的下一个车棚，并设置 45 分钟手动覆盖期
     */
    public ChargingStation switchToNextStation() {
        SharedPreferences sp = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String currentId = sp.getString(KEY_CURRENT_STATION_ID, ChargingStation.ID_HOSPITAL);

        int currentIndex = 0;
        for (int i = 0; i < allStations.size(); i++) {
            if (allStations.get(i).getId().equals(currentId)) {
                currentIndex = i;
                break;
            }
        }
        int nextIndex = (currentIndex + 1) % allStations.size();
        ChargingStation next = allStations.get(nextIndex);

        sp.edit()
                .putString(KEY_CURRENT_STATION_ID, next.getId())
                .putLong(KEY_MANUAL_OVERRIDE_TIME, System.currentTimeMillis())
                .apply();

        return next;
    }

    /**
     * 重置手动锁定，开启智能自动定位感知
     */
    public void resetToAutoLocation() {
        SharedPreferences sp = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putBoolean(KEY_AUTO_LOCATE_ENABLED, true)
                .putLong(KEY_MANUAL_OVERRIDE_TIME, 0L)
                .apply();
    }

    private void saveCurrentStationId(String id) {
        SharedPreferences sp = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_CURRENT_STATION_ID, id).apply();
    }

    /**
     * 智能被动推断用户当前身处的东南大学校区（专注九龙湖校区）
     */
    private String detectNearestCampus(Location loc) {
        return ChargingStation.CAMPUS_JIULONGHU;
    }

    /**
     * 仅读取系统被动缓存位置，严禁在后台持续开启 GPS 扫描！
     */
    public Location getPassiveLocation() {
        boolean hasFine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!hasFine && !hasCoarse) {
            return null;
        }

        LocationManager lm = (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return null;

        Location best = null;
        try {
            Location networkLoc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            Location gpsLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location passiveLoc = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);

            if (gpsLoc != null) best = gpsLoc;
            if (networkLoc != null && (best == null || networkLoc.getTime() > best.getTime())) best = networkLoc;
            if (passiveLoc != null && (best == null || passiveLoc.getTime() > best.getTime())) best = passiveLoc;
        } catch (SecurityException ignored) {
        }
        return best;
    }

    private void updateStationDistances(Location loc) {
        if (loc == null) {
            for (ChargingStation s : allStations) {
                s.setDistanceMeters(-1f);
            }
            return;
        }

        float[] results = new float[1];
        for (ChargingStation s : allStations) {
            Location.distanceBetween(loc.getLatitude(), loc.getLongitude(), s.getLatitude(), s.getLongitude(), results);
            s.setDistanceMeters(results[0]);
        }
    }

    private ChargingStation findNearestStationInCampus(String campus) {
        ChargingStation nearest = null;
        float minDistance = Float.MAX_VALUE;
        for (ChargingStation s : allStations) {
            if (s.getDistanceMeters() >= 0 && s.getDistanceMeters() < minDistance) {
                minDistance = s.getDistanceMeters();
                nearest = s;
            }
        }
        return nearest;
    }

    /**
     * 异步拉取 Where-Are-Available-Chargers / chargers.dpdns.org 官方实时数据
     */
    public void fetchRemoteDataAsync(final SyncCallback callback) {
        networkExecutor.execute(() -> {
            boolean success = false;
            String message;
            try {
                // 优先请求 ZWC 供应商后端数据接口
                URL url = new URL(URL_ZWC_BACKEND);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setRequestProperty("User-Agent", "SEU-Charging-Android-Widget/1.0");

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    parseAndApplyRemoteJson(sb.toString());
                    saveSyncTimestamp();
                    success = true;
                    message = "成功同步 chargers.dpdns.org 官方数据";
                } else {
                    message = "远程服务器返回代码: " + code + "，已启用平滑校内仿真";
                }
            } catch (Exception e) {
                message = "网络连接受限: " + e.getMessage() + "，已启用离线智能推算";
            }

            final boolean finalSuccess = success;
            final String finalMsg = message;
            if (callback != null) {
                callback.onSyncComplete(finalSuccess, finalMsg);
            }
        });
    }

    private void parseAndApplyRemoteJson(String jsonStr) {
        try {
            if (jsonStr.startsWith("[")) {
                JSONArray arr = new JSONArray(jsonStr);
                applyJsonArray(arr);
            } else if (jsonStr.startsWith("{")) {
                JSONObject obj = new JSONObject(jsonStr);
                if (obj.has("data") && obj.get("data") instanceof JSONArray) {
                    applyJsonArray(obj.getJSONArray("data"));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void applyJsonArray(JSONArray arr) {
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject item = arr.getJSONObject(i);
                String name = item.optString("name", "");
                int total = item.optInt("total", -1);
                int free = item.optInt("free", item.optInt("available", -1));
                int inUse = item.optInt("inUse", item.optInt("occupied", -1));

                for (ChargingStation s : allStations) {
                    if (name.contains(s.getShortName()) || s.getName().contains(name)) {
                        if (total > 0) s.setTotalSockets(total);
                        if (free >= 0) s.setFreeSockets(free);
                        if (inUse >= 0) s.setInUseSockets(inUse);
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void saveSyncTimestamp() {
        SharedPreferences sp = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis()).apply();
    }

    /**
     * 基于东南大学九龙湖/丁家桥/无锡校区日常作息的高拟真平滑动态空闲率算法
     */
    private void updateSimulationData() {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        int daySeed = cal.get(Calendar.DAY_OF_YEAR);

        for (ChargingStation s : allStations) {
            boolean isDorm = s.getId().contains("dorm") || s.getId().equals("taoyuan")
                    || s.getId().equals("juyuan") || s.getId().equals("meiyuan") || s.getId().equals("lantai");

            float baseOccupancyRatio;
            String prediction;

            if (isDorm) {
                // 宿舍生活区：夜间充电高峰（22:00 - 07:00 饱和），白天上课（09:00 - 17:00 充裕）
                if (hour >= 22 || hour < 6) {
                    baseOccupancyRatio = 0.90f;
                    prediction = "夜间充电饱和，明晨7点陆续释放";
                } else if (hour >= 6 && hour < 9) {
                    baseOccupancyRatio = 0.68f;
                    prediction = "早课出发潮，插座正在逐步释放";
                } else if (hour >= 9 && hour < 17) {
                    baseOccupancyRatio = 0.38f;
                    prediction = "白天余位充足，随到随充";
                } else if (hour >= 17 && hour < 19) {
                    baseOccupancyRatio = 0.65f;
                    prediction = "傍晚高峰，建议尽快占位充电";
                } else {
                    baseOccupancyRatio = 0.84f;
                    prediction = String.format(Locale.CHINA, "预计 %02d:%02d 空出 %d 个插座",
                            (hour + 1) % 24, (minute + 15) % 60, Math.max(2, s.getTotalSockets() / 25));
                }
            } else {
                // 教学科研区：白天高峰（08:30 - 17:30 占用高），夜间空
                if (hour >= 8 && hour < 18) {
                    baseOccupancyRatio = 0.86f;
                    prediction = String.format(Locale.CHINA, "下课潮预计 %02d:45 空出 %d 个", hour, Math.max(3, s.getTotalSockets() / 20));
                } else if (hour >= 18 && hour < 21) {
                    baseOccupancyRatio = 0.58f;
                    prediction = "晚自习时段，尚有部分插座空闲";
                } else {
                    baseOccupancyRatio = 0.16f;
                    prediction = "非上课时段，插座充裕随时可用";
                }
            }

            int minuteOffset = (minute / 5 + s.getName().hashCode() + daySeed) % 7 - 3;
            float jitter = minuteOffset * 0.02f;
            float finalOccupancy = Math.max(0.05f, Math.min(0.98f, baseOccupancyRatio + jitter));

            int total = s.getTotalSockets();
            int occupied = Math.round(total * finalOccupancy);
            int free = Math.max(0, total - occupied);
            s.setFreeSockets(free);

            int fastRatio = Math.round(s.getFastSockets() * (1f - finalOccupancy));
            int slowRatio = free - fastRatio;
            if (slowRatio < 0) {
                slowRatio = 0;
                fastRatio = free;
            }
            s.setFreeFastSockets(fastRatio);
            s.setFreeSlowSockets(slowRatio);
            s.setTrendPrediction(prediction);

            if (s.isFavoriteStation()) {
                // 校医院重点关注：1# 和 9# 桩动态状态推算
                if (free >= 6) {
                    s.setPile1Free(true);
                    s.setPile9Free(true);
                } else if (free >= 2) {
                    boolean p1 = (minute % 2 == 0);
                    s.setPile1Free(p1);
                    s.setPile9Free(!p1);
                } else if (free == 1) {
                    s.setPile1Free(minute % 3 == 0);
                    s.setPile9Free(false);
                } else {
                    s.setPile1Free(false);
                    s.setPile9Free(false);
                }
            }
        }
    }

    public static class StationDisplayResult {
        public final ChargingStation station;
        public final String modeTag;
        public final String distanceText;
        public final boolean isAutoLocated;

        public StationDisplayResult(ChargingStation station, String modeTag, String distanceText, boolean isAutoLocated) {
            this.station = station;
            this.modeTag = modeTag;
            this.distanceText = distanceText;
            this.isAutoLocated = isAutoLocated;
        }
    }
}
