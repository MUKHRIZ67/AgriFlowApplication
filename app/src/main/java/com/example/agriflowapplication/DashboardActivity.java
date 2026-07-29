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
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class DashboardActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TextView tvUserName, tvLastUpdated;
    private ListenerRegistration telemetryListener;

    // Telemetry Views
    private TextView tvSoilMoisture, tvWaterTank, tvTemperature, tvHumidity, tvHumidityStatus;
    private android.widget.ProgressBar pbSoilMoisture, pbWaterTank, pbHumidity;
    private android.view.View vTempIndicator;
    private com.google.android.material.materialswitch.MaterialSwitch switchWaterPump, switchFertilizerPump;

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
        tvSoilMoisture = findViewById(R.id.tvSoilMoisture);
        tvWaterTank = findViewById(R.id.tvWaterTank);
        tvTemperature = findViewById(R.id.tvTemperature);
        tvHumidity = findViewById(R.id.tvHumidity);
        tvHumidityStatus = findViewById(R.id.tvHumidityStatus);

        pbSoilMoisture = findViewById(R.id.pbSoilMoisture);
        pbWaterTank = findViewById(R.id.pbWaterTank);
        pbHumidity = findViewById(R.id.pbHumidity);

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

        // Logout Button Listener
        findViewById(R.id.btnLogout).setOnClickListener(v -> showLogoutConfirmation());
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
                return true;
            } else if (itemId == R.id.nav_manage) {
                startActivity(new Intent(DashboardActivity.this, FarmerListActivity.class));
                return true;
            } else if (itemId == R.id.nav_history || itemId == R.id.nav_schedule) {
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
                initialData.put("waterTankLevel", 78.0);
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
        if (humidity < 40) {
            tvHumidityStatus.setText("Low");
            tvHumidityStatus.setTextColor(android.graphics.Color.parseColor("#FF8A65")); // Soft Orange
        } else if (humidity <= 70) {
            tvHumidityStatus.setText("Normal");
            tvHumidityStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.primary_dark_green));
        } else {
            tvHumidityStatus.setText("High");
            tvHumidityStatus.setTextColor(android.graphics.Color.parseColor("#448AFF")); // Soft Blue
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
        telemetryListener = db.collection("system_data").document("live_telemetry")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        android.util.Log.e("AgriFlow_Debug", "Listen failed.", e);
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        android.util.Log.d("AgriFlow_Debug", "New Data Received: " + snapshot.getData());
                        Double moisture = snapshot.getDouble("soilMoisture");
                        Double tank = snapshot.getDouble("waterTankLevel");
                        Double temp = snapshot.getDouble("temperature");
                        Double hum = snapshot.getDouble("humidity");
                        Boolean pump1 = snapshot.getBoolean("waterPump");
                        Boolean pump2 = snapshot.getBoolean("fertilizerPump");

                        if (moisture != null) {
                            tvSoilMoisture.setText(String.format(java.util.Locale.getDefault(), "%.0f%%", moisture));
                            pbSoilMoisture.setProgress(moisture.intValue());
                        }
                        if (tank != null) {
                            tvWaterTank.setText(String.format(java.util.Locale.getDefault(), "%.0f%%", tank));
                            pbWaterTank.setProgress(tank.intValue());
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
                    }
                });

        setupPumpListeners();
    }

    private void setupPumpListeners() {
        switchWaterPump.setOnCheckedChangeListener((buttonView, isChecked) -> 
            db.collection("system_data").document("live_telemetry").update("waterPump", isChecked)
        );

        switchFertilizerPump.setOnCheckedChangeListener((buttonView, isChecked) -> 
            db.collection("system_data").document("live_telemetry").update("fertilizerPump", isChecked)
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (telemetryListener != null) {
            telemetryListener.remove();
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
