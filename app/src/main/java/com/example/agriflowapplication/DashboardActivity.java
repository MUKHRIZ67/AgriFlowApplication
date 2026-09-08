package com.example.agriflowapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class DashboardActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TextView tvUserName, tvLastUpdated, tvNotificationBadge;
    private ListenerRegistration telemetryListener, notificationListener;
    private NotificationAdapter alertsAdapter;
    private List<NotificationRecord> recentAlerts;
    private View tvNoRecentAlerts;

    // Telemetry Views
    private TextView tvSoilMoisture, tvSoilMoistureStatus, tvWaterTank, tvWaterTankStatus, tvTemperature, tvTemperatureStatus, tvHumidity, tvHumidityStatus, tvLightIntensity, tvLightIntensityStatus;
    private android.widget.ProgressBar pbSoilMoisture, pbWaterTank, pbHumidity, pbLightIntensity;
    private android.view.View vTempIndicator;
    private com.google.android.material.materialswitch.MaterialSwitch switchWaterPump, switchFertilizerPump;
    private String farmLocation = "Unknown Location";

    private long lastHistoryLogTime = 0;
    private static final long LOG_INTERVAL_MS = 60000; // Log real data at most once per minute

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseManager.getInstance().getAuth();
        db = FirebaseManager.getInstance().getDb();

        // Session Protection
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
        
        switchWaterPump = findViewById(R.id.switchWaterPump);
        switchFertilizerPump = findViewById(R.id.switchFertilizerPump);

        fetchUserName();
        initializeDatabaseStructure(); // Automatically create the document if it's missing
        startTelemetryListener();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_dashboard), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        setupBottomNavigation();
        FirebaseManager.getInstance().updateFcmToken();

        // Notification Button Listener
        findViewById(R.id.btnNotifications).setOnClickListener(v -> startActivity(new Intent(this, NotificationsActivity.class)));

        findViewById(R.id.btnLogout).setOnClickListener(v -> showLogoutConfirmation());

        // Instant Log Button (Hidden, click AgriFlow logo 5 times)
        final int[] logoClicks = {0};
        findViewById(R.id.tvAppName).setOnClickListener(v -> {
            logoClicks[0]++;
            if (logoClicks[0] >= 5) {
                logoClicks[0] = 0;
                forceLogToHistory();
            }
        });
        
        startNotificationListener();
        requestNotificationPermission();
    }

    private void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
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
                        if (e != null) {
                            android.util.Log.e("AgriFlow", "Notification listen failed", e);
                            return;
                        }
                        if (snapshots != null) {
                            recentAlerts.clear();
                            int unreadCount = 0;
                            for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snapshots) {
                                NotificationRecord record = doc.toObject(NotificationRecord.class);
                                record.setId(doc.getId());
                                recentAlerts.add(record);
                                if (!record.isRead()) unreadCount++;
                            }
                            alertsAdapter.notifyDataSetChanged();
                            tvNoRecentAlerts.setVisibility(recentAlerts.isEmpty() ? View.VISIBLE : View.GONE);
                            
                            // For badge, we might want a separate query or just use the limit for now
                            // Requirement says "unread notifications exist", usually means total unread.
                            // I'll add a separate unread count listener for the badge specifically to be accurate.
                            updateUnreadBadge();
                        }
                    });
        }
    }

    private void updateUnreadBadge() {
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
        // Requirement: Navigate to Dashboard (Already here) or specific page
    }

    private void forceLogToHistory() {
        Toast.makeText(this, "TRYING TO CREATE 'History' COLLECTION...", Toast.LENGTH_SHORT).show();
        
        // Use hardcoded values for a guaranteed write test
        com.google.firebase.Timestamp now = com.google.firebase.Timestamp.now();
        HistoryRecord testRecord = new HistoryRecord("waterlevel", 10.0, "%", "Test Zone", "Force Log", now);
        
        db.collection("History").add(testRecord)
            .addOnSuccessListener(documentReference -> {
                Toast.makeText(this, "SUCCESS! 'History' table created.", Toast.LENGTH_LONG).show();
                android.util.Log.d("AgriFlow", "Force Log Success: " + documentReference.getId());
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, "FAILED: " + e.getMessage(), Toast.LENGTH_LONG).show();
                android.util.Log.e("AgriFlow", "Force Log Failed", e);
            });
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
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
                Toast.makeText(this, "Coming Soon!", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });
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
                                tvUserName.setVisibility(android.view.View.VISIBLE);
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
        if ("admin".equalsIgnoreCase(role)) {
            bottomNavigationView.getMenu().clear();
            bottomNavigationView.inflateMenu(R.menu.admin_bottom_nav_menu);
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        }
    }

    private void initializeDatabaseStructure() {
        DocumentReference telemetryRef = db.collection("system_data").document("live_telemetry");
        
        telemetryRef.get().addOnSuccessListener(documentSnapshot -> {
            if (!documentSnapshot.exists()) {
                // Document doesn't exist, create it with default values
                java.util.Map<String, Object> initialData = new java.util.HashMap<>();
                initialData.put("temperature", 25.0);
                initialData.put("humidity", 60.0);
                initialData.put("soilMoisture", 45.0);
                initialData.put("waterTankLevel", 0.0);
                initialData.put("light_density", 0.0);
                initialData.put("waterPump", false);
                initialData.put("fertilizerPump", false);
                initialData.put("last_updated", com.google.firebase.Timestamp.now());

                telemetryRef.set(initialData)
                        .addOnSuccessListener(aVoid -> android.util.Log.d("AgriFlow", "Database structure initialized successfully!"))
                        .addOnFailureListener(e -> android.util.Log.e("AgriFlow", "Error initializing database structure", e));
            }
        });
    }

    private void updateTemperatureIndicator(double temp) {
        // Assuming range 0 to 50 Celsius
        float min = 0f;
        float max = 50f;
        float bias = (float) ((temp - min) / (max - min));
        
        // Clamp bias between 0 and 1
        bias = Math.max(0f, Math.min(1f, bias));

        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params = 
            (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) vTempIndicator.getLayoutParams();
        params.horizontalBias = bias;
        vTempIndicator.setLayoutParams(params);
    }

    private void updateHumidityStatus(double humidity) {
        if (humidity < SensorAlertConstants.HUMIDITY_WARNING_LOW) {
            tvHumidityStatus.setText(humidity < SensorAlertConstants.HUMIDITY_CRITICAL_LOW ? "Critical Low" : "Low");
            tvHumidityStatus.setTextColor(android.graphics.Color.parseColor(humidity < SensorAlertConstants.HUMIDITY_CRITICAL_LOW ? "#E53935" : "#FF8A65"));
        } else if (humidity <= SensorAlertConstants.HUMIDITY_WARNING_HIGH) {
            tvHumidityStatus.setText("Normal");
            tvHumidityStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.primary_dark_green));
        } else {
            tvHumidityStatus.setText(humidity > SensorAlertConstants.HUMIDITY_CRITICAL_HIGH ? "Critical High" : "High");
            tvHumidityStatus.setTextColor(android.graphics.Color.parseColor(humidity > SensorAlertConstants.HUMIDITY_CRITICAL_HIGH ? "#B71C1C" : "#448AFF"));
        }
    }

    private void updateSoilMoistureStatus(double moisture) {
        if (moisture < SensorAlertConstants.SOIL_CRITICAL_LOW) {
            tvSoilMoistureStatus.setText("Critical Dry");
            tvSoilMoistureStatus.setTextColor(android.graphics.Color.parseColor("#B71C1C"));
        } else if (moisture <= SensorAlertConstants.SOIL_WARNING_LOW) {
            tvSoilMoistureStatus.setText("Dry/Moderate");
            tvSoilMoistureStatus.setTextColor(android.graphics.Color.parseColor("#FB8C00"));
        } else {
            tvSoilMoistureStatus.setText("Normal");
            tvSoilMoistureStatus.setTextColor(android.graphics.Color.parseColor("#43A047"));
        }
    }

    private void updateTemperatureStatus(double temp) {
        if (temp < SensorAlertConstants.TEMP_WARNING_LOW) {
            tvTemperatureStatus.setText(temp < SensorAlertConstants.TEMP_CRITICAL_LOW ? "Critical Low" : "Low");
            tvTemperatureStatus.setTextColor(android.graphics.Color.parseColor(temp < SensorAlertConstants.TEMP_CRITICAL_LOW ? "#E53935" : "#64B5F6"));
        } else if (temp <= SensorAlertConstants.TEMP_WARNING_HIGH) {
            tvTemperatureStatus.setText("Normal");
            tvTemperatureStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.primary_dark_green));
        } else {
            tvTemperatureStatus.setText(temp > SensorAlertConstants.TEMP_CRITICAL_HIGH ? "Critical High" : "High");
            tvTemperatureStatus.setTextColor(android.graphics.Color.parseColor(temp > SensorAlertConstants.TEMP_CRITICAL_HIGH ? "#B71C1C" : "#FF8A65"));
        }
    }

    private void updateLightIntensityStatus(double light) {
        if (light > SensorAlertConstants.LIGHT_CRITICAL_HIGH) {
            tvLightIntensityStatus.setText("Extreme Light");
            tvLightIntensityStatus.setTextColor(android.graphics.Color.parseColor("#B71C1C"));
        } else if (light > SensorAlertConstants.LIGHT_WARNING_HIGH) {
            tvLightIntensityStatus.setText("Excessive Light");
            tvLightIntensityStatus.setTextColor(android.graphics.Color.parseColor("#E53935"));
        } else {
            tvLightIntensityStatus.setText("Normal");
            tvLightIntensityStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.primary_dark_green));
        }
    }

    private void updateLastUpdated() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault());
        String currentTime = sdf.format(new java.util.Date());
        tvLastUpdated.setText("Last updated: " + currentTime);
        tvLastUpdated.setVisibility(android.view.View.VISIBLE);
    }

    private void startTelemetryListener() {
        android.util.Log.d("AgriFlow_Debug", "Starting Telemetry Listener...");
        
        // Initial state
        tvWaterTank.setText("Loading...");

        telemetryListener = db.collection("system_data").document("live_telemetry")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        android.util.Log.e("AgriFlow_Debug", "Listen failed.", e);
                        tvWaterTank.setText("Error");
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        android.util.Log.d("AgriFlow_Debug", "New Data Received: " + snapshot.getData());
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
                            SensorAlertManager.getInstance(DashboardActivity.this).evaluateSoilMoisture(moisture, farmLocation);

                            // Log all sensors to history if interval passed
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
                            SensorAlertManager.getInstance(DashboardActivity.this).evaluateWaterLevel(tank, farmLocation);
                        } else {
                            tvWaterTank.setText("N/A");
                            tvWaterTankStatus.setText("Water level unavailable");
                            tvWaterTankStatus.setTextColor(android.graphics.Color.GRAY);
                        }

                        if (temp != null) {
                            tvTemperature.setText(String.format(java.util.Locale.getDefault(), "%.1f°C", temp));
                            updateTemperatureIndicator(temp);
                            updateTemperatureStatus(temp);

                            // Trigger Alert Check
                            SensorAlertManager.getInstance(DashboardActivity.this).evaluateTemperature(temp, farmLocation);
                        }
                        if (hum != null) {
                            tvHumidity.setText(String.format(java.util.Locale.getDefault(), "%.0f%%", hum));
                            pbHumidity.setProgress(hum.intValue());
                            updateHumidityStatus(hum);

                            // Trigger Alert Check
                            SensorAlertManager.getInstance(DashboardActivity.this).evaluateHumidity(hum, farmLocation);
                        }
                        if (light != null) {
                            tvLightIntensity.setText(String.format(java.util.Locale.getDefault(), "%.0f%%", light));
                            pbLightIntensity.setProgress(light.intValue());
                            
                            updateLightIntensityStatus(light);
                            
                            // Trigger Sunlight Notification Check
                            SensorAlertManager.getInstance(DashboardActivity.this).evaluateLightIntensity(light, farmLocation);
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
                        android.util.Log.w("AgriFlow_Debug", "Snapshot is null or doesn't exist");
                        tvWaterTank.setText("No Data");
                    }
                });

        setupPumpListeners();
    }

    private Double getSafeDouble(com.google.firebase.firestore.DocumentSnapshot snapshot, String field) {
        Object val = snapshot.get(field);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return null;
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
            db.collection("History").add(new HistoryRecord("moisture", moisture, "%", "Prototype Zone", "ESP32-S1", now));
        }
        if (temp != null) {
            db.collection("History").add(new HistoryRecord("temperature", temp, "°C", "Prototype Zone", "ESP32-S1", now));
        }
        if (hum != null) {
            db.collection("History").add(new HistoryRecord("humidity", hum, "%", "Prototype Zone", "ESP32-S1", now));
        }
        if (light != null) {
            db.collection("History").add(new HistoryRecord("light", light, "%", "Prototype Zone", "ESP32-S1", now));
        }
        if (tank != null) {
            db.collection("History").add(new HistoryRecord("waterlevel", tank, "%", "Prototype Zone", "ESP32-S1", now));
        }
    }

    private void logMoistureToHistory(double moisture) {
        HistoryRecord record = new HistoryRecord("moisture", moisture, "%", "Prototype Zone", "ESP32-S1", com.google.firebase.Timestamp.now());
        db.collection("History").add(record)
                .addOnFailureListener(e -> android.util.Log.e("AgriFlow", "Failed to log auto history", e));
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
