package cn.edu.seu.chargingwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ChargingWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_REFRESH = "cn.edu.seu.chargingwidget.ACTION_REFRESH";
    public static final String ACTION_SWITCH_NEXT = "cn.edu.seu.chargingwidget.ACTION_SWITCH_NEXT";
    public static final String ACTION_TOGGLE_AUTO_LOCATION = "cn.edu.seu.chargingwidget.ACTION_TOGGLE_AUTO_LOCATION";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        updateAllWidgets(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (action == null) return;

        ChargingDataManager dataManager = ChargingDataManager.getInstance(context);

        if (ACTION_SWITCH_NEXT.equals(action)) {
            dataManager.switchToNextStation();
            triggerUpdate(context);
        } else if (ACTION_TOGGLE_AUTO_LOCATION.equals(action)) {
            dataManager.resetToAutoLocation();
            triggerUpdate(context);
        } else if (ACTION_REFRESH.equals(action) || Intent.ACTION_USER_PRESENT.equals(action)) {
            // 亮屏解锁或手动点击刷新，被动重算并更新
            triggerUpdate(context);
            // 尝试在网络空闲时平滑拉取 chargers.dpdns.org
            dataManager.fetchRemoteDataAsync((success, message) -> {
                if (success) {
                    triggerUpdate(context);
                }
            });
        }
    }

    private void triggerUpdate(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName thisWidget = new ComponentName(context, ChargingWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
        updateAllWidgets(context, appWidgetManager, appWidgetIds);
    }

    private void updateAllWidgets(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        if (appWidgetIds == null || appWidgetIds.length == 0) return;

        ChargingDataManager dataManager = ChargingDataManager.getInstance(context);
        ChargingDataManager.StationDisplayResult result = dataManager.getCurrentDisplayStation();
        ChargingStation station = result.station;

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.CHINA);
        String updateTimeStr = sdf.format(new Date()) + " 更新";

        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_charging_4x2);

            // 1. 顶部模式标签（显示当前定位或校区）
            views.setTextViewText(R.id.tv_mode_tag, result.modeTag);

            // 2. 直达专属免安装 PWA 网页版 (GitHub Pages)
            String webUrl = context.getString(R.string.pwa_url);
            Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl));
            webIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent webPendingIntent = PendingIntent.getActivity(
                    context, 0, webIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.btn_web, webPendingIntent);

            // 3. 车棚名称与距离提示
            String displayName = station.isFavoriteStation() ? "⭐ " + station.getName() : station.getName();
            views.setTextViewText(R.id.tv_station_name, displayName);
            views.setTextViewText(R.id.tv_distance_info, result.distanceText);

            // 4. 状态色彩徽章
            switch (station.getStatus()) {
                case ChargingStation.STATUS_AMPLE:
                    views.setTextViewText(R.id.tv_status_badge, "● 充裕");
                    views.setInt(R.id.tv_status_badge, "setBackgroundResource", R.drawable.bg_badge_green);
                    views.setTextColor(R.id.tv_status_badge, ContextCompat.getColor(context, R.color.status_green_text));
                    break;
                case ChargingStation.STATUS_MODERATE:
                    views.setTextViewText(R.id.tv_status_badge, "● 紧张");
                    views.setInt(R.id.tv_status_badge, "setBackgroundResource", R.drawable.bg_badge_yellow);
                    views.setTextColor(R.id.tv_status_badge, ContextCompat.getColor(context, R.color.status_yellow_text));
                    break;
                case ChargingStation.STATUS_FULL:
                default:
                    views.setTextViewText(R.id.tv_status_badge, "● 满位");
                    views.setInt(R.id.tv_status_badge, "setBackgroundResource", R.drawable.bg_badge_red);
                    views.setTextColor(R.id.tv_status_badge, ContextCompat.getColor(context, R.color.status_red_text));
                    break;
            }

            // 5. 核心空闲插座大数（明确展示可用数与总数，杜绝歧义）
            views.setTextViewText(R.id.tv_free_count, String.valueOf(station.getFreeSockets()));
            views.setTextViewText(R.id.tv_total_suffix, "个可用 / 共 " + station.getTotalSockets() + " 桩");

            // 6. 细分快慢充配比与趋势（校医院重点展示 1# 与 9# 桩位）
            String breakdownStr;
            if (station.isFavoriteStation()) {
                breakdownStr = station.getPilesFocusSummary();
            } else {
                breakdownStr = String.format(Locale.CHINA, "⚡ 快充 %d/%d · 慢充 %d/%d · 在用 %d",
                        station.getFreeFastSockets(), station.getFastSockets(),
                        station.getFreeSlowSockets(), station.getSlowSockets(),
                        station.getInUseSockets());
            }
            views.setTextViewText(R.id.tv_breakdown, breakdownStr);
            views.setTextViewText(R.id.tv_trend_prediction, station.getTrendPrediction());

            // 7. 更新时间
            views.setTextViewText(R.id.tv_update_time, updateTimeStr);

            // 8. 按钮广播注册
            // 循环切换车棚
            Intent switchIntent = new Intent(context, ChargingWidgetProvider.class);
            switchIntent.setAction(ACTION_SWITCH_NEXT);
            PendingIntent switchPendingIntent = PendingIntent.getBroadcast(
                    context, 101, switchIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.btn_switch_next, switchPendingIntent);

            // 自动定位 / 离我最近
            Intent autoLocIntent = new Intent(context, ChargingWidgetProvider.class);
            autoLocIntent.setAction(ACTION_TOGGLE_AUTO_LOCATION);
            PendingIntent autoLocPendingIntent = PendingIntent.getBroadcast(
                    context, 102, autoLocIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.btn_auto_locate, autoLocPendingIntent);

            // 立即刷新
            Intent refreshIntent = new Intent(context, ChargingWidgetProvider.class);
            refreshIntent.setAction(ACTION_REFRESH);
            PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(
                    context, 103, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }
}
