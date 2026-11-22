package cn.edu.seu.chargingwidget;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ChargingDataManager {
    private static final String PREF_NAME = "seu_charging_prefs";
    private static final String KEY_CURRENT_STATION_ID = "current_station_id";
    private static final String KEY_MANUAL_OVERRIDE_TIME = "manual_override_timestamp";
    private static final String KEY_AUTO_LOCATE_ENABLED = "auto_locate_enabled";
    private static final long MANUAL_OVERRIDE_DURATION_MS = 45 * 60 * 1000L; // 45 minutes

    private static ChargingDataManager instance;
    private final Context appContext;
    private final List<ChargingStation> stations = new ArrayList<>();

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
        stations.clear();
        // 东南大学九龙湖校区核心充电区真实经纬度与基准插座配比
        stations.add(new ChargingStation("taoyuan", "桃园生活区车棚", "桃园车棚", 31.8892, 118.8235, 140, 40, 100));
        stations.add(new ChargingStation("juyuan", "橘园生活区车棚", "橘园车棚", 31.8848, 118.8142, 160, 40, 120));
        stations.add(new ChargingStation("meiyuan", "梅园生活区车棚", "梅园车棚", 31.8925, 118.8208, 110, 30, 80));
        stations.add(new ChargingStation("lantai", "兰台研究生公寓充电区", "兰台公寓", 31.8950, 118.8180, 90, 20, 70));
        stations.add(new ChargingStation("jizhong", "纪忠楼/文科综合楼充电点", "纪忠楼", 31.8872, 118.8256, 60, 15, 45));
        stations.add(new ChargingStation("bldg8", "教学八线/工科大楼充电点", "教八大楼", 31.8845, 118.8215, 75, 15, 60));
        stations.add(new ChargingStation("student_center", "大学生活动中心/焦廷标馆", "大活中心", 31.8875, 118.8188, 50, 15, 35));
        stations.add(new ChargingStation("cs_bldg", "计算机/人工智能学院大楼", "计算机系", 31.8825, 118.8185, 45, 15, 30));
    }

    public List<ChargingStation> getAllStations() {
        updateSimulationData();
        return new ArrayList<>(stations);
    }

    public ChargingStation getStationById(String id) {
        updateSimulationData();
        for (ChargingStation s : stations) {
            if (s.getId().equals(id)) {
                return s;
            }
        }
        return stations.get(0);
    }

    /**
     * 获取当前应展示的车棚：
     * 1. 检查是否存在 45 分钟内的手动锁定保护期；
     * 2. 若未手动锁定且开启了智能定位感知，使用系统的 getLastKnownLocation 被动推断最近车棚；
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
            ChargingStation nearest = findNearestStation();
            if (nearest != null) {
                // 如果离校区最近车棚在 2.5km 内，确认为校内定位
                if (nearest.getDistanceMeters() >= 0 && nearest.getDistanceMeters() <= 2500) {
                    saveCurrentStationId(nearest.getId());
                    int dist = Math.round(nearest.getDistanceMeters());
                    String modeTag = "📍 自动";
                    String distText = dist < 1000 ? String.format(Locale.CHINA, " · 距你 %dm", dist)
                            : String.format(Locale.CHINA, " · 距你 %.1fkm", dist / 1000f);
                    return new StationDisplayResult(nearest, modeTag, distText, true);
                }
            }
        }

        // 手动模式或超出校区范围
        String savedId = sp.getString(KEY_CURRENT_STATION_ID, stations.get(0).getId());
        ChargingStation current = getStationById(savedId);
        String modeTag = isManualLocked ? "👤 手动锁定" : "📍 默认";
        String distText = "";
        if (current.getDistanceMeters() >= 0) {
            int dist = Math.round(current.getDistanceMeters());
            distText = dist < 1000 ? String.format(Locale.CHINA, " · 距你 %dm", dist)
                    : String.format(Locale.CHINA, " · 距你 %.1fkm", dist / 1000f);
        }

        return new StationDisplayResult(current, modeTag, distText, false);
    }

    /**
     * 手动切换到下一个车棚，并设置 45 分钟手动覆盖期
     */
    public ChargingStation switchToNextStation() {
        SharedPreferences sp = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String currentId = sp.getString(KEY_CURRENT_STATION_ID, stations.get(0).getId());

        int currentIndex = 0;
        for (int i = 0; i < stations.size(); i++) {
            if (stations.get(i).getId().equals(currentId)) {
                currentIndex = i;
                break;
            }
        }
        int nextIndex = (currentIndex + 1) % stations.size();
        ChargingStation next = stations.get(nextIndex);

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
            for (ChargingStation s : stations) {
                s.setDistanceMeters(-1f);
            }
            return;
        }

        float[] results = new float[1];
        for (ChargingStation s : stations) {
            Location.distanceBetween(loc.getLatitude(), loc.getLongitude(), s.getLatitude(), s.getLongitude(), results);
            s.setDistanceMeters(results[0]);
        }
    }

    private ChargingStation findNearestStation() {
        ChargingStation nearest = null;
        float minDistance = Float.MAX_VALUE;
        for (ChargingStation s : stations) {
            if (s.getDistanceMeters() >= 0 && s.getDistanceMeters() < minDistance) {
                minDistance = s.getDistanceMeters();
                nearest = s;
            }
        }
        return nearest;
    }

    /**
     * 基于东南大学九龙湖校园日常作息（早晚通勤、上下课高峰）的高拟真动态空闲率算法
     */
    private void updateSimulationData() {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        int daySeed = cal.get(Calendar.DAY_OF_YEAR);

        for (ChargingStation s : stations) {
            boolean isDorm = s.getId().equals("taoyuan") || s.getId().equals("juyuan")
                    || s.getId().equals("meiyuan") || s.getId().equals("lantai");

            float baseOccupancyRatio; // 占用率
            String prediction;

            if (isDorm) {
                // 宿舍区：晚上回宿舍充电高峰（20:00 - 07:00 占用率极高），白天上课（09:00 - 17:00 较空）
                if (hour >= 22 || hour < 6) {
                    baseOccupancyRatio = 0.90f; // 满位/极紧张
                    prediction = "夜间充电饱和，明晨7点陆续空出";
                } else if (hour >= 6 && hour < 9) {
                    baseOccupancyRatio = 0.70f;
                    prediction = "早课出发潮，插座正在逐步释放";
                } else if (hour >= 9 && hour < 17) {
                    baseOccupancyRatio = 0.40f; // 充裕
                    prediction = "白天余位充足，随时可以充电";
                } else if (hour >= 17 && hour < 19) {
                    baseOccupancyRatio = 0.65f;
                    prediction = "晚饭高峰，晚自习前仍有空闲";
                } else {
                    baseOccupancyRatio = 0.85f;
                    prediction = String.format(Locale.CHINA, "预计 %02d:%02d 空出 %d 个插座",
                            (hour + 1) % 24, (minute + 15) % 60, Math.max(2, s.getTotalSockets() / 25));
                }
            } else {
                // 教学科研区：白天满（08:30 - 17:30 上课做实验插座满），夜间空
                if (hour >= 8 && hour < 18) {
                    baseOccupancyRatio = 0.88f;
                    prediction = String.format(Locale.CHINA, "下课潮预计 %02d:45 空出 %d 个", hour, Math.max(3, s.getTotalSockets() / 20));
                } else if (hour >= 18 && hour < 21) {
                    baseOccupancyRatio = 0.60f;
                    prediction = "晚自习时段，尚有部分插座空闲";
                } else {
                    baseOccupancyRatio = 0.15f; // 夜间空旷
                    prediction = "非上课时段，插座充裕随时可用";
                }
            }

            // 微调波动（让每次刷新随时间分钟平滑推移）
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
