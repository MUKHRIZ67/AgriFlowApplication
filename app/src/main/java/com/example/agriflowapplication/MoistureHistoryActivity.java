package com.example.agriflowapplication;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import android.graphics.Color;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

public class MoistureHistoryActivity extends AppCompatActivity {

    private static final String TAG = "AgriFlow_History";
    private HistoryAdapter adapter;
    private List<HistoryRecord> records;
    private View loadingState, emptyState, errorState, summaryLayout;
    private FirebaseFirestore db;
    private MaterialButton btnStartDate, btnEndDate;
    private TextView tvAvgMoisture, tvMinMoisture, tvMaxMoisture, tvEmptyState;
    private LineChart moistureChart;
    private ChipGroup chipGroupSensorType, chipGroupSort, chipGroupTimeRange;

    private Calendar startDate, endDate;
    private String selectedSensorType = "moisture";
    private String selectedTimeRange = "today";
    private boolean sortNewestFirst = true;
    private long referenceTimestamp = 0; // To fix float precision issues

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean isAdmin = getIntent().getBooleanExtra("is_admin", false);
        if (isAdmin) {
            setTheme(R.style.Theme_AgriFlow_Admin);
            setContentView(R.layout.activity_admin_history);
        } else {
            setContentView(R.layout.activity_moisture_history);
        }

        db = FirebaseManager.getInstance().getDb();
        
        loadingState = findViewById(R.id.loadingState);
        emptyState = findViewById(R.id.emptyState);
        errorState = findViewById(R.id.errorState);
        summaryLayout = findViewById(R.id.summaryLayout);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        
        btnStartDate = findViewById(R.id.btnStartDate);
        btnEndDate = findViewById(R.id.btnEndDate);
        
        tvAvgMoisture = findViewById(R.id.tvAvgMoisture);
        tvMinMoisture = findViewById(R.id.tvMinMoisture);
        tvMaxMoisture = findViewById(R.id.tvMaxMoisture);
        moistureChart = findViewById(R.id.moistureChart);

        chipGroupSensorType = findViewById(R.id.chipGroupSensorType);
        chipGroupSort = findViewById(R.id.chipGroupSort);
        chipGroupTimeRange = findViewById(R.id.chipGroupTimeRange);

        RecyclerView rvHistory = findViewById(R.id.rvHistory);
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        records = new ArrayList<>();
        adapter = new HistoryAdapter(records);
        rvHistory.setAdapter(adapter);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        // Replace Back Button with Refresh Button
        toolbar.setNavigationIcon(android.R.drawable.stat_notify_sync);
        toolbar.setNavigationIconTint(ContextCompat.getColor(this, R.color.primary_dark_green));
        toolbar.setNavigationContentDescription("Refresh history");
        toolbar.setNavigationOnClickListener(v -> {
            Toast.makeText(this, "Refreshing data...", Toast.LENGTH_SHORT).show();
            loadHistory();
        });

        setupFilters();
        setupBottomNavigation();
        setupChart();
        checkUserRoleAndApplyTheme();
        
        // Initialize with Today range
        setTodayRange();
        loadHistory();

        findViewById(R.id.btnRetry).setOnClickListener(v -> loadHistory());
        
