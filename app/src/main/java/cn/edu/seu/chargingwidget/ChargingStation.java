package cn.edu.seu.chargingwidget;

public class ChargingStation {
    public static final int STATUS_AMPLE = 0;    // 充裕 (>= 35%)
    public static final int STATUS_MODERATE = 1; // 紧张 (10% ~ 35%)
    public static final int STATUS_FULL = 2;     // 满位 (< 10%)

    private final String id;
    private final String name;
    private final String shortName;
    private final double latitude;
    private final double longitude;
    private final int totalSockets;
    private int freeSockets;
    private final int fastSockets;
    private int freeFastSockets;
    private final int slowSockets;
    private int freeSlowSockets;
    private String trendPrediction;
    private float distanceMeters = -1f;

    public ChargingStation(String id, String name, String shortName, double latitude, double longitude,
                           int totalSockets, int fastSockets, int slowSockets) {
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

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public int getTotalSockets() {
        return totalSockets;
    }

    public int getFreeSockets() {
        return freeSockets;
    }

    public void setFreeSockets(int freeSockets) {
        this.freeSockets = Math.max(0, Math.min(totalSockets, freeSockets));
    }

    public int getFastSockets() {
        return fastSockets;
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
}
