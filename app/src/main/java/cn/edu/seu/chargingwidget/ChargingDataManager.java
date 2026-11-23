package cn.edu.seu.chargingwidget;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 东南大学九龙湖校区充电桩数据管理。
 *
 * 数据来源：Where-Are-Available-Chargers 官方 JSON API
 *   https://chargers.dpdns.org/api/get_status/jiulonghu
 * 该接口直接返回每个充电站（total_count / available_count）以及每台桩（如 1#、9#）
 * 的实时空闲插座数与逐孔状态，无需抓取 HTML、无需任何伪造仿真。
 */
public class ChargingDataManager {
    private static final String PREF_NAME = "seu_charging_prefs";
    private static final String KEY_CURRENT_STATION_ID = "current_station_id";
    private static final String KEY_MANUAL_OVERRIDE_TIME = "manual_override_timestamp";
    private static final String KEY_LAST_SYNC_TIME = "last_remote_sync_timestamp";
    private static final String CACHE_FILE_NAME = "charging_status_cache.json";

    private static final long MANUAL_OVERRIDE_DURATION_MS = 45 * 60 * 1000L; // 45 分钟防跳变保护期

    // 官方实时数据接口（Where-Are-Available-Chargers）
    public static final String URL_STATUS_API = "https://chargers.dpdns.org/api/get_status/jiulonghu";
    // 官方网页看板（卡片右上角“全校看板”直达）
    public static final String URL_DPDNS_STATUS = "https://chargers.dpdns.org/status";

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
        restoreCachedData();
        fetchRemoteDataAsync(null);
    }

    /**
     * 初始化九龙湖校区 10 个真实充电站。
     * 坐标、总插座数、空闲数均为 chargers.dpdns.org 官方 API 提供的真实值；
     * 空闲数在这里仅作为“最后已知快照”兜底，联网同步后会被最新数据覆盖。
     */
    private void initStations() {
        allStations.clear();

        // 1. 校医院（默认置顶；重点关注 1# 与 9# 桩）
        ChargingStation hospital = new ChargingStation(ChargingStation.ID_HOSPITAL, "校医院", "校医院",
                31.892883, 118.82438, 90, 22, "");
        hospital.setPile1FreeCount(3);
        hospital.setPile9FreeCount(8);
        hospital.setPile1FreePorts("2, 3, 6");
        hospital.setPile9FreePorts("2, 3, 4, 5, 6, 7, 8, 9");
        hospital.setTrendPrediction("点击刷新同步最新实时数据");
        allStations.add(hospital);

        // 2. 东门北侧
        allStations.add(new ChargingStation("east_north", "东门北侧", "东门北侧",
                31.887383, 118.82984, 320, 238, ""));

        // 3. 东门南侧
        allStations.add(new ChargingStation("east_south", "东门南侧", "东门南侧",
                31.886993, 118.830055, 20, 15, ""));

        // 4. 北门东侧
        allStations.add(new ChargingStation("north_east", "北门东侧", "北门东侧",
                31.893595, 118.8246, 180, 46, ""));

        // 5. 北门外卖柜旁
        allStations.add(new ChargingStation("north_delivery", "北门外卖柜旁", "外卖柜旁",
                31.887253, 118.82091, 20, 6, ""));

        // 6. 北门西侧
        allStations.add(new ChargingStation("north_west", "北门西侧", "北门西侧",
                31.893429, 118.82249, 50, 21, ""));

        // 7. 南门外东侧
        allStations.add(new ChargingStation("south_east", "南门外东侧", "南门外东",
                31.881042, 118.819916, 20, 2, ""));

        // 8. 南门外西侧
        allStations.add(new ChargingStation("south_west", "南门外西侧", "南门外西",
                31.880817, 118.82094, 30, 3, ""));

        // 9. 橘园北侧
        allStations.add(new ChargingStation("juyuan_north", "橘园北侧", "橘园北侧",
                31.885979, 118.81205, 130, 1, ""));

        // 10. 青教公寓
        allStations.add(new ChargingStation("qingjiao", "青教公寓", "青教公寓",
                31.893145, 118.8271, 100, 36, ""));
    }

    // -------------------------------------------------------------------------
    // 站点查询
    // -------------------------------------------------------------------------

    public List<ChargingStation> getAllStations() {
        return new ArrayList<>(allStations);
    }

    public List<ChargingStation> getStationsByCampus(String campus) {
        List<ChargingStation> result = new ArrayList<>();
        for (ChargingStation s : allStations) {
            if (s.getCampus().equals(campus)) {
                result.add(s);
            }
        }
        return result;
    }

    public ChargingStation getStationById(String id) {
        for (ChargingStation s : allStations) {
            if (s.getId().equals(id)) {
                return s;
            }
        }
        return allStations.isEmpty() ? null : allStations.get(0);
    }

    // -------------------------------------------------------------------------
    // 当前展示站点与切换逻辑
    // -------------------------------------------------------------------------

    /**
     * 获取当前应展示的车棚：
     * 卡片默认永远锁定展示校医院（用户常充点位）；
     * 仅当用户主动点击“换车棚”或“离我最近”后、且处于 45 分钟临时查看期内，
     * 才临时展示指定车棚；保护期过后自动恢复默认校医院。
     */
    public StationDisplayResult getCurrentDisplayStation() {
        SharedPreferences sp = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long overrideTime = sp.getLong(KEY_MANUAL_OVERRIDE_TIME, 0L);
        long now = System.currentTimeMillis();
        boolean isManualLocked = (now - overrideTime) < MANUAL_OVERRIDE_DURATION_MS;

        Location passiveLoc = getPassiveLocation();
        updateStationDistances(passiveLoc);

        String stationId = isManualLocked
                ? sp.getString(KEY_CURRENT_STATION_ID, ChargingStation.ID_HOSPITAL)
                : ChargingStation.ID_HOSPITAL;

        ChargingStation current = getStationById(stationId);
        if (current == null) {
            current = getStationById(ChargingStation.ID_HOSPITAL);
        }

        String modeTag = current.isFavoriteStation() ? "⭐ 默认" : "🔄 临时";
        String distText = "";
        if (current.getDistanceMeters() >= 0) {
            int dist = Math.round(current.getDistanceMeters());
            distText = dist < 1000 ? String.format(Locale.CHINA, " · 距你 %dm", dist)
                    : String.format(Locale.CHINA, " · 距你 %.1fkm", dist / 1000f);
        }

        return new StationDisplayResult(current, modeTag, distText, !isManualLocked);
    }

    /**
     * 手动切换到下一个车棚（基于当前实际展示车棚轮转）。
     * 当轮转回到校医院时，自动清除临时覆盖状态，恢复永久默认锁定校医院。
     */
    public ChargingStation switchToNextStation() {
        StationDisplayResult currentResult = getCurrentDisplayStation();
        String currentId = currentResult.station.getId();

        int currentIndex = 0;
        for (int i = 0; i < allStations.size(); i++) {
            if (allStations.get(i).getId().equals(currentId)) {
                currentIndex = i;
                break;
            }
        }
        int nextIndex = (currentIndex + 1) % allStations.size();
        ChargingStation next = allStations.get(nextIndex);

        SharedPreferences sp = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (ChargingStation.ID_HOSPITAL.equals(next.getId())) {
            // 循环回到校医院：彻底解除临时查看状态，恢复常态默认锁定
            sp.edit()
                    .putString(KEY_CURRENT_STATION_ID, ChargingStation.ID_HOSPITAL)
                    .putLong(KEY_MANUAL_OVERRIDE_TIME, 0L)
                    .apply();
        } else {
            sp.edit()
                    .putString(KEY_CURRENT_STATION_ID, next.getId())
                    .putLong(KEY_MANUAL_OVERRIDE_TIME, System.currentTimeMillis())
                    .apply();
        }
        return next;
    }

    /** 一键重置回默认的校医院并解除临时锁定。 */
    public ChargingStation resetToHospital() {
        SharedPreferences sp = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putString(KEY_CURRENT_STATION_ID, ChargingStation.ID_HOSPITAL)
                .putLong(KEY_MANUAL_OVERRIDE_TIME, 0L)
                .apply();
        return getStationById(ChargingStation.ID_HOSPITAL);
    }

    /** 点击“离我最近”：定位最近车棚；若当前已是最近车棚，则重置回默认校医院。 */
    public ChargingStation snapToNearestOrReset() {
        SharedPreferences sp = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        StationDisplayResult currentResult = getCurrentDisplayStation();
        String currentId = currentResult.station.getId();

        Location loc = getPassiveLocation();
        updateStationDistances(loc);
        if (loc != null) {
            ChargingStation nearest = findNearestStationInCampus();
            if (nearest != null) {
                if (nearest.getId().equals(currentId) && !ChargingStation.ID_HOSPITAL.equals(currentId)) {
                    return resetToHospital();
                }
                sp.edit()
                        .putString(KEY_CURRENT_STATION_ID, nearest.getId())
                        .putLong(KEY_MANUAL_OVERRIDE_TIME, System.currentTimeMillis())
                        .apply();
                return nearest;
            }
        }
        return resetToHospital();
    }

    public void resetToAutoLocation() {
        snapToNearestOrReset();
    }

    public void setStationManually(String id) {
        if (ChargingStation.ID_HOSPITAL.equals(id)) {
            resetToHospital();
        } else {
            SharedPreferences sp = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            sp.edit()
                    .putString(KEY_CURRENT_STATION_ID, id)
                    .putLong(KEY_MANUAL_OVERRIDE_TIME, System.currentTimeMillis())
                    .apply();
        }
    }

    // -------------------------------------------------------------------------
    // 定位（仅读取系统被动缓存位置，不主动开启 GPS）
    // -------------------------------------------------------------------------

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

    private ChargingStation findNearestStationInCampus() {
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

    // -------------------------------------------------------------------------
    // 网络同步（JSON API）
    // -------------------------------------------------------------------------

    /** 异步拉取官方 JSON 实时数据并解析应用。 */
    public void fetchRemoteDataAsync(final SyncCallback callback) {
        networkExecutor.execute(() -> {
            boolean success = false;
            String message;
            try {
                URL url = new URL(URL_STATUS_API);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile) SEU-Charging-Widget/1.6");
                conn.setRequestProperty("Accept", "application/json");

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    reader.close();

                    String json = sb.toString();
                    if (parseAndApplyJson(json)) {
                        saveRawJsonToFile(json);
                        saveSyncTimestamp();
                        success = true;
                        message = "已同步 chargers.dpdns.org 实时数据";
                    } else {
                        message = "解析数据失败，保持现有数据";
                    }
                } else {
                    message = "服务返回代码 " + code + "，保持现有数据";
                }
            } catch (Exception e) {
                message = "网络不可用，显示最近缓存数据";
            }

            final boolean finalSuccess = success;
            final String finalMsg = message;
            if (callback != null) {
                callback.onSyncComplete(finalSuccess, finalMsg);
            }
        });
    }

    /**
     * 解析官方 JSON 响应并写入各站点。
     * 响应结构见 apps/worker/src/api/status.ts 的 StatusResponse：
     * { code, last_update_time, status: { stations: [ { name, total_count,
     *   available_count, lat, lng, addr, chargers: [ { name, available_count,
     *   sockets: [0|1|2,...] } ] } ] } }
     */
    private boolean parseAndApplyJson(String json) {
        try {
            JSONObject root = new JSONObject(json);
            if (root.optInt("code", -1) != 200) return false;

            JSONObject status = root.optJSONObject("status");
            if (status == null) return false;

            JSONArray stations = status.optJSONArray("stations");
            if (stations == null) return false;

            int matched = 0;
            for (int i = 0; i < stations.length(); i++) {
                JSONObject st = stations.optJSONObject(i);
                if (st == null) continue;

                String name = st.optString("name", "");
                int total = st.optInt("total_count", -1);
                int available = st.optInt("available_count", -1);

                ChargingStation target = matchStationByName(name);
                if (target == null) continue;
                matched++;

                if (total >= 0) target.setTotalSockets(total);
                if (available >= 0) {
                    target.setFreeSockets(available);
                    target.setInUseSockets(Math.max(0, target.getTotalSockets() - available));
                }

                JSONArray chargers = st.optJSONArray("chargers");
                StringBuilder pilesSb = new StringBuilder();
                int chargerCount = 0;
                if (chargers != null) {
                    chargerCount = chargers.length();
                    for (int j = 0; j < chargers.length(); j++) {
                        JSONObject ch = chargers.optJSONObject(j);
                        if (ch == null) continue;
                        String chName = ch.optString("name", "");
                        int chAvail = ch.optInt("available_count", 0);
                        if (pilesSb.length() > 0) pilesSb.append(", ");
                        pilesSb.append(chName).append(":").append(chAvail);

                        if (target.isFavoriteStation()) {
                            if ("1#".equals(chName)) {
                                target.setPile1FreeCount(chAvail);
                                target.setPile1FreePorts(extractFreePorts(ch));
                            } else if ("9#".equals(chName)) {
                                target.setPile9FreeCount(chAvail);
                                target.setPile9FreePorts(extractFreePorts(ch));
                            }
                        }
                    }
                }
                target.setPilesRemainingSummary(pilesSb.length() > 0 ? "余量: " + pilesSb : "");

                if (target.isFavoriteStation()) {
                    target.setTrendPrediction("校医院全场" + (chargerCount > 0 ? chargerCount : 9)
                            + "台桩共" + target.getTotalSockets() + "孔 · 实时余 "
                            + target.getFreeSockets() + " 可用");
                } else if (target.getTotalSockets() > 0) {
                    target.setTrendPrediction(target.getFreeSockets() > target.getTotalSockets() / 3
                            ? "插座充足，随到随充" : "余量紧张，建议留意空孔");
                }
            }
            return matched > 0;
        } catch (JSONException e) {
            return false;
        }
    }

    private ChargingStation matchStationByName(String name) {
        for (ChargingStation s : allStations) {
            if (s.getName().equals(name)
                    || name.contains(s.getShortName())
                    || s.getName().contains(name)) {
                return s;
            }
        }
        return null;
    }

    /** 从 sockets 数组提取空闲孔位号（sockets 中 1=空闲；孔位号从 1 开始）。 */
    private String extractFreePorts(JSONObject charger) {
        JSONArray sockets = charger.optJSONArray("sockets");
        if (sockets == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < sockets.length(); k++) {
            if (sockets.optInt(k, 0) == 1) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(k + 1);
            }
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // 缓存（原样保存最近一次成功同步的 JSON，离线时回退）
    // -------------------------------------------------------------------------

    private File cacheFile() {
        return new File(appContext.getFilesDir(), CACHE_FILE_NAME);
    }

    private void saveRawJsonToFile(String json) {
        File f = cacheFile();
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(f);
            fos.write(json.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        } catch (Exception ignored) {
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (Exception ignored) { }
            }
        }
    }

    private void restoreCachedData() {
        File f = cacheFile();
        if (!f.exists()) return;
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            int read = fis.read(buf);
            if (read > 0) {
                parseAndApplyJson(new String(buf, 0, read, StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        } finally {
            if (fis != null) {
                try { fis.close(); } catch (Exception ignored) { }
            }
        }
    }

    private void saveSyncTimestamp() {
        SharedPreferences sp = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis()).apply();
    }

    public long getLastSyncTimestamp() {
        SharedPreferences sp = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getLong(KEY_LAST_SYNC_TIME, 0L);
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
