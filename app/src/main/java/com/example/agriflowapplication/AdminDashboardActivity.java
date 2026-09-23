package com.example.agriflowapplication;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminDashboardActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TextView tvUserName, tvLastUpdated, tvNotificationBadge;
    private ListenerRegistration telemetryListener, notificationListener, deviceControlListener, scheduleWaterListener, scheduleFertilizerListener;
    private NotificationAdapter alertsAdapter;
    private List<NotificationRecord> recentAlerts;
    private View tvNoRecentAlerts;

    // Telemetry Views
    private TextView tvSoilMoisture, tvSoilMoistureStatus, tvWaterTank, tvWaterTankStatus, tvTemperature, tvTemperatureStatus, tvHumidity, tvHumidityStatus, tvLightIntensity, tvLightIntensityStatus;
    private ProgressBar pbSoilMoisture, pbWaterTank, pbHumidity, pbLightIntensity;
    private View vTempIndicator;

    // Pump Control Views
    private TextView tvWaterPumpStatus, tvFertilizerPumpStatus;
    private MaterialSwitch switchWaterPump, switchFertilizerPump;
    private MaterialButton btnStartWater, btnStopWater, btnStartFertilizer, btnStopFertilizer;

    // Schedule Views
    private TextView tvWaterScheduleTime, tvWaterScheduleDetail, tvWaterScheduleStatus;
    private TextView tvFertilizerScheduleTime, tvFertilizerScheduleDetail, tvFertilizerScheduleStatus;

    private String currentWaterState = "OFF";
    private String currentFertilizerState = "OFF";

    private String farmLocation = "Admin Terminal";
    private long lastHistoryLogTime = 0;
    private static final long LOG_INTERVAL_MS = 60000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        mAuth = FirebaseManager.getInstance().getAuth();
        db = FirebaseManager.getInstance().getDb();

        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        tvUserName = findViewById(R.id.tvUserName);
        tvLastUpdated = findViewById(R.id.tvLastUpdated);
        tvNotificationBadge = findViewById(R.id.tvNotificationBadge);
        tvNoRecentAlerts = findViewById(R.id.tvNoRecentAlerts);
        tvSoilMoisture = findViewById(R.id.tvSoilMoisture);

        RecyclerView rvRecentAlerts = findViewById(R.id.rvRecentAlerts);
        rvRecentAlerts.setLayoutManager(new LinearLayoutManager(this));
        recentAlerts = new ArrayList<>();
        alertsAdapter = new NotificationAdapter(recentAlerts, this::handleAlertClick);
        rvRecentAlerts.setAdapter(alertsAdapter);

        findViewById(R.id.btnViewAllNotifications).setOnClickListener(v -> startActivity(new Intent(this, NotificationsActivity.class)));
        tvSoilMoistureStatus = findViewById(R.id.tvSoilMoistureStatus);
        tvWaterTank = findViewById(R.id.tvWaterTank);
        tvWaterTankStatus = findViewById(R.id.tvWaterTankStatus);
        tvWaterTank.setText("Loading...");
        tvTemperature = findViewById(R.id.tvTemperature);
        tvTemperatureStatus = findViewById(R.id.tvTemperatureStatus);
        tvHumidity = findViewById(R.id.tvHumidity);
        tvHumidityStatus = findViewById(R.id.tvHumidityStatus);
        tvLightIntensity = findViewById(R.id.tvLightIntensity);
        tvLightIntensityStatus = findViewById(R.id.tvLightIntensityStatus);

        pbSoilMoisture = findViewById(R.id.pbSoilMoisture);
        pbWaterTank = findViewById(R.id.pbWaterTank);
        pbHumidity = findViewById(R.id.pbHumidity);
        pbLightIntensity = findViewById(R.id.pbLightIntensity);

        vTempIndicator = findViewById(R.id.vTempIndicator);

        // Pump Controls
        tvWaterPumpStatus = findViewById(R.id.tvWaterPumpStatus);
        tvFertilizerPumpStatus = findViewById(R.id.tvFertilizerPumpStatus);
        switchWaterPump = findViewById(R.id.switchWaterPump);
        switchFertilizerPump = findViewById(R.id.switchFertilizerPump);
        btnStartWater = findViewById(R.id.btnStartWater);
        btnStopWater = findViewById(R.id.btnStopWater);
        btnStartFertilizer = findViewById(R.id.btnStartFertilizer);
        btnStopFertilizer = findViewById(R.id.btnStopFertilizer);

        if (btnStartWater != null) btnStartWater.setOnClickListener(v -> handleStartWater());
        if (btnStopWater != null) btnStopWater.setOnClickListener(v -> handleStopWater());
        if (btnStartFertilizer != null) btnStartFertilizer.setOnClickListener(v -> handleStartFertilizer());
        if (btnStopFertilizer != null) btnStopFertilizer.setOnClickListener(v -> handleStopFertilizer());

        // Schedule Controls
        tvWaterScheduleTime = findViewById(R.id.tvWaterScheduleTime);
        tvWaterScheduleDetail = findViewById(R.id.tvWaterScheduleDetail);
        tvWaterScheduleStatus = findViewById(R.id.tvWaterScheduleStatus);
        tvFertilizerScheduleTime = findViewById(R.id.tvFertilizerScheduleTime);
        tvFertilizerScheduleDetail = findViewById(R.id.tvFertilizerScheduleDetail);
        tvFertilizerScheduleStatus = findViewById(R.id.tvFertilizerScheduleStatus);

        View btnConfigureSchedule = findViewById(R.id.btnConfigureSchedule);
        if (btnConfigureSchedule != null) {
            btnConfigureSchedule.setOnClickListener(v -> startActivity(new Intent(AdminDashboardActivity.this, ScheduleActivity.class)));
        }

        fetchUserName();
        initializeDeviceControl();
        startTelemetryListener();
        startDeviceControlListener();
        startScheduleListener();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_admin_dashboard), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        // Bottom Navigation
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
            bottomNavigationView.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_home) {
                    return true;
                } else if (itemId == R.id.nav_history) {
                    Intent intent = new Intent(AdminDashboardActivity.this, MoistureHistoryActivity.class);
                    intent.putExtra("is_admin", true);
                    startActivity(intent);
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    finish();
                    return true;
                } else if (itemId == R.id.nav_manage) {
                    startActivity(new Intent(AdminDashboardActivity.this, FarmerListActivity.class));
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    finish();
                    return true;
                } else if (itemId == R.id.nav_schedule) {
                    startActivity(new Intent(AdminDashboardActivity.this, ScheduleActivity.class));
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    finish();
                    return true;
                } else if (itemId == R.id.nav_profile) {
                    startActivity(new Intent(AdminDashboardActivity.this, AdminProfileActivity.class));
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    finish();
                    return true;
                }
                return false;
            });
        }

        FirebaseManager.getInstance().updateFcmToken();
        findViewById(R.id.btnNotifications).setOnClickListener(v -> startActivity(new Intent(this, NotificationsActivity.class)));
        findViewById(R.id.btnLogout).setOnClickListener(v -> showLogoutConfirmation());

        startNotificationListener();
    }

    private void initializeDeviceControl() {
        DocumentReference controlRef = db.collection("system_data").document("device_control");
        controlRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                Map<String, Object> initialControl = new HashMap<>();
                initialControl.put("waterPumpState", "OFF");
                initialControl.put("fertilizerPumpState", "OFF");
                initialControl.put("deviceStatus", "CONNECTED");
                initialControl.put("lastUpdated", Timestamp.now());
                initialControl.put("updatedBy", mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "system");
                controlRef.set(initialControl);
            }
        });
    }

    private void startDeviceControlListener() {
        deviceControlListener = db.collection("system_data").document("device_control")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) return;
                    if (snapshot != null && snapshot.exists()) {
                        String waterState = snapshot.getString("waterPumpState");
                        String fertState = snapshot.getString("fertilizerPumpState");

                        if (waterState != null) currentWaterState = waterState;
                        if (fertState != null) currentFertilizerState = fertState;

                        updatePumpUi();
                    }
                });
    }

    private void updatePumpUi() {
        if (tvWaterPumpStatus != null) {
            if ("ON".equalsIgnoreCase(currentWaterState) || "RUNNING".equalsIgnoreCase(currentWaterState)) {
                tvWaterPumpStatus.setText("RUNNING");
                tvWaterPumpStatus.setTextColor(ContextCompat.getColor(this, R.color.iot_success));
            } else if ("SCHEDULED".equalsIgnoreCase(currentWaterState)) {
                tvWaterPumpStatus.setText("SCHEDULED");
                tvWaterPumpStatus.setTextColor(ContextCompat.getColor(this, R.color.iot_warning));
            } else {
                tvWaterPumpStatus.setText("OFF");
                tvWaterPumpStatus.setTextColor(Color.GRAY);
            }
        }

        if (switchWaterPump != null) {
            switchWaterPump.setOnCheckedChangeListener(null);
            switchWaterPump.setChecked("ON".equalsIgnoreCase(currentWaterState) || "RUNNING".equalsIgnoreCase(currentWaterState));
            setupPumpListeners();
        }

        if (tvFertilizerPumpStatus != null) {
            if ("ON".equalsIgnoreCase(currentFertilizerState) || "RUNNING".equalsIgnoreCase(currentFertilizerState)) {
                tvFertilizerPumpStatus.setText("RUNNING");
                tvFertilizerPumpStatus.setTextColor(ContextCompat.getColor(this, R.color.iot_success));
            } else if ("SCHEDULED".equalsIgnoreCase(currentFertilizerState)) {
                tvFertilizerPumpStatus.setText("SCHEDULED");
                tvFertilizerPumpStatus.setTextColor(ContextCompat.getColor(this, R.color.iot_warning));
            } else {
                tvFertilizerPumpStatus.setText("OFF");
                tvFertilizerPumpStatus.setTextColor(Color.GRAY);
            }
        }

        if (switchFertilizerPump != null) {
            switchFertilizerPump.setOnCheckedChangeListener(null);
            switchFertilizerPump.setChecked("ON".equalsIgnoreCase(currentFertilizerState) || "RUNNING".equalsIgnoreCase(currentFertilizerState));
            setupPumpListeners();
        }
    }

    private void setupPumpListeners() {
        if (switchWaterPump != null) {
            switchWaterPump.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) handleStartWater();
                else handleStopWater();
            });
        }

        if (switchFertilizerPump != null) {
            switchFertilizerPump.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) handleStartFertilizer();
                else handleStopFertilizer();
            });
        }
    }

    private void handleStartWater() {
        if ("ON".equalsIgnoreCase(currentWaterState) || "RUNNING".equalsIgnoreCase(currentWaterState)) {
            Toast.makeText(this, "Water irrigation is already running.", Toast.LENGTH_SHORT).show();
            updatePumpUi();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Start water irrigation?")
                .setMessage("This will activate the water irrigation system.")
                .setPositiveButton("START", (dialog, which) -> executeWaterPumpChange("ON"))
                .setNegativeButton("CANCEL", (dialog, which) -> updatePumpUi())
                .setOnCancelListener(dialog -> updatePumpUi())
                .show();
    }

    private void handleStopWater() {
        if ("OFF".equalsIgnoreCase(currentWaterState)) {
            Toast.makeText(this, "Water irrigation is already stopped.", Toast.LENGTH_SHORT).show();
            updatePumpUi();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Stop water irrigation?")
                .setMessage("Are you sure you want to stop water irrigation?")
                .setPositiveButton("STOP", (dialog, which) -> executeWaterPumpChange("OFF"))
                .setNegativeButton("CANCEL", (dialog, which) -> updatePumpUi())
                .setOnCancelListener(dialog -> updatePumpUi())
                .show();
    }

    private void executeWaterPumpChange(String newState) {
        String userId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "unknown";
        Map<String, Object> update = new HashMap<>();
        update.put("waterPumpState", newState);
        update.put("lastUpdated", Timestamp.now());
        update.put("updatedBy", userId);

        db.collection("system_data").document("device_control").update(update)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Water irrigation set to " + newState, Toast.LENGTH_SHORT).show();
                    logIrrigationEvent("Water", newState, "Manual");
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update water pump: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    updatePumpUi();
                });
    }

    private void handleStartFertilizer() {
        if ("ON".equalsIgnoreCase(currentFertilizerState) || "RUNNING".equalsIgnoreCase(currentFertilizerState)) {
            Toast.makeText(this, "Fertilizer irrigation is already running.", Toast.LENGTH_SHORT).show();
            updatePumpUi();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Start fertilizer irrigation?")
                .setMessage("This will activate the fertilizer distribution system.")
                .setPositiveButton("START", (dialog, which) -> executeFertilizerPumpChange("ON"))
                .setNegativeButton("CANCEL", (dialog, which) -> updatePumpUi())
                .setOnCancelListener(dialog -> updatePumpUi())
                .show();
    }

    private void handleStopFertilizer() {
        if ("OFF".equalsIgnoreCase(currentFertilizerState)) {
            Toast.makeText(this, "Fertilizer irrigation is already stopped.", Toast.LENGTH_SHORT).show();
            updatePumpUi();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Stop fertilizer irrigation?")
                .setMessage("Are you sure you want to stop fertilizer irrigation?")
                .setPositiveButton("STOP", (dialog, which) -> executeFertilizerPumpChange("OFF"))
                .setNegativeButton("CANCEL", (dialog, which) -> updatePumpUi())
                .setOnCancelListener(dialog -> updatePumpUi())
                .show();
    }

    private void executeFertilizerPumpChange(String newState) {
        String userId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "unknown";
        Map<String, Object> update = new HashMap<>();
        update.put("fertilizerPumpState", newState);
        update.put("lastUpdated", Timestamp.now());
        update.put("updatedBy", userId);

        db.collection("system_data").document("device_control").update(update)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Fertilizer irrigation set to " + newState, Toast.LENGTH_SHORT).show();
                    logIrrigationEvent("Fertilizer", newState, "Manual");
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update fertilizer pump: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    updatePumpUi();
                });
    }

    private void logIrrigationEvent(String type, String action, String source) {
        String userId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "unknown";
        String userEmail = mAuth.getCurrentUser() != null && mAuth.getCurrentUser().getEmail() != null ? mAuth.getCurrentUser().getEmail() : userId;

        Map<String, Object> historyRecord = new HashMap<>();
        historyRecord.put("irrigationType", type);
        historyRecord.put("action", action);
        historyRecord.put("timestamp", Timestamp.now());
        historyRecord.put("startedBy", userEmail);
        historyRecord.put("source", source);

        db.collection("irrigation_history").add(historyRecord);
    }

    private void startScheduleListener() {
        scheduleWaterListener = db.collection("schedules").document("water")
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot != null && snapshot.exists()) {
                        String time = snapshot.getString("startTime");
                        Long duration = snapshot.getLong("durationMinutes");
                        String repeat = snapshot.getString("repeatType");
                        Boolean enabled = snapshot.getBoolean("enabled");

                        if (tvWaterScheduleTime != null && time != null) tvWaterScheduleTime.setText(time);
                        if (tvWaterScheduleDetail != null && duration != null) {
                            tvWaterScheduleDetail.setText(String.format(Locale.getDefault(), "Water Irrigation (%d mins)", duration));
                        }
                        if (tvWaterScheduleStatus != null) {
                            if (Boolean.TRUE.equals(enabled)) {
                                tvWaterScheduleStatus.setText(repeat != null ? repeat : "Enabled");
                                tvWaterScheduleStatus.setTextColor(ContextCompat.getColor(this, R.color.iot_success));
                            } else {
                                tvWaterScheduleStatus.setText("Disabled");
                                tvWaterScheduleStatus.setTextColor(Color.GRAY);
                            }
                        }
                    }
                });

        scheduleFertilizerListener = db.collection("schedules").document("fertilizer")
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot != null && snapshot.exists()) {
                        String time = snapshot.getString("startTime");
                        Long duration = snapshot.getLong("durationSeconds");
                        String repeat = snapshot.getString("repeatType");
                        Boolean enabled = snapshot.getBoolean("enabled");

                        if (tvFertilizerScheduleTime != null && time != null) tvFertilizerScheduleTime.setText(time);
                        if (tvFertilizerScheduleDetail != null && duration != null) {
                            tvFertilizerScheduleDetail.setText(String.format(Locale.getDefault(), "Fertilizer Distribution (%d secs)", duration));
                        }
                        if (tvFertilizerScheduleStatus != null) {
                            if (Boolean.TRUE.equals(enabled)) {
                                tvFertilizerScheduleStatus.setText(repeat != null ? repeat : "Enabled");
                                tvFertilizerScheduleStatus.setTextColor(ContextCompat.getColor(this, R.color.iot_success));
                            } else {
                                tvFertilizerScheduleStatus.setText("Disabled");
                                tvFertilizerScheduleStatus.setTextColor(Color.GRAY);
                            }
                        }
                    }
                });
    }

    private void startNotificationListener() {
        if (mAuth.getCurrentUser() != null) {
            notificationListener = db.collection("notifications")
                    .whereEqualTo("userId", mAuth.getCurrentUser().getUid())
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(3)
                    .addSnapshotListener((snapshots, e) -> {
                        if (e != null) return;
                        if (snapshots != null) {
                            recentAlerts.clear();
                            for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snapshots) {
                                NotificationRecord record = doc.toObject(NotificationRecord.class);
                                record.setId(doc.getId());
                                recentAlerts.add(record);
                            }
                            alertsAdapter.notifyDataSetChanged();
                            if (tvNoRecentAlerts != null) {
                                tvNoRecentAlerts.setVisibility(recentAlerts.isEmpty() ? View.VISIBLE : View.GONE);
                            }
                            updateUnreadBadge();
                        }
                    });
        }
    }

    private void updateUnreadBadge() {
        if (mAuth.getCurrentUser() == null) return;
        db.collection("notifications")
                .whereEqualTo("userId", mAuth.getCurrentUser().getUid())
                .whereEqualTo("read", false)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int count = queryDocumentSnapshots.size();
                    if (count > 0) {
                        tvNotificationBadge.setText(String.valueOf(count));
                        tvNotificationBadge.setVisibility(View.VISIBLE);
                    } else {
                        tvNotificationBadge.setVisibility(View.GONE);
                    }
                });
    }

    private void handleAlertClick(NotificationRecord notification) {
        if (!notification.isRead()) {
            db.collection("notifications").document(notification.getId()).update("read", true);
        }
    }

    private void startTelemetryListener() {
        telemetryListener = db.collection("system_data").document("live_telemetry")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !snapshot.exists()) return;

                    Double moisture = getSafeDouble(snapshot, "soilMoisture");
                    Double tank = getSafeDouble(snapshot, "waterTankLevel");
                    if (tank == null) tank = getSafeDouble(snapshot, "water_level");

                    Double temp = getSafeDouble(snapshot, "temperature");
                    Double hum = getSafeDouble(snapshot, "humidity");

                    Double light = getSafeDouble(snapshot, "lux");
                    if (light == null) light = getSafeDouble(snapshot, "lightIntensity");
                    if (light == null) light = getSafeDouble(snapshot, "light_density");

                    if (moisture != null) {
                        tvSoilMoisture.setText(String.format(Locale.getDefault(), "%.0f%%", moisture));
                        pbSoilMoisture.setProgress(moisture.intValue());
                        updateSoilMoistureStatus(moisture);
                        SensorAlertManager.getInstance(AdminDashboardActivity.this).evaluateSoilMoisture(moisture, farmLocation);

                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastHistoryLogTime >= LOG_INTERVAL_MS) {
                            logAllSensorsToHistory(moisture, temp, hum, light, tank);
                            lastHistoryLogTime = currentTime;
                        }
                    }

                    if (tank != null) {
                        tvWaterTank.setText(String.format(Locale.getDefault(), "%.0f%%", tank));
                        pbWaterTank.setProgress(tank.intValue());
                        TelemetryUtils.StatusInfo status = TelemetryUtils.getWaterLevelClassification(tank);
                        tvWaterTankStatus.setText(status.label);
                        tvWaterTankStatus.setTextColor(status.color);
                        SensorAlertManager.getInstance(AdminDashboardActivity.this).evaluateWaterLevel(tank, farmLocation);
                    }

                    if (temp != null) {
                        tvTemperature.setText(String.format(Locale.getDefault(), "%.1f°C", temp));
                        updateTemperatureIndicator(temp);
                        updateTemperatureStatus(temp);
                        SensorAlertManager.getInstance(AdminDashboardActivity.this).evaluateTemperature(temp, farmLocation);
                    }

                    if (hum != null) {
                        tvHumidity.setText(String.format(Locale.getDefault(), "%.0f%%", hum));
                        pbHumidity.setProgress(hum.intValue());
                        updateHumidityStatus(hum);
                        SensorAlertManager.getInstance(AdminDashboardActivity.this).evaluateHumidity(hum, farmLocation);
                    }

                    if (light != null) {
                        tvLightIntensity.setText(String.format(Locale.getDefault(), "%.0f%%", light));
                        pbLightIntensity.setProgress(light.intValue());
                        updateLightIntensityStatus(light);
                        SensorAlertManager.getInstance(AdminDashboardActivity.this).evaluateLightIntensity(light, farmLocation);
                    }

                    updateLastUpdated();
                });
    }

    private Double getSafeDouble(DocumentSnapshot snapshot, String field) {
        Object val = snapshot.get(field);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return null;
    }

    private void updateTemperatureIndicator(double temp) {
        float min = 0f;
        float max = 50f;
        float bias = (float) ((temp - min) / (max - min));
        bias = Math.max(0f, Math.min(1f, bias));

        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params =
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) vTempIndicator.getLayoutParams();
        params.horizontalBias = bias;
        vTempIndicator.setLayoutParams(params);
    }

    private void updateHumidityStatus(double humidity) {
        if (humidity < SensorAlertConstants.HUMIDITY_WARNING_LOW) {
            tvHumidityStatus.setText(humidity < SensorAlertConstants.HUMIDITY_CRITICAL_LOW ? "Critical Low" : "Low");
            tvHumidityStatus.setTextColor(ContextCompat.getColor(this, humidity < SensorAlertConstants.HUMIDITY_CRITICAL_LOW ? R.color.iot_error : R.color.iot_warning));
        } else if (humidity <= SensorAlertConstants.HUMIDITY_WARNING_HIGH) {
            tvHumidityStatus.setText("Normal");
            tvHumidityStatus.setTextColor(ContextCompat.getColor(this, R.color.iot_success));
        } else {
            tvHumidityStatus.setText(humidity > SensorAlertConstants.HUMIDITY_CRITICAL_HIGH ? "Critical High" : "High");
            tvHumidityStatus.setTextColor(ContextCompat.getColor(this, humidity > SensorAlertConstants.HUMIDITY_CRITICAL_HIGH ? R.color.iot_error : R.color.iot_secondary));
        }
    }

    private void updateSoilMoistureStatus(double moisture) {
        if (moisture < SensorAlertConstants.SOIL_CRITICAL_LOW) {
            tvSoilMoistureStatus.setText("Critical Dry");
            tvSoilMoistureStatus.setTextColor(ContextCompat.getColor(this, R.color.iot_error));
        } else if (moisture <= SensorAlertConstants.SOIL_WARNING_LOW) {
            tvSoilMoistureStatus.setText("Dry/Moderate");
            tvSoilMoistureStatus.setTextColor(ContextCompat.getColor(this, R.color.iot_warning));
        } else {
            tvSoilMoistureStatus.setText("Normal");
            tvSoilMoistureStatus.setTextColor(ContextCompat.getColor(this, R.color.iot_success));
        }
    }

    private void updateTemperatureStatus(double temp) {
        if (temp < SensorAlertConstants.TEMP_WARNING_LOW) {
            tvTemperatureStatus.setText(temp < SensorAlertConstants.TEMP_CRITICAL_LOW ? "Critical Low" : "Low");
            tvTemperatureStatus.setTextColor(ContextCompat.getColor(this, temp < SensorAlertConstants.TEMP_CRITICAL_LOW ? R.color.iot_error : R.color.iot_secondary));
        } else if (temp <= SensorAlertConstants.TEMP_WARNING_HIGH) {
            tvTemperatureStatus.setText("Normal");
            tvTemperatureStatus.setTextColor(ContextCompat.getColor(this, R.color.iot_success));
        } else {
            tvTemperatureStatus.setText(temp > SensorAlertConstants.TEMP_CRITICAL_HIGH ? "Critical High" : "High");
            tvTemperatureStatus.setTextColor(ContextCompat.getColor(this, temp > SensorAlertConstants.TEMP_CRITICAL_HIGH ? R.color.iot_error : R.color.iot_warning));
        }
    }

    private void updateLightIntensityStatus(double light) {
        if (light > SensorAlertConstants.LIGHT_CRITICAL_HIGH) {
            tvLightIntensityStatus.setText("Extreme Light");
            tvLightIntensityStatus.setTextColor(ContextCompat.getColor(this, R.color.iot_error));
        } else if (light > SensorAlertConstants.LIGHT_WARNING_HIGH) {
            tvLightIntensityStatus.setText("Excessive Light");
            tvLightIntensityStatus.setTextColor(ContextCompat.getColor(this, R.color.iot_error));
        } else {
            tvLightIntensityStatus.setText("Normal");
            tvLightIntensityStatus.setTextColor(ContextCompat.getColor(this, R.color.iot_success));
        }
    }

    private void updateLastUpdated() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String currentTime = sdf.format(new java.util.Date());
        tvLastUpdated.setText("Last updated: " + currentTime);
        tvLastUpdated.setVisibility(View.VISIBLE);
    }

    private void logAllSensorsToHistory(Double moisture, Double temp, Double hum, Double light, Double tank) {
        Timestamp now = Timestamp.now();
        if (moisture != null) db.collection("History").add(new HistoryRecord("moisture", moisture, "%", "Prototype Zone", "Admin Terminal", now));
        if (temp != null) db.collection("History").add(new HistoryRecord("temperature", temp, "°C", "Prototype Zone", "Admin Terminal", now));
        if (hum != null) db.collection("History").add(new HistoryRecord("humidity", hum, "%", "Prototype Zone", "Admin Terminal", now));
        if (light != null) db.collection("History").add(new HistoryRecord("light", light, "%", "Prototype Zone", "Admin Terminal", now));
        if (tank != null) db.collection("History").add(new HistoryRecord("waterlevel", tank, "%", "Prototype Zone", "Admin Terminal", now));
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (telemetryListener != null) telemetryListener.remove();
        if (notificationListener != null) notificationListener.remove();
        if (deviceControlListener != null) deviceControlListener.remove();
        if (scheduleWaterListener != null) scheduleWaterListener.remove();
        if (scheduleFertilizerListener != null) scheduleFertilizerListener.remove();
    }

    private void fetchUserName() {
        if (mAuth.getCurrentUser() != null) {
            String userId = mAuth.getCurrentUser().getUid();
            db.collection("users").document(userId).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String fullName = documentSnapshot.getString("fullName");
                            if (fullName != null && !fullName.isEmpty()) {
                                String firstName = fullName.split(" ")[0];
                                tvUserName.setText(firstName);
                            }

                            String location = documentSnapshot.getString("farmLocation");
                            if (location != null) {
                                farmLocation = location;
                            }
                        }
                    });
        }
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.logout)
                .setMessage(R.string.logout_confirmation)
                .setPositiveButton(R.string.logout, (dialog, which) -> {
                    mAuth.signOut();
                    Intent intent = new Intent(AdminDashboardActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
