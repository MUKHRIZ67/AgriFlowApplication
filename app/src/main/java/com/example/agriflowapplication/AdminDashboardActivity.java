package com.example.agriflowapplication;

import android.content.Intent;
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

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AdminDashboardActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TextView tvUserName, tvLastUpdated, tvNotificationBadge;
    private ListenerRegistration telemetryListener, notificationListener;
    private NotificationAdapter alertsAdapter;
    private List<NotificationRecord> recentAlerts;
    private View tvNoRecentAlerts;

    // Telemetry Views
    private TextView tvSoilMoisture, tvSoilMoistureStatus, tvWaterTank, tvWaterTankStatus, tvTemperature, tvTemperatureStatus, tvHumidity, tvHumidityStatus, tvLightIntensity, tvLightIntensityStatus;
    private ProgressBar pbSoilMoisture, pbWaterTank, pbHumidity, pbLightIntensity;
    private View vTempIndicator;
    private MaterialSwitch switchWaterPump, switchFertilizerPump;
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
        switchWaterPump = findViewById(R.id.switchWaterPump);
        switchFertilizerPump = findViewById(R.id.switchFertilizerPump);

        fetchUserName();
        startTelemetryListener();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_admin_dashboard), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        // Bottom Navigation
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.nav_home);
        FirebaseManager.getInstance().updateFcmToken();

        // Notification Button Listener
        findViewById(R.id.btnNotifications).setOnClickListener(v -> startActivity(new Intent(this, NotificationsActivity.class)));

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
            } else if (itemId == R.id.nav_profile) {
                startActivity(new Intent(AdminDashboardActivity.this, AdminProfileActivity.class));
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                finish();
                return true;
            }
            return false;
        });

        // Logout
        findViewById(R.id.btnLogout).setOnClickListener(v -> showLogoutConfirmation());
        
        startNotificationListener();
    }

    private void startNotificationListener() {
        if (mAuth.getCurrentUser() != null) {
            notificationListener = db.collection("notifications")
                    .whereEqualTo("userId", mAuth.getCurrentUser().getUid())
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(3)
                    .addSnapshotListener((snapshots, e) -> {
                        if (e != null) {
                            android.util.Log.e("AgriFlow", "Notification listen failed", e);
                            return;
                        }
                        if (snapshots != null) {
                            recentAlerts.clear();
                            for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snapshots) {
                                NotificationRecord record = doc.toObject(NotificationRecord.class);
                                record.setId(doc.getId());
                                recentAlerts.add(record);
                            }
                            alertsAdapter.notifyDataSetChanged();
                            tvNoRecentAlerts.setVisibility(recentAlerts.isEmpty() ? View.VISIBLE : View.GONE);
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
        android.util.Log.d("AgriFlow_Debug", "Starting Admin Telemetry Listener...");
        telemetryListener = db.collection("system_data").document("live_telemetry")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        android.util.Log.e("AgriFlow_Debug", "Listen failed.", e);
                        tvWaterTank.setText("Error");
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        android.util.Log.d("AgriFlow_Debug", "Admin New Data Received: " + snapshot.getData());
                        
                        // Use a safe way to get numbers (handles both Long and Double from Firestore)
                        Double moisture = getSafeDouble(snapshot, "soilMoisture");
                        Double tank = getSafeDouble(snapshot, "waterTankLevel");
                        if (tank == null) tank = getSafeDouble(snapshot, "water_level");

                        Double temp = getSafeDouble(snapshot, "temperature");
                        Double hum = getSafeDouble(snapshot, "humidity");
                        
                        Double light = getSafeDouble(snapshot, "lux");
                        if (light == null) light = getSafeDouble(snapshot, "lightIntensity");
                        if (light == null) light = getSafeDouble(snapshot, "light_density");

                        Boolean pump1 = snapshot.getBoolean("waterPump");
                        Boolean pump2 = snapshot.getBoolean("fertilizerPump");

                        if (moisture != null) {
                            tvSoilMoisture.setText(String.format(java.util.Locale.getDefault(), "%.0f%%", moisture));
                            pbSoilMoisture.setProgress(moisture.intValue());
                            updateSoilMoistureStatus(moisture);

                            // Trigger Alert Check
                            SensorAlertManager.getInstance(AdminDashboardActivity.this).evaluateSoilMoisture(moisture, farmLocation);

                            // Log to history if interval passed
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastHistoryLogTime >= LOG_INTERVAL_MS) {
                                logAllSensorsToHistory(moisture, temp, hum, light, tank);
                                lastHistoryLogTime = currentTime;
                            }
                        }
                        
                        if (tank != null) {
                            tvWaterTank.setText(String.format(java.util.Locale.getDefault(), "%.0f%%", tank));
                            pbWaterTank.setProgress(tank.intValue());

                            TelemetryUtils.StatusInfo status = TelemetryUtils.getWaterLevelClassification(tank);
                            tvWaterTankStatus.setText(status.label);
                            tvWaterTankStatus.setTextColor(status.color);

                            // Trigger Notification Check
                            SensorAlertManager.getInstance(AdminDashboardActivity.this).evaluateWaterLevel(tank, farmLocation);
                        } else {
                            tvWaterTank.setText("N/A");
                            tvWaterTankStatus.setText("Live water-level data is unavailable");
                            tvWaterTankStatus.setTextColor(android.graphics.Color.GRAY);
                        }

                        if (temp != null) {
                            tvTemperature.setText(String.format(java.util.Locale.getDefault(), "%.1f°C", temp));
                            updateTemperatureIndicator(temp);
                            updateTemperatureStatus(temp);

                            // Trigger Alert Check
                            SensorAlertManager.getInstance(AdminDashboardActivity.this).evaluateTemperature(temp, farmLocation);
                        }
                        if (hum != null) {
                            tvHumidity.setText(String.format(java.util.Locale.getDefault(), "%.0f%%", hum));
                            pbHumidity.setProgress(hum.intValue());
                            updateHumidityStatus(hum);

                            // Trigger Alert Check
                            SensorAlertManager.getInstance(AdminDashboardActivity.this).evaluateHumidity(hum, farmLocation);
                        }
                        if (light != null) {
                            tvLightIntensity.setText(String.format(java.util.Locale.getDefault(), "%.0f%%", light));
                            pbLightIntensity.setProgress(light.intValue());
                            
                            updateLightIntensityStatus(light);
                            
                            // Trigger Sunlight Notification Check
                            SensorAlertManager.getInstance(AdminDashboardActivity.this).evaluateLightIntensity(light, farmLocation);
                        }
                        
                        updateLastUpdated();

                        if (pump1 != null) {
                            switchWaterPump.setOnCheckedChangeListener(null);
                            switchWaterPump.setChecked(pump1);
                            setupPumpListeners();
                        }
                        if (pump2 != null) {
                            switchFertilizerPump.setOnCheckedChangeListener(null);
                            switchFertilizerPump.setChecked(pump2);
                            setupPumpListeners();
                        }
                    } else {
                        android.util.Log.w("AgriFlow_Debug", "Admin Snapshot is null or doesn't exist");
                        tvWaterTank.setText("No Data");
                    }
                });

        setupPumpListeners();
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
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault());
        String currentTime = sdf.format(new java.util.Date());
        tvLastUpdated.setText("Last updated: " + currentTime);
        tvLastUpdated.setVisibility(View.VISIBLE);
    }

    private void setupPumpListeners() {
        switchWaterPump.setOnCheckedChangeListener((buttonView, isChecked) -> 
            db.collection("system_data").document("live_telemetry").update("waterPump", isChecked)
        );

        switchFertilizerPump.setOnCheckedChangeListener((buttonView, isChecked) -> 
            db.collection("system_data").document("live_telemetry").update("fertilizerPump", isChecked)
        );
    }

    private void logAllSensorsToHistory(Double moisture, Double temp, Double hum, Double light, Double tank) {
        com.google.firebase.Timestamp now = com.google.firebase.Timestamp.now();
        if (moisture != null) {
            db.collection("History").add(new HistoryRecord("moisture", moisture, "%", "Prototype Zone", "Admin Terminal", now));
        }
        if (temp != null) {
            db.collection("History").add(new HistoryRecord("temperature", temp, "°C", "Prototype Zone", "Admin Terminal", now));
        }
        if (hum != null) {
            db.collection("History").add(new HistoryRecord("humidity", hum, "%", "Prototype Zone", "Admin Terminal", now));
        }
        if (light != null) {
            db.collection("History").add(new HistoryRecord("light", light, "%", "Prototype Zone", "Admin Terminal", now));
        }
        if (tank != null) {
            db.collection("History").add(new HistoryRecord("waterlevel", tank, "%", "Prototype Zone", "Admin Terminal", now));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.nav_home);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (telemetryListener != null) {
            telemetryListener.remove();
        }
        if (notificationListener != null) {
            notificationListener.remove();
        }
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
