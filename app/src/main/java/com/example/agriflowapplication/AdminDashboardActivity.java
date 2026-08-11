package com.example.agriflowapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class AdminDashboardActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TextView tvUserName, tvLastUpdated;
    private ListenerRegistration telemetryListener;

    // Telemetry Views
    private TextView tvSoilMoisture, tvSoilMoistureStatus, tvWaterTank, tvTemperature, tvHumidity, tvHumidityStatus, tvLightIntensity;
    private android.widget.ProgressBar pbSoilMoisture, pbWaterTank, pbHumidity, pbLightIntensity;
    private android.view.View vTempIndicator;
    private com.google.android.material.materialswitch.MaterialSwitch switchWaterPump, switchFertilizerPump;

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
        tvSoilMoisture = findViewById(R.id.tvSoilMoisture);
        tvSoilMoistureStatus = findViewById(R.id.tvSoilMoistureStatus);
        tvWaterTank = findViewById(R.id.tvWaterTank);
        tvWaterTank.setText("Loading...");
        tvTemperature = findViewById(R.id.tvTemperature);
        tvHumidity = findViewById(R.id.tvHumidity);
        tvHumidityStatus = findViewById(R.id.tvHumidityStatus);
        tvLightIntensity = findViewById(R.id.tvLightIntensity);

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

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                return true;
            } else if (itemId == R.id.nav_history) {
                startActivity(new Intent(AdminDashboardActivity.this, MoistureHistoryActivity.class));
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                finish();
                return true;
            } else if (itemId == R.id.nav_schedule) {
                Toast.makeText(this, "Coming Soon!", Toast.LENGTH_SHORT).show();
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

                            // Log all sensors to history if interval passed
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastHistoryLogTime >= LOG_INTERVAL_MS) {
                                logAllSensorsToHistory(moisture, temp, hum, light);
                                lastHistoryLogTime = currentTime;
                            }
                        }
                        
                        if (tank != null) {
                            tvWaterTank.setText(String.format(java.util.Locale.getDefault(), "%.0f%%", tank));
                            pbWaterTank.setProgress(tank.intValue());
                        } else {
                            tvWaterTank.setText("N/A");
                        }

                        if (temp != null) {
                            tvTemperature.setText(String.format(java.util.Locale.getDefault(), "%.1f°C", temp));
                            updateTemperatureIndicator(temp);
                        }
                        if (hum != null) {
                            tvHumidity.setText(String.format(java.util.Locale.getDefault(), "%.0f%%", hum));
                            pbHumidity.setProgress(hum.intValue());
                            updateHumidityStatus(hum);
                        }
                        if (light != null) {
                            tvLightIntensity.setText(String.format(java.util.Locale.getDefault(), "%.0f%%", light));
                            pbLightIntensity.setProgress(light.intValue());
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

    private Double getSafeDouble(com.google.firebase.firestore.DocumentSnapshot snapshot, String field) {
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
        if (humidity < 40) {
            tvHumidityStatus.setText("Low");
            tvHumidityStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.iot_warning));
        } else if (humidity <= 70) {
            tvHumidityStatus.setText("Normal");
            tvHumidityStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.iot_success));
        } else {
            tvHumidityStatus.setText("High");
            tvHumidityStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.iot_secondary));
        }
    }

    private void updateSoilMoistureStatus(double moisture) {
        if (moisture <= 30) {
            tvSoilMoistureStatus.setText("Dry");
            tvSoilMoistureStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.iot_error));
        } else if (moisture <= 70) {
            tvSoilMoistureStatus.setText("Moderate");
            tvSoilMoistureStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.iot_warning));
        } else {
            tvSoilMoistureStatus.setText("Wet");
            tvSoilMoistureStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.iot_success));
        }
    }

    private void updateLastUpdated() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault());
        String currentTime = sdf.format(new java.util.Date());
        tvLastUpdated.setText("Last updated: " + currentTime);
        tvLastUpdated.setVisibility(android.view.View.VISIBLE);
    }

    private void setupPumpListeners() {
        switchWaterPump.setOnCheckedChangeListener((buttonView, isChecked) -> 
            db.collection("system_data").document("live_telemetry").update("waterPump", isChecked)
        );

        switchFertilizerPump.setOnCheckedChangeListener((buttonView, isChecked) -> 
            db.collection("system_data").document("live_telemetry").update("fertilizerPump", isChecked)
        );
    }

    private void logAllSensorsToHistory(Double moisture, Double temp, Double hum, Double light) {
        com.google.firebase.Timestamp now = com.google.firebase.Timestamp.now();
        if (moisture != null) {
            db.collection("moisture_history").add(new HistoryRecord("moisture", moisture, "%%", "Prototype Zone", "Admin Terminal", now));
        }
        if (temp != null) {
            db.collection("moisture_history").add(new HistoryRecord("temperature", temp, "°C", "Prototype Zone", "Admin Terminal", now));
        }
        if (hum != null) {
            db.collection("moisture_history").add(new HistoryRecord("humidity", hum, "%%", "Prototype Zone", "Admin Terminal", now));
        }
        if (light != null) {
            db.collection("moisture_history").add(new HistoryRecord("light", light, "%%", "Prototype Zone", "Admin Terminal", now));
        }
    }

    private void logMoistureToHistory(double moisture) {
        HistoryRecord record = new HistoryRecord("moisture", moisture, "%%", "Prototype Zone", "Admin Terminal", com.google.firebase.Timestamp.now());
        db.collection("moisture_history").add(record)
                .addOnFailureListener(e -> android.util.Log.e("AgriFlow", "Failed to log admin auto history", e));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (telemetryListener != null) {
            telemetryListener.remove();
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

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.nav_home);
    }
}
