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
            // 亮屏解锁或手动刷新，被动重算展示
            triggerUpdate(context);
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

            // 1. 顶部栏与模式标识
            views.setTextViewText(R.id.tv_mode_tag, result.modeTag);

            // 2. 直达 PWA 网页版按钮（严格携带 FLAG_ACTIVITY_NEW_TASK）
            String pwaUrl = context.getString(R.string.pwa_url);
            Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(pwaUrl));
            webIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent webPendingIntent = PendingIntent.getActivity(
                    context, 0, webIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.btn_web, webPendingIntent);

            // 3. 车棚名称与被动距离感知
            views.setTextViewText(R.id.tv_station_name, station.getName());
            views.setTextViewText(R.id.tv_distance_info, result.distanceText);

            // 4. 状态徽章色彩（绿色充裕、黄色紧张、红色已满）
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

            // 5. 核心空闲插座大字展示
            views.setTextViewText(R.id.tv_free_count, String.valueOf(station.getFreeSockets()));
            views.setTextViewText(R.id.tv_total_suffix, "/ " + station.getTotalSockets() + " 空闲插座");

            // 6. 细分快充慢充与趋势预估
            String breakdownStr = String.format(Locale.CHINA, "⚡ 快充 %d/%d · 慢充 %d/%d",
                    station.getFreeFastSockets(), station.getFastSockets(),
                    station.getFreeSlowSockets(), station.getSlowSockets());
            views.setTextViewText(R.id.tv_breakdown, breakdownStr);
            views.setTextViewText(R.id.tv_trend_prediction, station.getTrendPrediction());

            // 7. 更新时间
            views.setTextViewText(R.id.tv_update_time, updateTimeStr);

            // 8. 按钮交互广播配置 (PendingIntent.getBroadcast)
            // 切换车棚按钮
            Intent switchIntent = new Intent(context, ChargingWidgetProvider.class);
            switchIntent.setAction(ACTION_SWITCH_NEXT);
            PendingIntent switchPendingIntent = PendingIntent.getBroadcast(
                    context, 101, switchIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.btn_switch_next, switchPendingIntent);

            // 自动定位 / 离我最近按钮
            Intent autoLocIntent = new Intent(context, ChargingWidgetProvider.class);
            autoLocIntent.setAction(ACTION_TOGGLE_AUTO_LOCATION);
            PendingIntent autoLocPendingIntent = PendingIntent.getBroadcast(
                    context, 102, autoLocIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.btn_auto_locate, autoLocPendingIntent);

            // 立即刷新按钮
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
