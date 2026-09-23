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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);

        mAuth = FirebaseManager.getInstance().getAuth();
        db = FirebaseManager.getInstance().getDb();

        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
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

            String timeFormatted = String.format(Locale.getDefault(), "%02d:%02d %s", hour12, selectedMinute, amPm);

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

        // Save to system_data/schedules/water and schedules/water
        db.collection("schedules").document("water").set(waterSchedule, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    db.collection("system_data").document("schedules").collection("water").document("config")
                            .set(waterSchedule, SetOptions.merge());

                    if (enabled) {
                        db.collection("system_data").document("device_control").update("waterPumpState", "SCHEDULED");
                    }

                    Toast.makeText(this, "Watering schedule saved!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
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

                    if (enabled) {
                        db.collection("system_data").document("device_control").update("fertilizerPumpState", "SCHEDULED");
                    }

                    Toast.makeText(this, "Fertilizer schedule saved!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_schedule);

            bottomNavigationView.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_schedule) {
                    return true;
                } else if (itemId == R.id.nav_home) {
                    startActivity(new Intent(this, DashboardActivity.class));
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    finish();
                    return true;
                } else if (itemId == R.id.nav_history) {
                    startActivity(new Intent(this, MoistureHistoryActivity.class));
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    finish();
                    return true;
                } else if (itemId == R.id.nav_profile) {
                    startActivity(new Intent(this, ProfileActivity.class));
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    finish();
                    return true;
                }
                return false;
            });
        }
    }
}
