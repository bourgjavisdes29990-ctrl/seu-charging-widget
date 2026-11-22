package cn.edu.seu.chargingwidget;

import org.json.JSONException;
import org.json.JSONObject;

public class ChargingStation {
    public static final int STATUS_AMPLE = 0;    // 充裕 (>= 35%)
    public static final int STATUS_MODERATE = 1; // 紧张 (10% ~ 35%)
    public static final int STATUS_FULL = 2;     // 满位 (< 10%)

    public static final String ID_HOSPITAL = "hospital";
    public static final String CAMPUS_JIULONGHU = "jiulonghu";
    public static final String CAMPUS_DINGJIAQIAO = "dingjiaqiao";
    public static final String CAMPUS_WUXI = "wuxi";

    private final String id;
    private final String name;
    private final String shortName;
    private final double latitude;
    private final double longitude;
    private int totalSockets;
    private int freeSockets;
    private int inUseSockets;
    private int faultSockets;
    private int fastSockets;
    private int freeFastSockets;
    private int slowSockets;
    private int freeSlowSockets;
    private String trendPrediction;
    private float distanceMeters = -1f;

    // 重点关注桩位（校医院 1# 桩与 9# 桩，各 10 个插孔，合计 20 孔）
    private boolean isFavoriteStation = false;
    private int pile1FreeCount = 5;
    private int pile9FreeCount = 7;
    private String pile1FreePorts = "1, 2, 5, 8";
    private String pile9FreePorts = "2, 3, 4, 5, 9, 10";
    private String pilesRemainingSummary = "";

    public ChargingStation(String id, String name, String shortName,
                           double latitude, double longitude, int totalSockets, int initialFree, String pilesRemainingSummary) {
        this.id = id;
        this.name = name;
        this.shortName = shortName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.totalSockets = totalSockets;
        this.freeSockets = initialFree;
        this.inUseSockets = Math.max(0, totalSockets - initialFree);
        this.faultSockets = 0;
        this.fastSockets = 0;
        this.slowSockets = totalSockets;
        this.freeFastSockets = 0;
        this.freeSlowSockets = initialFree;
        this.pilesRemainingSummary = pilesRemainingSummary;
        this.trendPrediction = "";
        if (ID_HOSPITAL.equals(id)) {
            this.isFavoriteStation = true;
            this.pile1FreeCount = 5;
            this.pile9FreeCount = 7;
        }
    }

