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

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DashboardActivity extends AppCompatActivity {

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

    // Schedule Runner State
    private Handler scheduleRunnerHandler;
    private Runnable scheduleRunnerRunnable;

    private boolean waterScheduleEnabled = false;
    private String waterScheduleTime = "";
    private long waterScheduleDurationMins = 5;
    private String waterRepeatType = "Every Day";
    private String lastExecutedWaterDate = "";
    private String lastExecutedWaterMinute = "";
    private boolean isWaterScheduleRunningCurrently = false;

    private boolean fertScheduleEnabled = false;
    private String fertScheduleTime = "";
    private long fertScheduleDurationSecs = 30;
    private String fertRepeatType = "Every 7 Days";
    private String lastExecutedFertilizerDate = "";
    private String lastExecutedFertilizerMinute = "";
    private boolean isFertScheduleRunningCurrently = false;

    private String farmLocation = "Unknown Location";
    private long lastHistoryLogTime = 0;
    private static final long LOG_INTERVAL_MS = 60000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseManager.getInstance().getAuth();
        db = FirebaseManager.getInstance().getDb();

        if (mAuth.getCurrentUser() == null) {
            redirectToLogin();
            return;
        }

        setContentView(R.layout.activity_dashboard);

        // Initialize UI Elements
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
            btnConfigureSchedule.setOnClickListener(v -> startActivity(new Intent(DashboardActivity.this, ScheduleActivity.class)));
        }

        View btnAiAssistantTop = findViewById(R.id.btnAiAssistantTop);
        if (btnAiAssistantTop != null) {
            btnAiAssistantTop.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, AiAssistantActivity.class);
                intent.putExtra("is_admin", false);
                startActivity(intent);
            });
        }

        View btnAskAi = findViewById(R.id.btnAskAi);
        if (btnAskAi != null) {
            btnAskAi.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, AiAssistantActivity.class);
                intent.putExtra("is_admin", false);
                startActivity(intent);
            });
        }

        View cardAiAssistant = findViewById(R.id.cardAiAssistant);
        if (cardAiAssistant != null) {
            cardAiAssistant.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, AiAssistantActivity.class);
                intent.putExtra("is_admin", false);
                startActivity(intent);
            });
        }

        fetchUserName();
        initializeDatabaseStructure();
        initializeDeviceControl();
        startTelemetryListener();
        startDeviceControlListener();
        startScheduleListener();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_dashboard), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        setupBottomNavigation();
        FirebaseManager.getInstance().updateFcmToken();

        findViewById(R.id.btnNotifications).setOnClickListener(v -> startActivity(new Intent(this, NotificationsActivity.class)));
        findViewById(R.id.btnLogout).setOnClickListener(v -> showLogoutConfirmation());

        startNotificationListener();
        requestNotificationPermission();
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
                    if (e != null) {
                        android.util.Log.e("AgriFlow", "Device control listen failed", e);
                        return;
                    }
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
                tvWaterPumpStatus.setTextColor(Color.parseColor("#2E7D32"));
            } else if ("SCHEDULED".equalsIgnoreCase(currentWaterState)) {
                tvWaterPumpStatus.setText("SCHEDULED");
                tvWaterPumpStatus.setTextColor(Color.parseColor("#F57C00"));
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
                tvFertilizerPumpStatus.setTextColor(Color.parseColor("#2E7D32"));
            } else if ("SCHEDULED".equalsIgnoreCase(currentFertilizerState)) {
                tvFertilizerPumpStatus.setText("SCHEDULED");
                tvFertilizerPumpStatus.setTextColor(Color.parseColor("#F57C00"));
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
                if (isChecked) {
                    handleStartWater();
                } else {
                    handleStopWater();
                }
            });
        }

        if (switchFertilizerPump != null) {
            switchFertilizerPump.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    handleStartFertilizer();
                } else {
                    handleStopFertilizer();
                }
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
        boolean isPumpOn = "ON".equalsIgnoreCase(newState) || "RUNNING".equalsIgnoreCase(newState);

        Map<String, Object> update = new HashMap<>();
        update.put("waterPumpState", newState);
        update.put("waterPump", isPumpOn);
        update.put("water_pump", isPumpOn);
        update.put("lastUpdated", Timestamp.now());
        update.put("updatedBy", userId);

        db.collection("system_data").document("device_control").set(update, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    db.collection("system_data").document("live_telemetry").set(update, com.google.firebase.firestore.SetOptions.merge());
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
        boolean isPumpOn = "ON".equalsIgnoreCase(newState) || "RUNNING".equalsIgnoreCase(newState);

        Map<String, Object> update = new HashMap<>();
        update.put("fertilizerPumpState", newState);
        update.put("fertilizerPump", isPumpOn);
        update.put("fertilizer_pump", isPumpOn);
        update.put("lastUpdated", Timestamp.now());
        update.put("updatedBy", userId);

        db.collection("system_data").document("device_control").set(update, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    db.collection("system_data").document("live_telemetry").set(update, com.google.firebase.firestore.SetOptions.merge());
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
                        String lastExec = snapshot.getString("lastExecutedDate");

                        if (enabled != null) waterScheduleEnabled = enabled;
                        if (time != null) waterScheduleTime = time;
                        if (duration != null) waterScheduleDurationMins = duration;
                        if (repeat != null) waterRepeatType = repeat;
                        if (lastExec != null) lastExecutedWaterDate = lastExec;

                        if (tvWaterScheduleTime != null && time != null) tvWaterScheduleTime.setText(time);
                        if (tvWaterScheduleDetail != null && duration != null) {
                            tvWaterScheduleDetail.setText(String.format(Locale.getDefault(), "Water Irrigation (%d mins)", duration));
                        }
                        if (tvWaterScheduleStatus != null) {
                            if (Boolean.TRUE.equals(enabled)) {
                                tvWaterScheduleStatus.setText(repeat != null ? repeat : "Enabled");
                                tvWaterScheduleStatus.setTextColor(ContextCompat.getColor(this, R.color.primary_dark_green));
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
                        String lastExec = snapshot.getString("lastExecutedDate");

                        if (enabled != null) fertScheduleEnabled = enabled;
                        if (time != null) fertScheduleTime = time;
                        if (duration != null) fertScheduleDurationSecs = duration;
                        if (repeat != null) fertRepeatType = repeat;
                        if (lastExec != null) lastExecutedFertilizerDate = lastExec;

                        if (tvFertilizerScheduleTime != null && time != null) tvFertilizerScheduleTime.setText(time);
                        if (tvFertilizerScheduleDetail != null && duration != null) {
                            tvFertilizerScheduleDetail.setText(String.format(Locale.getDefault(), "Fertilizer Distribution (%d secs)", duration));
                        }
                        if (tvFertilizerScheduleStatus != null) {
                            if (Boolean.TRUE.equals(enabled)) {
                                tvFertilizerScheduleStatus.setText(repeat != null ? repeat : "Enabled");
                                tvFertilizerScheduleStatus.setTextColor(ContextCompat.getColor(this, R.color.primary_dark_green));
                            } else {
                                tvFertilizerScheduleStatus.setText("Disabled");
                                tvFertilizerScheduleStatus.setTextColor(Color.GRAY);
                            }
                        }
                    }
                });

        startAutomatedScheduleCheckRunner();
    }

    private void startAutomatedScheduleCheckRunner() {
        if (scheduleRunnerHandler != null) return;
        scheduleRunnerHandler = new Handler(Looper.getMainLooper());
        scheduleRunnerRunnable = new Runnable() {
            @Override
            public void run() {
                checkAndExecuteSchedules();
                if (scheduleRunnerHandler != null) {
                    scheduleRunnerHandler.postDelayed(this, 15000);
                }
            }
        };
        scheduleRunnerHandler.post(scheduleRunnerRunnable);
    }

    private boolean shouldRunScheduleToday(String lastExecutedDateStr, String repeatType) {
        if (lastExecutedDateStr == null || lastExecutedDateStr.isEmpty()) {
            return true;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        try {
            Date lastDate = sdf.parse(lastExecutedDateStr);
            Date todayDate = sdf.parse(sdf.format(new Date()));
            if (lastDate == null || todayDate == null) return true;

            long diffInMillis = todayDate.getTime() - lastDate.getTime();
            long daysDiff = diffInMillis / (24 * 60 * 60 * 1000L);

            int requiredDays = 1;
            if (repeatType != null) {
                String lower = repeatType.toLowerCase(Locale.US);
                if (lower.contains("2 days")) {
                    requiredDays = 2;
                } else if (lower.contains("3 days")) {
                    requiredDays = 3;
                } else if (lower.contains("7 days")) {
                    requiredDays = 7;
                } else if (lower.contains("14 days")) {
                    requiredDays = 14;
                } else {
                    requiredDays = 1;
                }
            }
            return daysDiff >= requiredDays;
        } catch (Exception e) {
            return true;
        }
    }

    private void checkAndExecuteSchedules() {
        SimpleDateFormat sdfTime = new SimpleDateFormat("hh:mm a", Locale.US);
        SimpleDateFormat sdfMinute = new SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.US);
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        Date now = new Date();
        String currentTimeFormatted = sdfTime.format(now);
        String currentMinuteFormatted = sdfMinute.format(now);
        String currentDateFormatted = sdfDate.format(now);

        // Water Schedule Check
        if (waterScheduleEnabled && !waterScheduleTime.isEmpty()) {
            if (currentTimeFormatted.equalsIgnoreCase(waterScheduleTime.trim())) {
                if (!isWaterScheduleRunningCurrently && !currentMinuteFormatted.equals(lastExecutedWaterMinute)) {
                    if (shouldRunScheduleToday(lastExecutedWaterDate, waterRepeatType)) {
                        isWaterScheduleRunningCurrently = true;
                        lastExecutedWaterMinute = currentMinuteFormatted;
                        lastExecutedWaterDate = currentDateFormatted;

                        db.collection("schedules").document("water").update("lastExecutedDate", currentDateFormatted);
                        Log.d("AgriFlow", "⏰ Water Schedule Match! Turning Water Pump ON for " + waterScheduleDurationMins + " mins.");
                        
                        Map<String, Object> waterStartUpdate = new HashMap<>();
                        waterStartUpdate.put("waterPumpState", "RUNNING");
                        waterStartUpdate.put("waterPump", true);
                        waterStartUpdate.put("water_pump", true);
                        waterStartUpdate.put("lastUpdated", Timestamp.now());
                        db.collection("system_data").document("device_control").update(waterStartUpdate);
                        db.collection("system_data").document("live_telemetry").update(waterStartUpdate);

                        logIrrigationEvent("Water", "RUNNING", "Automated Schedule");

                        AppNotificationManager.getInstance(this).triggerAlertNotification(
                                "Watering Schedule Active",
                                "Scheduled water irrigation started at " + currentTimeFormatted + " for " + waterScheduleDurationMins + " minutes.",
                                "water",
                                0, 0, "Normal"
                        );

                        long durationMillis = waterScheduleDurationMins * 60 * 1000L;
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            Map<String, Object> waterStopUpdate = new HashMap<>();
                            waterStopUpdate.put("waterPumpState", "SCHEDULED");
                            waterStopUpdate.put("waterPump", false);
                            waterStopUpdate.put("water_pump", false);
                            waterStopUpdate.put("lastUpdated", Timestamp.now());
                            db.collection("system_data").document("device_control").update(waterStopUpdate);
                            db.collection("system_data").document("live_telemetry").update(waterStopUpdate);

                            isWaterScheduleRunningCurrently = false;
                            logIrrigationEvent("Water", "COMPLETED", "Automated Schedule");
                            AppNotificationManager.getInstance(DashboardActivity.this).triggerAlertNotification(
                                    "Watering Schedule Completed",
                                    "Scheduled water irrigation completed.",
                                    "water",
                                    0, 0, "Normal"
                            );
                        }, durationMillis);
                    }
                }
            }
        }

        // Fertilizer Schedule Check
        if (fertScheduleEnabled && !fertScheduleTime.isEmpty()) {
            if (currentTimeFormatted.equalsIgnoreCase(fertScheduleTime.trim())) {
                if (!isFertScheduleRunningCurrently && !currentMinuteFormatted.equals(lastExecutedFertilizerMinute)) {
                    if (shouldRunScheduleToday(lastExecutedFertilizerDate, fertRepeatType)) {
                        isFertScheduleRunningCurrently = true;
                        lastExecutedFertilizerMinute = currentMinuteFormatted;
                        lastExecutedFertilizerDate = currentDateFormatted;

                        db.collection("schedules").document("fertilizer").update("lastExecutedDate", currentDateFormatted);
                        Log.d("AgriFlow", "⏰ Fertilizer Schedule Match! Turning Fertilizer Pump ON for " + fertScheduleDurationSecs + " secs.");
                        
                        Map<String, Object> fertStartUpdate = new HashMap<>();
                        fertStartUpdate.put("fertilizerPumpState", "RUNNING");
                        fertStartUpdate.put("fertilizerPump", true);
                        fertStartUpdate.put("fertilizer_pump", true);
                        fertStartUpdate.put("lastUpdated", Timestamp.now());
                        db.collection("system_data").document("device_control").update(fertStartUpdate);
                        db.collection("system_data").document("live_telemetry").update(fertStartUpdate);

                        logIrrigationEvent("Fertilizer", "RUNNING", "Automated Schedule");

                        AppNotificationManager.getInstance(this).triggerAlertNotification(
                                "Fertilizer Schedule Active",
                                "Scheduled fertilizer distribution started at " + currentTimeFormatted + " for " + fertScheduleDurationSecs + " seconds.",
                                "sunlight",
                                0, 0, "Normal"
                        );

                        long durationMillis = fertScheduleDurationSecs * 1000L;
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            Map<String, Object> fertStopUpdate = new HashMap<>();
                            fertStopUpdate.put("fertilizerPumpState", "SCHEDULED");
                            fertStopUpdate.put("fertilizerPump", false);
                            fertStopUpdate.put("fertilizer_pump", false);
                            fertStopUpdate.put("lastUpdated", Timestamp.now());
                            db.collection("system_data").document("device_control").update(fertStopUpdate);
                            db.collection("system_data").document("live_telemetry").update(fertStopUpdate);

                            isFertScheduleRunningCurrently = false;
                            logIrrigationEvent("Fertilizer", "COMPLETED", "Automated Schedule");
                            AppNotificationManager.getInstance(DashboardActivity.this).triggerAlertNotification(
                                    "Fertilizer Schedule Completed",
                                    "Scheduled fertilizer distribution completed.",
                                    "sunlight",
                                    0, 0, "Normal"
                            );
                        }, durationMillis);
                    }
                }
            }
        }
    }

    private void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
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

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_home);

            bottomNavigationView.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_home) {
                    return true;
                } else if (itemId == R.id.nav_profile) {
                    startActivity(new Intent(DashboardActivity.this, ProfileActivity.class));
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    finish();
                    return true;
                } else if (itemId == R.id.nav_manage) {
                    startActivity(new Intent(DashboardActivity.this, FarmerListActivity.class));
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    finish();
                    return true;
                } else if (itemId == R.id.nav_history) {
                    startActivity(new Intent(DashboardActivity.this, MoistureHistoryActivity.class));
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    finish();
                    return true;
                } else if (itemId == R.id.nav_schedule) {
                    startActivity(new Intent(DashboardActivity.this, ScheduleActivity.class));
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    finish();
                    return true;
                }
                return false;
            });
        }
    }

    private void fetchUserName() {
        if (mAuth.getCurrentUser() != null) {
            String userId = mAuth.getCurrentUser().getUid();
            db.collection("users").document(userId).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String fullName = documentSnapshot.getString("fullName");
                            String role = documentSnapshot.getString("role");

                            if (fullName != null && !fullName.isEmpty()) {
                                String firstName = fullName.split(" ")[0];
                                tvUserName.setText(firstName);
                                tvUserName.setVisibility(View.VISIBLE);
                            }

                            String location = documentSnapshot.getString("farmLocation");
                            if (location != null) {
                                farmLocation = location;
                            }

                            updateBottomNavigationForRole(role);
                        }
                    });
        }
    }

    private void updateBottomNavigationForRole(String role) {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        if (bottomNavigationView != null && "admin".equalsIgnoreCase(role)) {
            bottomNavigationView.getMenu().clear();
            bottomNavigationView.inflateMenu(R.menu.admin_bottom_nav_menu);
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        }
    }

    private void initializeDatabaseStructure() {
        DocumentReference telemetryRef = db.collection("system_data").document("live_telemetry");
        telemetryRef.get().addOnSuccessListener(documentSnapshot -> {
            if (!documentSnapshot.exists()) {
                Map<String, Object> initialData = new HashMap<>();
                initialData.put("temperature", 25.0);
                initialData.put("humidity", 60.0);
                initialData.put("soilMoisture", 45.0);
                initialData.put("waterTankLevel", 0.0);
                initialData.put("light_density", 0.0);
                initialData.put("last_updated", Timestamp.now());
                telemetryRef.set(initialData);
            }
        });
    }

    private void updateTemperatureIndicator(double temp) {
        if (vTempIndicator == null) return;
        float min = 0f;
        float max = 50f;
        float bias = (float) ((temp - min) / (max - min));
        bias = Math.max(0f, Math.min(1f, bias));

        if (vTempIndicator.getLayoutParams() instanceof androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) {
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params =
                    (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) vTempIndicator.getLayoutParams();
            params.horizontalBias = bias;
            vTempIndicator.setLayoutParams(params);
        }
    }

    private void updateHumidityStatus(double humidity) {
        if (humidity < SensorAlertConstants.HUMIDITY_WARNING_LOW) {
            tvHumidityStatus.setText(humidity < SensorAlertConstants.HUMIDITY_CRITICAL_LOW ? "Critical Low" : "Low");
            tvHumidityStatus.setTextColor(Color.parseColor(humidity < SensorAlertConstants.HUMIDITY_CRITICAL_LOW ? "#E53935" : "#FF8A65"));
        } else if (humidity <= SensorAlertConstants.HUMIDITY_WARNING_HIGH) {
            tvHumidityStatus.setText("Normal");
            tvHumidityStatus.setTextColor(ContextCompat.getColor(this, R.color.primary_dark_green));
        } else {
            tvHumidityStatus.setText(humidity > SensorAlertConstants.HUMIDITY_CRITICAL_HIGH ? "Critical High" : "High");
            tvHumidityStatus.setTextColor(Color.parseColor(humidity > SensorAlertConstants.HUMIDITY_CRITICAL_HIGH ? "#B71C1C" : "#448AFF"));
        }
    }

    private void updateSoilMoistureStatus(double moisture) {
        if (moisture < SensorAlertConstants.SOIL_CRITICAL_LOW) {
            tvSoilMoistureStatus.setText("Critical Dry");
            tvSoilMoistureStatus.setTextColor(Color.parseColor("#B71C1C"));
        } else if (moisture <= SensorAlertConstants.SOIL_WARNING_LOW) {
            tvSoilMoistureStatus.setText("Dry/Moderate");
            tvSoilMoistureStatus.setTextColor(Color.parseColor("#FB8C00"));
        } else {
            tvSoilMoistureStatus.setText("Normal");
            tvSoilMoistureStatus.setTextColor(Color.parseColor("#43A047"));
        }
    }

    private void updateTemperatureStatus(double temp) {
        if (temp < SensorAlertConstants.TEMP_WARNING_LOW) {
            tvTemperatureStatus.setText(temp < SensorAlertConstants.TEMP_CRITICAL_LOW ? "Critical Low" : "Low");
            tvTemperatureStatus.setTextColor(Color.parseColor(temp < SensorAlertConstants.TEMP_CRITICAL_LOW ? "#E53935" : "#64B5F6"));
        } else if (temp <= SensorAlertConstants.TEMP_WARNING_HIGH) {
            tvTemperatureStatus.setText("Normal");
            tvTemperatureStatus.setTextColor(ContextCompat.getColor(this, R.color.primary_dark_green));
        } else {
            tvTemperatureStatus.setText(temp > SensorAlertConstants.TEMP_CRITICAL_HIGH ? "Critical High" : "High");
            tvTemperatureStatus.setTextColor(Color.parseColor(temp > SensorAlertConstants.TEMP_CRITICAL_HIGH ? "#B71C1C" : "#FF8A65"));
        }
    }

    private void updateLightIntensityStatus(double light) {
        if (light > SensorAlertConstants.LIGHT_CRITICAL_HIGH) {
            tvLightIntensityStatus.setText("Extreme Light");
            tvLightIntensityStatus.setTextColor(Color.parseColor("#B71C1C"));
        } else if (light > SensorAlertConstants.LIGHT_WARNING_HIGH) {
            tvLightIntensityStatus.setText("Excessive Light");
            tvLightIntensityStatus.setTextColor(Color.parseColor("#E53935"));
        } else {
            tvLightIntensityStatus.setText("Normal");
            tvLightIntensityStatus.setTextColor(ContextCompat.getColor(this, R.color.primary_dark_green));
        }
    }

    private void updateLastUpdated() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String currentTime = sdf.format(new java.util.Date());
        tvLastUpdated.setText("Last updated: " + currentTime);
        tvLastUpdated.setVisibility(View.VISIBLE);
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
                        SensorAlertManager.getInstance(DashboardActivity.this).evaluateSoilMoisture(moisture, farmLocation);

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
                        SensorAlertManager.getInstance(DashboardActivity.this).evaluateWaterLevel(tank, farmLocation);
                    }

                    if (temp != null) {
                        tvTemperature.setText(String.format(Locale.getDefault(), "%.1f°C", temp));
                        updateTemperatureIndicator(temp);
                        updateTemperatureStatus(temp);
                        SensorAlertManager.getInstance(DashboardActivity.this).evaluateTemperature(temp, farmLocation);
                    }

                    if (hum != null) {
                        tvHumidity.setText(String.format(Locale.getDefault(), "%.0f%%", hum));
                        pbHumidity.setProgress(hum.intValue());
                        updateHumidityStatus(hum);
                        SensorAlertManager.getInstance(DashboardActivity.this).evaluateHumidity(hum, farmLocation);
                    }

                    if (light != null) {
                        tvLightIntensity.setText(String.format(Locale.getDefault(), "%.0f%%", light));
                        pbLightIntensity.setProgress(light.intValue());
                        updateLightIntensityStatus(light);
                        SensorAlertManager.getInstance(DashboardActivity.this).evaluateLightIntensity(light, farmLocation);
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

    private void logAllSensorsToHistory(Double moisture, Double temp, Double hum, Double light, Double tank) {
        Timestamp now = Timestamp.now();
        if (moisture != null) db.collection("History").add(new HistoryRecord("moisture", moisture, "%", "Prototype Zone", "ESP32-S1", now));
        if (temp != null) db.collection("History").add(new HistoryRecord("temperature", temp, "°C", "Prototype Zone", "ESP32-S1", now));
        if (hum != null) db.collection("History").add(new HistoryRecord("humidity", hum, "%", "Prototype Zone", "ESP32-S1", now));
        if (light != null) db.collection("History").add(new HistoryRecord("light", light, "%", "Prototype Zone", "ESP32-S1", now));
        if (tank != null) db.collection("History").add(new HistoryRecord("waterlevel", tank, "%", "Prototype Zone", "ESP32-S1", now));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scheduleRunnerHandler != null && scheduleRunnerRunnable != null) {
            scheduleRunnerHandler.removeCallbacks(scheduleRunnerRunnable);
        }
        if (telemetryListener != null) telemetryListener.remove();
        if (notificationListener != null) notificationListener.remove();
        if (deviceControlListener != null) deviceControlListener.remove();
        if (scheduleWaterListener != null) scheduleWaterListener.remove();
        if (scheduleFertilizerListener != null) scheduleFertilizerListener.remove();
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.logout)
                .setMessage(R.string.logout_confirmation)
                .setPositiveButton(R.string.logout, (dialog, which) -> performLogout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performLogout() {
        mAuth.signOut();
        Intent intent = new Intent(DashboardActivity.this, MainActivity.class);
        intent.putExtra("logout_message", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