        // One-time Migration Helper (hidden long press on refresh icon)
        toolbar.setOnLongClickListener(v -> {
            showMigrationDialog();
            return true;
        });
    }

    private void showMigrationDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Data Migration")
                .setMessage("Do you want to migrate data from 'moisture_history' to 'History'?")
                .setPositiveButton("Migrate", (dialog, which) -> startMigration())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startMigration() {
        Toast.makeText(this, "Migration started...", Toast.LENGTH_SHORT).show();
        db.collection("moisture_history").get().addOnSuccessListener(queryDocumentSnapshots -> {
            if (queryDocumentSnapshots.isEmpty()) {
                Toast.makeText(this, "No old data found", Toast.LENGTH_SHORT).show();
                return;
            }
            
            com.google.firebase.firestore.WriteBatch batch = db.batch();
            for (com.google.firebase.firestore.QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                batch.set(db.collection("History").document(doc.getId()), doc.getData());
            }
            
            batch.commit().addOnSuccessListener(aVoid -> {
                Toast.makeText(this, "Migration successful! Restarting...", Toast.LENGTH_LONG).show();
                loadHistory();
            }).addOnFailureListener(e -> {
                Toast.makeText(this, "Migration failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
        });
    }

    private void setupFilters() {
        chipGroupSensorType.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipMoisture) selectedSensorType = "moisture";
            else if (id == R.id.chipTemperature) selectedSensorType = "temperature";
            else if (id == R.id.chipHumidity) selectedSensorType = "humidity";
            else if (id == R.id.chipLight) selectedSensorType = "light";
            else if (id == R.id.chipWaterLevel) selectedSensorType = "waterlevel";
            loadHistory();
        });

        chipGroupTimeRange.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipToday) {
                selectedTimeRange = "today";
                setTodayRange();
            } else if (id == R.id.chipWeek) {
                selectedTimeRange = "week";
                setWeekRange();
            } else if (id == R.id.chipMonth) {
                selectedTimeRange = "month";
                setMonthRange();
            }
            loadHistory();
        });

        chipGroupSort.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            sortNewestFirst = (id == R.id.chipNewest);
            applySortingAndNotify();
        });

        btnStartDate.setOnClickListener(v -> {
            Calendar c = startDate != null ? startDate : Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                startDate = Calendar.getInstance();
                startDate.set(year, month, dayOfMonth, 0, 0, 0);
                btnStartDate.setText(String.format(Locale.getDefault(), "%d-%d-%d", year, month + 1, dayOfMonth));
                loadHistory();
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnEndDate.setOnClickListener(v -> {
            Calendar c = endDate != null ? endDate : Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                endDate = Calendar.getInstance();
                endDate.set(year, month, dayOfMonth, 23, 59, 59);
                btnEndDate.setText(String.format(Locale.getDefault(), "%d-%d-%d", year, month + 1, dayOfMonth));
                loadHistory();
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        findViewById(R.id.btnClearFilters).setOnClickListener(v -> {
            startDate = null;
            endDate = null;
            selectedTimeRange = "custom";
            chipGroupTimeRange.clearCheck();
            btnStartDate.setText(R.string.filter_start_date);
            btnEndDate.setText(R.string.filter_end_date);
            loadHistory();
        });
    }

    private void setTodayRange() {
        startDate = Calendar.getInstance();
        startDate.set(Calendar.HOUR_OF_DAY, 0);
        startDate.set(Calendar.MINUTE, 0);
        startDate.set(Calendar.SECOND, 0);
        startDate.set(Calendar.MILLISECOND, 0);
        
        endDate = Calendar.getInstance();
        endDate.set(Calendar.HOUR_OF_DAY, 23);
        endDate.set(Calendar.MINUTE, 59);
        endDate.set(Calendar.SECOND, 59);
        endDate.set(Calendar.MILLISECOND, 999);
    }

    private void setWeekRange() {
        startDate = Calendar.getInstance();
        startDate.add(Calendar.DAY_OF_YEAR, -7);
        startDate.set(Calendar.HOUR_OF_DAY, 0);
        startDate.set(Calendar.MINUTE, 0);
        startDate.set(Calendar.SECOND, 0);
        startDate.set(Calendar.MILLISECOND, 0);
        
        endDate = Calendar.getInstance();
        endDate.set(Calendar.HOUR_OF_DAY, 23);
        endDate.set(Calendar.MINUTE, 59);
        endDate.set(Calendar.SECOND, 59);
        endDate.set(Calendar.MILLISECOND, 999);
    }

    private void setMonthRange() {
        startDate = Calendar.getInstance();
        startDate.add(Calendar.MONTH, -1);
        startDate.set(Calendar.HOUR_OF_DAY, 0);
        startDate.set(Calendar.MINUTE, 0);
        startDate.set(Calendar.SECOND, 0);
        startDate.set(Calendar.MILLISECOND, 0);
        
        endDate = Calendar.getInstance();
        endDate.set(Calendar.HOUR_OF_DAY, 23);
        endDate.set(Calendar.MINUTE, 59);
        endDate.set(Calendar.SECOND, 59);
        endDate.set(Calendar.MILLISECOND, 999);
    }

    private void loadHistory() {
        showState("loading");

        // Use a simple query that doesn't require composite indexes.
        // We filter the specific date range in memory for reliability.
        db.collection("History")
                .whereEqualTo("sensorType", selectedSensorType)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(500) // Fetch recent records to avoid performance issues
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    records.clear();
                    if (queryDocumentSnapshots.isEmpty()) {
                        showState("empty");
                        adapter.notifyDataSetChanged();
                        return;
                    }

                    double sum = 0, min = Double.MAX_VALUE, max = Double.MIN_VALUE;
                    String unit = getUnitForType(selectedSensorType);
                    
                    long startMillis = startDate != null ? startDate.getTimeInMillis() : 0;
                    long endMillis = endDate != null ? endDate.getTimeInMillis() : Long.MAX_VALUE;

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        HistoryRecord record = doc.toObject(HistoryRecord.class);
                        
                        // Data Validation & Range Filtering
                        if (record.getTimestamp() == null) continue;
                        
                        long ts = record.getTimestamp().toDate().getTime();
                        if (ts < startMillis || ts > endMillis) continue;
                        
                        double val = record.getValue();
                        if ("waterlevel".equalsIgnoreCase(selectedSensorType)) {
                            if (val < 0 || val > 100) continue;
                        }
                        
                        // Backfill legacy fields
                        if (record.getSensorType() == null) record.setSensorType(selectedSensorType);
                        if (record.getUnit() == null) record.setUnit(getUnitForType(record.getSensorType()));
                        
                        records.add(record);
                        sum += val;
                        if (val < min) min = val;
                        if (val > max) max = val;
                    }

                    if (records.isEmpty()) {
                        showState("empty");
                    } else {
                        applySortingAndNotify();
                        showState("content");
                        
                        tvAvgMoisture.setText(String.format(Locale.getDefault(), "%.1f%s", sum / records.size(), unit));
                        tvMinMoisture.setText(String.format(Locale.getDefault(), "%.1f%s", min, unit));
                        tvMaxMoisture.setText(String.format(Locale.getDefault(), "%.1f%s", max, unit));
                        updateChart();
                    }

                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Load failed", e);
                    // Fallback: If ordering failed (might still require index), try without ordering
                    db.collection("History")
                            .whereEqualTo("sensorType", selectedSensorType)
                            .get()
                            .addOnSuccessListener(snapshots -> {
                                // Re-run logic without the initial order
                                records.clear();
                                double _sum = 0, _min = Double.MAX_VALUE, _max = Double.MIN_VALUE;
                                long _start = startDate != null ? startDate.getTimeInMillis() : 0;
                                long _end = endDate != null ? endDate.getTimeInMillis() : Long.MAX_VALUE;

                                for (QueryDocumentSnapshot doc : snapshots) {
                                    HistoryRecord record = doc.toObject(HistoryRecord.class);
                                    if (record.getTimestamp() == null) continue;
                                    long ts = record.getTimestamp().toDate().getTime();
                                    if (ts < _start || ts > _end) continue;
                                    
                                    records.add(record);
                                    double val = record.getValue();
                                    _sum += val;
                                    if (val < _min) _min = val;
                                    if (val > _max) _max = val;
                                }

                                if (records.isEmpty()) {
                                    showState("empty");
                                } else {
                                    applySortingAndNotify();
                                    showState("content");
                                    tvAvgMoisture.setText(String.format(Locale.getDefault(), "%.1f%s", _sum / records.size(), getUnitForType(selectedSensorType)));
                                    tvMinMoisture.setText(String.format(Locale.getDefault(), "%.1f%s", _min, getUnitForType(selectedSensorType)));
                                    tvMaxMoisture.setText(String.format(Locale.getDefault(), "%.1f%s", _max, getUnitForType(selectedSensorType)));
                                    updateChart();
                                }
                            })
                            .addOnFailureListener(e2 -> showState("error"));
                });
    }

    private void applySortingAndNotify() {
        Collections.sort(records, (a, b) -> {
            if (a.getTimestamp() == null || b.getTimestamp() == null) return 0;
            return sortNewestFirst ? b.getTimestamp().compareTo(a.getTimestamp()) 
                                 : a.getTimestamp().compareTo(b.getTimestamp());
        });
        adapter.notifyDataSetChanged();
    }

    private void showState(String state) {
        loadingState.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);
        summaryLayout.setVisibility(View.GONE);

        switch (state) {
            case "loading":
                loadingState.setVisibility(View.VISIBLE);
                break;
            case "empty":
                emptyState.setVisibility(View.VISIBLE);
                updateEmptyStateText();
                break;
            case "error":
                errorState.setVisibility(View.VISIBLE);
                break;
            case "content":
                summaryLayout.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void updateEmptyStateText() {
        int resId;
        switch (selectedSensorType) {
            case "temperature": resId = R.string.empty_temperature; break;
            case "humidity": resId = R.string.empty_humidity; break;
            case "light": resId = R.string.empty_light; break;
            case "waterlevel": resId = R.string.empty_water_level; break;
            default: resId = R.string.empty_moisture; break;
        }
        tvEmptyState.setText(resId);
    }

    private String getUnitForType(String type) {
        return "temperature".equalsIgnoreCase(type) ? "°C" : "%";
    }

    private void setupChart() {
        moistureChart.getDescription().setEnabled(false);
        moistureChart.setNoDataText("No history data available");
        moistureChart.setDrawGridBackground(false);
        moistureChart.setTouchEnabled(true);
        moistureChart.setDragEnabled(true);
        moistureChart.setScaleEnabled(true);
        moistureChart.setPinchZoom(true);

        XAxis xAxis = moistureChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(6, false);
        xAxis.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant));
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                long millis = (long) value + referenceTimestamp;
                java.util.Date date = new java.util.Date(millis);
                
                SimpleDateFormat sdf;
                if ("today".equals(selectedTimeRange)) {
                    sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                } else if ("week".equals(selectedTimeRange)) {
                    sdf = new SimpleDateFormat("EEE dd", Locale.getDefault());
                } else if ("month".equals(selectedTimeRange)) {
                    sdf = new SimpleDateFormat("dd MMM", Locale.getDefault());
                } else {
                    sdf = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
                }
                return sdf.format(date);
            }
        });

        YAxis leftAxis = moistureChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(ContextCompat.getColor(this, R.color.surface_container_high));
        leftAxis.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant));
        leftAxis.setAxisMinimum(0f);

        moistureChart.getAxisRight().setEnabled(false);
        moistureChart.getLegend().setEnabled(false);
    }

    private void updateChart() {
        if (records.isEmpty()) {
            moistureChart.clear();
            return;
        }

        List<Entry> entries = new ArrayList<>();
        List<HistoryRecord> sortedForChart = new ArrayList<>(records);
        
        // Chronological Sorting: Oldest to Newest
        Collections.sort(sortedForChart, (a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));

        // Use the first record's timestamp as reference to avoid float precision loss (Reverted)
        referenceTimestamp = sortedForChart.get(0).getTimestamp().toDate().getTime();

        for (HistoryRecord record : sortedForChart) {
            long currentTs = record.getTimestamp().toDate().getTime();
            entries.add(new Entry((float) (currentTs - referenceTimestamp), (float) record.getValue()));
        }

        LineDataSet dataSet = new LineDataSet(entries, selectedSensorType);
        
        // Revert back to original green color for all sensors
        int chartColor = ContextCompat.getColor(this, R.color.primary_dark_green);
        
        dataSet.setColor(chartColor);
        dataSet.setLineWidth(2.5f);
        dataSet.setDrawCircles(true);
        dataSet.setCircleColor(chartColor);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawCircleHole(true);
        dataSet.setCircleHoleColor(ContextCompat.getColor(this, R.color.bg_light_cream));
        
        // Water Level specific: Stepped line
        if ("waterlevel".equalsIgnoreCase(selectedSensorType)) {
            dataSet.setMode(LineDataSet.Mode.STEPPED);
            dataSet.setDrawCircles(false);
        } else {
            // Revert back to original behavior for other sensors
            dataSet.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);
        }
        
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(chartColor);
        dataSet.setFillAlpha(35);
        dataSet.setDrawValues(false);

        LineData lineData = new LineData(dataSet);
        moistureChart.setData(lineData);
        
        // Revert back to automatic X-axis scaling based on data
        XAxis xAxis = moistureChart.getXAxis();
        xAxis.resetAxisMinimum();
        xAxis.resetAxisMaximum();
        
        YAxis leftAxis = moistureChart.getAxisLeft();
        if ("temperature".equalsIgnoreCase(selectedSensorType)) {
            leftAxis.setAxisMaximum(55f);
            leftAxis.setAxisMinimum(0f);
        } else {
            leftAxis.setAxisMaximum(100f);
            leftAxis.setAxisMinimum(0f);
        }

        moistureChart.invalidate();
        moistureChart.animateX(600);
    }

    private void checkUserRoleAndApplyTheme() {
        // Theme is now handled immediately in onCreate via Intent extra to avoid flicker
    }

    private void applyAdminTheme() {
        // Theme is now handled immediately in onCreate via Intent extra to avoid flicker
    }

    private void setupBottomNavigation() {
        BottomNavigationView bnv = findViewById(R.id.bottom_navigation);
        boolean isAdmin = getIntent().getBooleanExtra("is_admin", false);
        if (isAdmin) {
            bnv.getMenu().clear();
            bnv.inflateMenu(R.menu.admin_bottom_nav_menu);
        }
        bnv.setSelectedItemId(R.id.nav_history);
        bnv.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_history) return true;
            if (id == R.id.nav_home) {
                navigateHome();
                return true;
            } else if (id == R.id.nav_schedule) {
                Intent intent = new Intent(this, ScheduleActivity.class);
                intent.putExtra("is_admin", isAdmin);
                startActivity(intent);
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                finish();
                return true;
            } else if (id == R.id.nav_profile) {
                navigateProfile();
                return true;
            } else if (id == R.id.nav_manage) {
                startActivity(new Intent(this, FarmerListActivity.class));
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                finish();
                return true;
            }
            return false;
        });
    }

    private void navigateHome() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        boolean isAdmin = getIntent().getBooleanExtra("is_admin", false);
        Intent intent = new Intent(this, isAdmin ? AdminDashboardActivity.class : DashboardActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }

    private void navigateProfile() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        boolean isAdmin = getIntent().getBooleanExtra("is_admin", false);
        Intent intent = new Intent(this, isAdmin ? AdminProfileActivity.class : ProfileActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }
}