    public ChargingStation(String id, String name, String shortName,
                           double latitude, double longitude, int totalSockets, int fastSockets, int slowSockets) {
        this.id = id;
        this.name = name;
        this.shortName = shortName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.totalSockets = totalSockets;
        this.fastSockets = fastSockets;
        this.slowSockets = slowSockets;
        this.freeFastSockets = fastSockets / 3;
        this.freeSlowSockets = slowSockets / 3;
        this.freeSockets = this.freeFastSockets + this.freeSlowSockets;
        this.inUseSockets = totalSockets - freeSockets;
        this.faultSockets = 0;
        this.trendPrediction = "";
        if (ID_HOSPITAL.equals(id)) {
            this.isFavoriteStation = true;
            this.pile1FreeCount = 5;
            this.pile9FreeCount = 7;
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getShortName() {
        return shortName;
    }

    public String getCampus() {
        return CAMPUS_JIULONGHU;
    }

    public String getCampusName() {
        return "九龙湖校区";
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public int getTotalSockets() {
        return totalSockets;
    }

    public void setTotalSockets(int totalSockets) {
        this.totalSockets = totalSockets;
    }

    public int getFreeSockets() {
        return freeSockets;
    }

    public void setFreeSockets(int freeSockets) {
        this.freeSockets = Math.max(0, Math.min(totalSockets, freeSockets));
        this.inUseSockets = Math.max(0, totalSockets - this.freeSockets - this.faultSockets);
    }

    public int getInUseSockets() {
        return inUseSockets;
    }

    public void setInUseSockets(int inUseSockets) {
        this.inUseSockets = inUseSockets;
    }

    public int getFaultSockets() {
        return faultSockets;
    }

    public void setFaultSockets(int faultSockets) {
        this.faultSockets = faultSockets;
    }

    public int getFastSockets() {
        return fastSockets;
    }

    public void setFastSockets(int fastSockets) {
        this.fastSockets = fastSockets;
    }

    public int getFreeFastSockets() {
        return freeFastSockets;
    }

    public void setFreeFastSockets(int freeFastSockets) {
        this.freeFastSockets = Math.max(0, Math.min(fastSockets, freeFastSockets));
    }

    public int getSlowSockets() {
        return slowSockets;
    }

    public void setSlowSockets(int slowSockets) {
        this.slowSockets = slowSockets;
    }

    public int getFreeSlowSockets() {
        return freeSlowSockets;
    }

    public void setFreeSlowSockets(int freeSlowSockets) {
        this.freeSlowSockets = Math.max(0, Math.min(slowSockets, freeSlowSockets));
    }

    public String getTrendPrediction() {
        return trendPrediction;
    }

    public void setTrendPrediction(String trendPrediction) {
        this.trendPrediction = trendPrediction;
    }

    public float getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(float distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public boolean isFavoriteStation() {
        return isFavoriteStation;
    }

    public void setFavoriteStation(boolean favoriteStation) {
        isFavoriteStation = favoriteStation;
    }

    public int getPile1FreeCount() {
        return pile1FreeCount;
    }

    public void setPile1FreeCount(int pile1FreeCount) {
        this.pile1FreeCount = Math.max(0, Math.min(10, pile1FreeCount));
    }

    public int getPile9FreeCount() {
        return pile9FreeCount;
    }

    public void setPile9FreeCount(int pile9FreeCount) {
        this.pile9FreeCount = Math.max(0, Math.min(10, pile9FreeCount));
    }

    public String getPile1FreePorts() {
        return pile1FreePorts;
    }

    public void setPile1FreePorts(String pile1FreePorts) {
        this.pile1FreePorts = pile1FreePorts;
    }

    public String getPile9FreePorts() {
        return pile9FreePorts;
    }

    public void setPile9FreePorts(String pile9FreePorts) {
        this.pile9FreePorts = pile9FreePorts;
    }

    public int getFavoritePilesFreeTotal() {
        return pile1FreeCount + pile9FreeCount;
    }

    public int getFavoritePilesCapacity() {
        return 20; // 1# 桩 10 孔 + 9# 桩 10 孔
    }

    public boolean isPile1Free() {
        return pile1FreeCount > 0;
    }

    public void setPile1Free(boolean pile1Free) {
        if (!pile1Free) this.pile1FreeCount = 0;
    }

    public boolean isPile9Free() {
        return pile9FreeCount > 0;
    }

    public void setPile9Free(boolean pile9Free) {
        if (!pile9Free) this.pile9FreeCount = 0;
    }

    /**
     * 生成专属关注桩位（1# 与 9# 桩）的即时摘要
     */
    public String getPilesFocusSummary() {
        return "⭐ 1#桩: " + pile1FreeCount + "/10空闲 · 9#桩: " + pile9FreeCount + "/10空闲";
    }

    public String getPilesDetailSummary() {
        return "1#空孔(" + pile1FreePorts + ") · 9#空孔(" + pile9FreePorts + ")";
    }

    public String getPilesRemainingSummary() {
        return pilesRemainingSummary != null ? pilesRemainingSummary : "";
    }

    public void setPilesRemainingSummary(String pilesRemainingSummary) {
        this.pilesRemainingSummary = pilesRemainingSummary;
    }

    public int getStatus() {
        if (totalSockets == 0) return STATUS_FULL;
        float ratio = (float) freeSockets / (float) totalSockets;
        if (ratio >= 0.35f) {
            return STATUS_AMPLE;
        } else if (ratio >= 0.10f) {
            return STATUS_MODERATE;
        } else {
            return STATUS_FULL;
        }
    }

    public String getStatusText() {
        switch (getStatus()) {
            case STATUS_AMPLE:
                return "● 充裕";
            case STATUS_MODERATE:
                return "● 紧张";
            case STATUS_FULL:
            default:
                return "● 满位";
        }
    }

    public JSONObject toJsonObject() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("name", name);
            json.put("shortName", shortName);
            json.put("total", totalSockets);
            json.put("free", freeSockets);
            json.put("inUse", inUseSockets);
            json.put("fault", faultSockets);
            json.put("pile1FreeCount", pile1FreeCount);
            json.put("pile9FreeCount", pile9FreeCount);
            json.put("prediction", trendPrediction);
        } catch (JSONException ignored) {
        }
        return json;
    }
}
