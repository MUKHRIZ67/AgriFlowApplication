package com.example.agriflowapplication;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ScheduleActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private MaterialSwitch switchWaterScheduleEnable, switchFertilizerScheduleEnable;
    private MaterialButton btnWaterStartTime, btnFertilizerStartTime;
    private EditText etWaterDuration, etFertilizerDuration;
    private Spinner spinnerWaterRepeat, spinnerFertilizerRepeat;

    private String waterStartTime = "08:00 AM";
    private String fertilizerStartTime = "08:30 AM";
    private boolean isAdmin = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseManager.getInstance().getAuth();
        db = FirebaseManager.getInstance().getDb();

        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        isAdmin = getIntent().getBooleanExtra("is_admin", false);
        if (isAdmin) {
            setTheme(R.style.Theme_AgriFlow_Admin);
            setContentView(R.layout.activity_admin_schedule);
        } else {
            setContentView(R.layout.activity_schedule);
        }

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        switchWaterScheduleEnable = findViewById(R.id.switchWaterScheduleEnable);
        switchFertilizerScheduleEnable = findViewById(R.id.switchFertilizerScheduleEnable);

        btnWaterStartTime = findViewById(R.id.btnWaterStartTime);
        btnFertilizerStartTime = findViewById(R.id.btnFertilizerStartTime);

        etWaterDuration = findViewById(R.id.etWaterDuration);
        etFertilizerDuration = findViewById(R.id.etFertilizerDuration);

        spinnerWaterRepeat = findViewById(R.id.spinnerWaterRepeat);
        spinnerFertilizerRepeat = findViewById(R.id.spinnerFertilizerRepeat);

        setupRepeatSpinners();

        btnWaterStartTime.setOnClickListener(v -> showTimePicker(true));
        btnFertilizerStartTime.setOnClickListener(v -> showTimePicker(false));

        findViewById(R.id.btnSaveWaterSchedule).setOnClickListener(v -> saveWaterSchedule());
        findViewById(R.id.btnSaveFertilizerSchedule).setOnClickListener(v -> saveFertilizerSchedule());

        View btnTestWater = findViewById(R.id.btnTestWaterSchedule);
        if (btnTestWater != null) {
            btnTestWater.setOnClickListener(v -> testWaterSchedule());
        }

        View btnTestFertilizer = findViewById(R.id.btnTestFertilizerSchedule);
        if (btnTestFertilizer != null) {
            btnTestFertilizer.setOnClickListener(v -> testFertilizerSchedule());
        }

        setupBottomNavigation();
        loadExistingSchedules();
    }

    private void setupRepeatSpinners() {
        String[] waterFrequencies = {"Every Day", "Every 2 Days", "Every 3 Days", "Every 7 Days"};
        ArrayAdapter<String> waterAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, waterFrequencies);
        spinnerWaterRepeat.setAdapter(waterAdapter);

        String[] fertilizerFrequencies = {"Every Day", "Every 3 Days", "Every 7 Days", "Every 14 Days"};
        ArrayAdapter<String> fertilizerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, fertilizerFrequencies);
        spinnerFertilizerRepeat.setAdapter(fertilizerAdapter);
    }

    private void showTimePicker(boolean isWater) {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(this, (view, hourOfDay, selectedMinute) -> {
            String amPm = hourOfDay >= 12 ? "PM" : "AM";
            int hour12 = hourOfDay % 12;
            if (hour12 == 0) hour12 = 12;

            String timeFormatted = String.format(Locale.US, "%02d:%02d %s", hour12, selectedMinute, amPm);

            if (isWater) {
                waterStartTime = timeFormatted;
                btnWaterStartTime.setText(timeFormatted);
            } else {
                fertilizerStartTime = timeFormatted;
                btnFertilizerStartTime.setText(timeFormatted);
            }
        }, hour, minute, false);

        timePickerDialog.show();
    }

    private void loadExistingSchedules() {
        // Load Water Schedule
        db.collection("schedules").document("water").get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                Boolean enabled = documentSnapshot.getBoolean("enabled");
                String startTime = documentSnapshot.getString("startTime");
                Long duration = documentSnapshot.getLong("durationMinutes");
                String repeat = documentSnapshot.getString("repeatType");

                if (enabled != null) switchWaterScheduleEnable.setChecked(enabled);
                if (startTime != null) {
                    waterStartTime = startTime;
                    btnWaterStartTime.setText(startTime);
                }
                if (duration != null) etWaterDuration.setText(String.valueOf(duration));
                if (repeat != null) selectSpinnerItem(spinnerWaterRepeat, repeat);
            }
        });

        // Load Fertilizer Schedule
        db.collection("schedules").document("fertilizer").get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                Boolean enabled = documentSnapshot.getBoolean("enabled");
                String startTime = documentSnapshot.getString("startTime");
                Long duration = documentSnapshot.getLong("durationSeconds");
                String repeat = documentSnapshot.getString("repeatType");

                if (enabled != null) switchFertilizerScheduleEnable.setChecked(enabled);
                if (startTime != null) {
                    fertilizerStartTime = startTime;
                    btnFertilizerStartTime.setText(startTime);
                }
                if (duration != null) etFertilizerDuration.setText(String.valueOf(duration));
                if (repeat != null) selectSpinnerItem(spinnerFertilizerRepeat, repeat);
            }
        });
    }

    private void selectSpinnerItem(Spinner spinner, String value) {
        ArrayAdapter adapter = (ArrayAdapter) spinner.getAdapter();
        if (adapter != null) {
            int position = adapter.getPosition(value);
            if (position >= 0) spinner.setSelection(position);
        }
    }

    private void saveWaterSchedule() {
        boolean enabled = switchWaterScheduleEnable.isChecked();
        String durationStr = etWaterDuration.getText().toString().trim();
        int duration = durationStr.isEmpty() ? 5 : Integer.parseInt(durationStr);
        String repeatType = spinnerWaterRepeat.getSelectedItem() != null ? spinnerWaterRepeat.getSelectedItem().toString() : "Every Day";

        Map<String, Object> waterSchedule = new HashMap<>();
        waterSchedule.put("enabled", enabled);
        waterSchedule.put("startTime", waterStartTime);
        waterSchedule.put("durationMinutes", duration);
        waterSchedule.put("repeatType", repeatType);
        waterSchedule.put("lastUpdated", Timestamp.now());
        waterSchedule.put("updatedBy", mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "unknown");

        db.collection("schedules").document("water").set(waterSchedule, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    db.collection("system_data").document("schedules").collection("water").document("config")
                            .set(waterSchedule, SetOptions.merge());

                    Map<String, Object> stateUpdate = new HashMap<>();
                    stateUpdate.put("waterPumpState", enabled ? "SCHEDULED" : "OFF");
                    stateUpdate.put("waterPump", false);
                    stateUpdate.put("water_pump", false);
                    stateUpdate.put("lastUpdated", Timestamp.now());

                    db.collection("system_data").document("device_control").set(stateUpdate, SetOptions.merge());
                    db.collection("system_data").document("live_telemetry").set(stateUpdate, SetOptions.merge());

                    Toast.makeText(this, "Watering schedule saved! (" + (enabled ? "Enabled for " + waterStartTime : "Disabled") + ")", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error saving schedule: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void saveFertilizerSchedule() {
        boolean enabled = switchFertilizerScheduleEnable.isChecked();
        String durationStr = etFertilizerDuration.getText().toString().trim();
        int duration = durationStr.isEmpty() ? 30 : Integer.parseInt(durationStr);
        String repeatType = spinnerFertilizerRepeat.getSelectedItem() != null ? spinnerFertilizerRepeat.getSelectedItem().toString() : "Every 7 Days";

        Map<String, Object> fertilizerSchedule = new HashMap<>();
        fertilizerSchedule.put("enabled", enabled);
        fertilizerSchedule.put("startTime", fertilizerStartTime);
        fertilizerSchedule.put("durationSeconds", duration);
        fertilizerSchedule.put("repeatType", repeatType);
        fertilizerSchedule.put("lastUpdated", Timestamp.now());
        fertilizerSchedule.put("updatedBy", mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "unknown");

        db.collection("schedules").document("fertilizer").set(fertilizerSchedule, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    db.collection("system_data").document("schedules").collection("fertilizer").document("config")
                            .set(fertilizerSchedule, SetOptions.merge());

                    Map<String, Object> stateUpdate = new HashMap<>();
                    stateUpdate.put("fertilizerPumpState", enabled ? "SCHEDULED" : "OFF");
                    stateUpdate.put("fertilizerPump", false);
                    stateUpdate.put("fertilizer_pump", false);
                    stateUpdate.put("lastUpdated", Timestamp.now());

                    db.collection("system_data").document("device_control").set(stateUpdate, SetOptions.merge());
                    db.collection("system_data").document("live_telemetry").set(stateUpdate, SetOptions.merge());

                    Toast.makeText(this, "Fertilizer schedule saved! (" + (enabled ? "Enabled for " + fertilizerStartTime : "Disabled") + ")", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error saving schedule: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void testWaterSchedule() {
        Toast.makeText(this, "⚡ Testing Water Schedule...", Toast.LENGTH_SHORT).show();
        Map<String, Object> startUpdate = new HashMap<>();
        startUpdate.put("waterPumpState", "RUNNING");
        startUpdate.put("waterPump", true);
        startUpdate.put("water_pump", true);
        startUpdate.put("lastUpdated", Timestamp.now());

        db.collection("system_data").document("device_control").set(startUpdate, SetOptions.merge());
        db.collection("system_data").document("live_telemetry").set(startUpdate, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "✅ Water Pump RUNNING for 10s test...", Toast.LENGTH_SHORT).show();
                    logIrrigationEvent("Water", "RUNNING", "Schedule Test");
                    AppNotificationManager.getInstance(this).triggerAlertNotification(
                            "Watering Schedule Test Active",
                            "Water schedule test started.",
                            "water",
                            0, 0, "Normal"
                    );

                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        boolean enabled = switchWaterScheduleEnable.isChecked();
                        Map<String, Object> stopUpdate = new HashMap<>();
                        stopUpdate.put("waterPumpState", enabled ? "SCHEDULED" : "OFF");
                        stopUpdate.put("waterPump", false);
                        stopUpdate.put("water_pump", false);
                        stopUpdate.put("lastUpdated", Timestamp.now());

                        db.collection("system_data").document("device_control").set(stopUpdate, SetOptions.merge());
                        db.collection("system_data").document("live_telemetry").set(stopUpdate, SetOptions.merge());

                        logIrrigationEvent("Water", "COMPLETED", "Schedule Test");
                        AppNotificationManager.getInstance(ScheduleActivity.this).triggerAlertNotification(
                                "Watering Schedule Test Completed",
                                "Water schedule test completed.",
                                "water",
                                0, 0, "Normal"
                        );
                        Toast.makeText(ScheduleActivity.this, "Water schedule test finished.", Toast.LENGTH_SHORT).show();
                    }, 10000L);
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to test water pump: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void testFertilizerSchedule() {
        Toast.makeText(this, "⚡ Testing Fertilizer Schedule...", Toast.LENGTH_SHORT).show();
        Map<String, Object> startUpdate = new HashMap<>();
        startUpdate.put("fertilizerPumpState", "RUNNING");
        startUpdate.put("fertilizerPump", true);
        startUpdate.put("fertilizer_pump", true);
        startUpdate.put("lastUpdated", Timestamp.now());

        db.collection("system_data").document("device_control").set(startUpdate, SetOptions.merge());
        db.collection("system_data").document("live_telemetry").set(startUpdate, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "✅ Fertilizer Pump RUNNING for 10s test...", Toast.LENGTH_SHORT).show();
                    logIrrigationEvent("Fertilizer", "RUNNING", "Schedule Test");
                    AppNotificationManager.getInstance(this).triggerAlertNotification(
                            "Fertilizer Schedule Test Active",
                            "Fertilizer schedule test started.",
                            "sunlight",
                            0, 0, "Normal"
                    );

                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        boolean enabled = switchFertilizerScheduleEnable.isChecked();
                        Map<String, Object> stopUpdate = new HashMap<>();
                        stopUpdate.put("fertilizerPumpState", enabled ? "SCHEDULED" : "OFF");
                        stopUpdate.put("fertilizerPump", false);
                        stopUpdate.put("fertilizer_pump", false);
                        stopUpdate.put("lastUpdated", Timestamp.now());

                        db.collection("system_data").document("device_control").set(stopUpdate, SetOptions.merge());
                        db.collection("system_data").document("live_telemetry").set(stopUpdate, SetOptions.merge());

                        logIrrigationEvent("Fertilizer", "COMPLETED", "Schedule Test");
                        AppNotificationManager.getInstance(ScheduleActivity.this).triggerAlertNotification(
                                "Fertilizer Schedule Test Completed",
                                "Fertilizer schedule test completed.",
                                "sunlight",
                                0, 0, "Normal"
                        );
                        Toast.makeText(ScheduleActivity.this, "Fertilizer schedule test finished.", Toast.LENGTH_SHORT).show();
                    }, 10000L);
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to test fertilizer pump: " + e.getMessage(), Toast.LENGTH_SHORT).show());
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

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        if (bottomNavigationView != null) {
            if (isAdmin) {
                bottomNavigationView.getMenu().clear();
                bottomNavigationView.inflateMenu(R.menu.admin_bottom_nav_menu);
            }
            bottomNavigationView.setSelectedItemId(R.id.nav_schedule);

            bottomNavigationView.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_schedule) {
                    return true;
                } else if (itemId == R.id.nav_home) {
                    Intent intent = new Intent(this, isAdmin ? AdminDashboardActivity.class : DashboardActivity.class);
                    startActivity(intent);
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    finish();
                    return true;
                } else if (itemId == R.id.nav_history) {
                    Intent intent = new Intent(this, MoistureHistoryActivity.class);
                    if (isAdmin) intent.putExtra("is_admin", true);
                    startActivity(intent);
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    finish();
                    return true;
                } else if (itemId == R.id.nav_manage) {
                    Intent intent = new Intent(this, FarmerListActivity.class);
                    startActivity(intent);
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    finish();
                    return true;
                } else if (itemId == R.id.nav_profile) {
                    Intent intent = new Intent(this, isAdmin ? AdminProfileActivity.class : ProfileActivity.class);
                    startActivity(intent);
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    finish();
                    return true;
                }
                return false;
            });
        }
    }
}
