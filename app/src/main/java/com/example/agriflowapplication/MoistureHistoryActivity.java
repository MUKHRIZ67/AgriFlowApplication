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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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
    private ChipGroup chipGroupSensorType, chipGroupSort;

    private Calendar startDate, endDate;
    private String selectedSensorType = "moisture";
    private boolean sortNewestFirst = true;
    private long referenceTimestamp = 0; // To fix float precision issues

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moisture_history);

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
        loadHistory();

        findViewById(R.id.btnRetry).setOnClickListener(v -> loadHistory());
    }

    private void setupFilters() {
        chipGroupSensorType.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipMoisture) selectedSensorType = "moisture";
            else if (id == R.id.chipTemperature) selectedSensorType = "temperature";
            else if (id == R.id.chipHumidity) selectedSensorType = "humidity";
            else if (id == R.id.chipLight) selectedSensorType = "light";
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
            btnStartDate.setText(R.string.filter_start_date);
            btnEndDate.setText(R.string.filter_end_date);
            loadHistory();
        });
    }

    private void loadHistory() {
        showState("loading");

        Query query = db.collection("moisture_history")
                .whereEqualTo("sensorType", selectedSensorType);

        if (startDate != null) {
            query = query.whereGreaterThanOrEqualTo("timestamp", new Timestamp(startDate.getTime()));
        }
        if (endDate != null) {
            query = query.whereLessThanOrEqualTo("timestamp", new Timestamp(endDate.getTime()));
        }

        query.get().addOnSuccessListener(queryDocumentSnapshots -> {
            records.clear();
            if (queryDocumentSnapshots.isEmpty()) {
                showState("empty");
                adapter.notifyDataSetChanged();
                return;
            }

            double sum = 0, min = Double.MAX_VALUE, max = Double.MIN_VALUE;
            String unit = getUnitForType(selectedSensorType);

            for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                HistoryRecord record = doc.toObject(HistoryRecord.class);
                // Backfill sensorType and unit if missing in DB for legacy records
                if (record.getSensorType() == null) record.setSensorType("moisture");
                if (record.getUnit() == null) record.setUnit(getUnitForType(record.getSensorType()));
                
                records.add(record);
                double val = record.getValue();
                sum += val;
                if (val < min) min = val;
                if (val > max) max = val;
            }

            applySortingAndNotify();
            showState("content");
            
            tvAvgMoisture.setText(String.format(Locale.getDefault(), "%.1f%s", sum / records.size(), unit));
            tvMinMoisture.setText(String.format(Locale.getDefault(), "%.1f%s", min, unit));
            tvMaxMoisture.setText(String.format(Locale.getDefault(), "%.1f%s", max, unit));
            updateChart();

        }).addOnFailureListener(e -> {
            Log.e(TAG, "Load failed", e);
            showState("error");
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
            default: resId = R.string.empty_moisture; break;
        }
        tvEmptyState.setText(resId);
    }

    private String getUnitForType(String type) {
        return "temperature".equalsIgnoreCase(type) ? "°C" : "%%";
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
        xAxis.setGranularity(1f); // One unit (ms/1000)
        xAxis.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant));
        xAxis.setValueFormatter(new ValueFormatter() {
            private final java.text.SimpleDateFormat mFormat = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
            @Override
            public String getFormattedValue(float value) {
                // Add back the reference timestamp and convert to date
                return mFormat.format(new java.util.Date((long) value + referenceTimestamp));
            }
        });

        YAxis leftAxis = moistureChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(ContextCompat.getColor(this, R.color.surface_container_high));
        leftAxis.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant));
        leftAxis.setAxisMinimum(0f);
        // Range is updated dynamically in loadHistory based on sensor type

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
        Collections.sort(sortedForChart, (a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));

        // Use the first record's timestamp as reference to avoid float precision loss
        referenceTimestamp = sortedForChart.get(0).getTimestamp().toDate().getTime();

        for (HistoryRecord record : sortedForChart) {
            if (record.getTimestamp() != null) {
                long currentTs = record.getTimestamp().toDate().getTime();
                // Store as offset from reference
                entries.add(new Entry((float) (currentTs - referenceTimestamp), (float) record.getValue()));
            }
        }

        LineDataSet dataSet = new LineDataSet(entries, selectedSensorType);
        dataSet.setColor(ContextCompat.getColor(this, R.color.primary_dark_green));
        dataSet.setLineWidth(2.5f);
        dataSet.setDrawCircles(true);
        dataSet.setCircleColor(ContextCompat.getColor(this, R.color.primary_dark_green));
        dataSet.setCircleRadius(4f);
        dataSet.setDrawCircleHole(true);
        dataSet.setCircleHoleColor(ContextCompat.getColor(this, R.color.bg_light_cream));
        
        // Horizontal Bezier is often "cleaner" and doesn't overshoot as much as Cubic
        dataSet.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);
        
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(ContextCompat.getColor(this, R.color.primary_dark_green));
        dataSet.setFillAlpha(35);
        dataSet.setDrawValues(false);

        LineData lineData = new LineData(dataSet);
        moistureChart.setData(lineData);
        
        // Adjust Y Axis based on sensor type
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
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId != null) {
            db.collection("users").document(userId).get().addOnSuccessListener(doc -> {
                if (doc.exists() && "admin".equalsIgnoreCase(doc.getString("role"))) applyAdminTheme();
            });
        }
    }

    private void applyAdminTheme() {
        findViewById(R.id.main_layout).setBackgroundColor(ContextCompat.getColor(this, R.color.iot_bg));
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.iot_bg));
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.iot_on_surface));
        
        BottomNavigationView bnv = findViewById(R.id.bottom_navigation);
        bnv.getMenu().clear();
        bnv.inflateMenu(R.menu.admin_bottom_nav_menu);
        bnv.setSelectedItemId(R.id.nav_history);
    }

    private void setupBottomNavigation() {
        BottomNavigationView bnv = findViewById(R.id.bottom_navigation);
        bnv.setSelectedItemId(R.id.nav_history);
        bnv.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_history) return true;
            if (id == R.id.nav_home) navigateHome();
            else if (id == R.id.nav_profile) navigateProfile();
            else if (id == R.id.nav_manage) startActivity(new Intent(this, FarmerListActivity.class));
            return true;
        });
    }

    private void navigateHome() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            Intent intent = new Intent(this, "admin".equalsIgnoreCase(doc.getString("role")) ? AdminDashboardActivity.class : DashboardActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void navigateProfile() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            Intent intent = new Intent(this, "admin".equalsIgnoreCase(doc.getString("role")) ? AdminProfileActivity.class : ProfileActivity.class);
            startActivity(intent);
            finish();
        });
    }
}
