package cn.edu.seu.chargingwidget;

import org.json.JSONException;
import org.json.JSONObject;

public class ChargingStation {
    public static final int STATUS_AMPLE = 0;    // 充裕 (>= 35%)
    public static final int STATUS_MODERATE = 1; // 紧张 (10% ~ 35%)
    public static final int STATUS_FULL = 2;     // 满位 (< 10%)

    public static final String CAMPUS_JIULONGHU = "jiulonghu";
    public static final String CAMPUS_DINGJIAQIAO = "dingjiaqiao";
    public static final String CAMPUS_WUXI = "wuxi";

    private final String id;
    private final String name;
    private final String shortName;
    private final String campus;
    private final String campusName;
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

    public ChargingStation(String id, String name, String shortName, String campus, String campusName,
                           double latitude, double longitude, int totalSockets, int fastSockets, int slowSockets) {
        this.id = id;
        this.name = name;
        this.shortName = shortName;
        this.campus = campus;
        this.campusName = campusName;
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
        return campus;
    }

    public String getCampusName() {
        return campusName;
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
            json.put("campus", campus);
            json.put("campusName", campusName);
            json.put("total", totalSockets);
            json.put("free", freeSockets);
            json.put("inUse", inUseSockets);
            json.put("fault", faultSockets);
            json.put("fast", fastSockets);
            json.put("freeFast", freeFastSockets);
            json.put("slow", slowSockets);
            json.put("freeSlow", freeSlowSockets);
            json.put("prediction", trendPrediction);
        } catch (JSONException ignored) {
        }
        return json;
    }
}
