package cn.edu.seu.chargingwidget;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQ_LOCATION = 1001;

    private Button btnGrantPermission;
    private TextView tvPermStatus;
    private TextView tvSyncStatus;
    private Button btnSyncRemote;
    private LinearLayout layoutStationsList;
    private View widgetPreviewRoot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        checkLocationPermissionState();
        refreshPreviewAndList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkLocationPermissionState();
        refreshPreviewAndList();
    }

    private void initViews() {
        btnGrantPermission = findViewById(R.id.btn_grant_permission);
        tvPermStatus = findViewById(R.id.tv_perm_status);
        tvSyncStatus = findViewById(R.id.tv_sync_status);
        btnSyncRemote = findViewById(R.id.btn_sync_remote);
        layoutStationsList = findViewById(R.id.layout_stations_list);
        widgetPreviewRoot = findViewById(R.id.widget_preview_container);

        btnGrantPermission.setOnClickListener(v -> requestLocationPermissions());

        // 云端同步按钮
        btnSyncRemote.setOnClickListener(v -> {
            btnSyncRemote.setEnabled(false);
            btnSyncRemote.setText("正在同步...");
            ChargingDataManager.getInstance(this).fetchRemoteDataAsync((success, message) -> runOnUiThread(() -> {
                btnSyncRemote.setEnabled(true);
                btnSyncRemote.setText("立即同步数据");
                tvSyncStatus.setText(message);
                refreshPreviewAndList();
                notifyWidgetUpdate();
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }));
        });

        // 打开 Where Are Available Chargers 网页版
        Button btnOpenDpdns = findViewById(R.id.btn_open_dpdns);
        btnOpenDpdns.setOnClickListener(v -> {
            String url = getString(R.string.dpdns_status_url);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        });

        // 打开 GitHub 开源仓库
        Button btnOpenGithub = findViewById(R.id.btn_open_github);
        btnOpenGithub.setOnClickListener(v -> {
            String url = getString(R.string.github_repo_url);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        });

        // 小组件预览交互
        if (widgetPreviewRoot != null) {
            View btnWeb = widgetPreviewRoot.findViewById(R.id.btn_web);
            if (btnWeb != null) {
                btnWeb.setOnClickListener(v -> {
                    String url = getString(R.string.dpdns_status_url);
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                });
            }

            View btnNext = widgetPreviewRoot.findViewById(R.id.btn_switch_next);
            if (btnNext != null) {
                btnNext.setOnClickListener(v -> {
                    ChargingDataManager.getInstance(this).switchToNextStation();
                    refreshPreviewAndList();
                    notifyWidgetUpdate();
                });
            }

            View btnAutoLoc = widgetPreviewRoot.findViewById(R.id.btn_auto_locate);
            if (btnAutoLoc != null) {
                btnAutoLoc.setOnClickListener(v -> {
                    ChargingDataManager.getInstance(this).resetToAutoLocation();
                    refreshPreviewAndList();
                    notifyWidgetUpdate();
                    Toast.makeText(this, "已开启智能位置感知", Toast.LENGTH_SHORT).show();
                });
            }

            View btnRefresh = widgetPreviewRoot.findViewById(R.id.btn_refresh);
            if (btnRefresh != null) {
                btnRefresh.setOnClickListener(v -> {
                    refreshPreviewAndList();
                    notifyWidgetUpdate();
                    Toast.makeText(this, "状态已刷新", Toast.LENGTH_SHORT).show();
                });
            }
        }
    }

    private void checkLocationPermissionState() {
        boolean hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (hasFine || hasCoarse) {
            tvPermStatus.setText("✅ 已获定位权限：在九龙湖校区内将自动为你匹配距离最近的车棚。");
            tvPermStatus.setTextColor(ContextCompat.getColor(this, R.color.status_green_text));
            btnGrantPermission.setVisibility(View.GONE);
        } else {
            tvPermStatus.setText("⚠️ 尚未授予定位权限：将始终为你优先展示默认的校医院充电桩。");
            tvPermStatus.setTextColor(ContextCompat.getColor(this, R.color.status_yellow_text));
            btnGrantPermission.setVisibility(View.VISIBLE);
        }
    }

    private void requestLocationPermissions() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                PERMISSION_REQ_LOCATION
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_LOCATION) {
            checkLocationPermissionState();
            refreshPreviewAndList();
            notifyWidgetUpdate();
        }
    }

    private void refreshPreviewAndList() {
        ChargingDataManager dataManager = ChargingDataManager.getInstance(this);
        ChargingDataManager.StationDisplayResult result = dataManager.getCurrentDisplayStation();
        ChargingStation currentStation = result.station;

        // 1. 刷新预览卡片
        if (widgetPreviewRoot != null) {
            TextView tvMode = widgetPreviewRoot.findViewById(R.id.tv_mode_tag);
            TextView tvName = widgetPreviewRoot.findViewById(R.id.tv_station_name);
            TextView tvDist = widgetPreviewRoot.findViewById(R.id.tv_distance_info);
            TextView tvBadge = widgetPreviewRoot.findViewById(R.id.tv_status_badge);
            TextView tvFree = widgetPreviewRoot.findViewById(R.id.tv_free_count);
            TextView tvTotal = widgetPreviewRoot.findViewById(R.id.tv_total_suffix);
            TextView tvBreakdown = widgetPreviewRoot.findViewById(R.id.tv_breakdown);
            TextView tvTrend = widgetPreviewRoot.findViewById(R.id.tv_trend_prediction);
            TextView tvTime = widgetPreviewRoot.findViewById(R.id.tv_update_time);

            if (tvMode != null) tvMode.setText(result.modeTag);
            if (tvName != null) {
                String title = currentStation.isFavoriteStation() ? "⭐ " + currentStation.getName() : currentStation.getName();
                tvName.setText(title);
            }
            if (tvDist != null) tvDist.setText(result.distanceText);
            if (tvFree != null) tvFree.setText(String.valueOf(currentStation.getFreeSockets()));
            if (tvTotal != null) tvTotal.setText("/ " + currentStation.getTotalSockets() + " 空闲");

            if (tvBadge != null) {
                switch (currentStation.getStatus()) {
                    case ChargingStation.STATUS_AMPLE:
                        tvBadge.setText("● 充裕");
                        tvBadge.setBackgroundResource(R.drawable.bg_badge_green);
                        tvBadge.setTextColor(ContextCompat.getColor(this, R.color.status_green_text));
                        break;
                    case ChargingStation.STATUS_MODERATE:
                        tvBadge.setText("● 紧张");
                        tvBadge.setBackgroundResource(R.drawable.bg_badge_yellow);
                        tvBadge.setTextColor(ContextCompat.getColor(this, R.color.status_yellow_text));
                        break;
                    case ChargingStation.STATUS_FULL:
                    default:
                        tvBadge.setText("● 满位");
                        tvBadge.setBackgroundResource(R.drawable.bg_badge_red);
                        tvBadge.setTextColor(ContextCompat.getColor(this, R.color.status_red_text));
                        break;
                }
            }

            if (tvBreakdown != null) {
                if (currentStation.isFavoriteStation()) {
                    tvBreakdown.setText(currentStation.getPilesFocusSummary());
                } else {
                    tvBreakdown.setText(String.format(Locale.CHINA, "⚡ 快充 %d/%d · 慢充 %d/%d · 在用 %d",
                            currentStation.getFreeFastSockets(), currentStation.getFastSockets(),
                            currentStation.getFreeSlowSockets(), currentStation.getSlowSockets(),
                            currentStation.getInUseSockets()));
                }
            }

            if (tvTrend != null) tvTrend.setText(currentStation.getTrendPrediction());

            if (tvTime != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.CHINA);
                tvTime.setText(sdf.format(new Date()) + " 更新");
            }
        }

        // 2. 刷新九龙湖校区电桩列表
        layoutStationsList.removeAllViews();
        List<ChargingStation> campusStations = dataManager.getAllStations();

        for (ChargingStation s : campusStations) {
            View itemView = LayoutInflater.from(this).inflate(R.layout.widget_charging_4x2, layoutStationsList, false);

            View bottomBar = itemView.findViewById(R.id.btn_switch_next).getParent() instanceof View
                    ? (View) itemView.findViewById(R.id.btn_switch_next).getParent() : null;
            if (bottomBar != null) {
                bottomBar.setVisibility(View.GONE);
            }

            TextView tvMode = itemView.findViewById(R.id.tv_mode_tag);
            TextView tvName = itemView.findViewById(R.id.tv_station_name);
            TextView tvDist = itemView.findViewById(R.id.tv_distance_info);
            TextView tvBadge = itemView.findViewById(R.id.tv_status_badge);
            TextView tvFree = itemView.findViewById(R.id.tv_free_count);
            TextView tvTotal = itemView.findViewById(R.id.tv_total_suffix);
            TextView tvBreakdown = itemView.findViewById(R.id.tv_breakdown);
            TextView tvTrend = itemView.findViewById(R.id.tv_trend_prediction);

            if (tvMode != null) tvMode.setVisibility(View.GONE);
            if (tvName != null) {
                tvName.setText(s.isFavoriteStation() ? "⭐ " + s.getName() : s.getName());
            }
            if (tvDist != null) {
                if (s.getDistanceMeters() >= 0) {
                    int d = Math.round(s.getDistanceMeters());
                    tvDist.setText(d < 1000 ? String.format(Locale.CHINA, " · 距你 %dm", d)
                            : String.format(Locale.CHINA, " · 距你 %.1fkm", d / 1000f));
                } else {
                    tvDist.setText("");
                }
            }

            if (tvFree != null) tvFree.setText(String.valueOf(s.getFreeSockets()));
            if (tvTotal != null) tvTotal.setText("/ " + s.getTotalSockets() + " 插座");

            if (tvBadge != null) {
                switch (s.getStatus()) {
                    case ChargingStation.STATUS_AMPLE:
                        tvBadge.setText("● 充裕");
                        tvBadge.setBackgroundResource(R.drawable.bg_badge_green);
                        tvBadge.setTextColor(ContextCompat.getColor(this, R.color.status_green_text));
                        break;
                    case ChargingStation.STATUS_MODERATE:
                        tvBadge.setText("● 紧张");
                        tvBadge.setBackgroundResource(R.drawable.bg_badge_yellow);
                        tvBadge.setTextColor(ContextCompat.getColor(this, R.color.status_yellow_text));
                        break;
                    case ChargingStation.STATUS_FULL:
                    default:
                        tvBadge.setText("● 满位");
                        tvBadge.setBackgroundResource(R.drawable.bg_badge_red);
                        tvBadge.setTextColor(ContextCompat.getColor(this, R.color.status_red_text));
                        break;
                }
            }

            if (tvBreakdown != null) {
                if (s.isFavoriteStation()) {
                    tvBreakdown.setText(s.getPilesFocusSummary());
                } else {
                    tvBreakdown.setText(String.format(Locale.CHINA, "⚡ 快充 %d/%d · 慢充 %d/%d · 在用 %d",
                            s.getFreeFastSockets(), s.getFastSockets(),
                            s.getFreeSlowSockets(), s.getSlowSockets(),
                            s.getInUseSockets()));
                }
            }
            if (tvTrend != null) tvTrend.setText(s.getTrendPrediction());

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.bottomMargin = 24;
            itemView.setLayoutParams(lp);
            layoutStationsList.addView(itemView);
        }
    }

    private void notifyWidgetUpdate() {
        Intent updateIntent = new Intent(this, ChargingWidgetProvider.class);
        updateIntent.setAction(ChargingWidgetProvider.ACTION_REFRESH);
        sendBroadcast(updateIntent);
    }
}
