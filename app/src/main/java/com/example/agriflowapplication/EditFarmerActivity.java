package com.example.agriflowapplication;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class EditFarmerActivity extends AppCompatActivity {

    private TextInputEditText etFullName, etPhone, etFarmName, etFarmLocation;
    private Button btnSave, btnDeleteFarmer;
    private ProgressBar progressBar;
    private FirebaseFirestore db;
    private DocumentReference farmerRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_farmer);

        db = FirebaseManager.getInstance().getDb();
        String farmerId = getIntent().getStringExtra("farmerId");

        if (farmerId == null) {
            Toast.makeText(this, "Error: No Farmer ID provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        farmerRef = db.collection("users").document(farmerId);

        // Initialize Views
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        etFullName = findViewById(R.id.etFullName);
        etPhone = findViewById(R.id.etPhone);
        etFarmName = findViewById(R.id.etFarmName);
        etFarmLocation = findViewById(R.id.etFarmLocation);
        btnSave = findViewById(R.id.btnSave);
        btnDeleteFarmer = findViewById(R.id.btnDeleteFarmer);
        progressBar = findViewById(R.id.progressBar);

        loadFarmerData();

        btnSave.setOnClickListener(v -> updateFarmerData());
        btnDeleteFarmer.setOnClickListener(v -> showDeleteConfirmation());
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_farmer)
                .setMessage(R.string.delete_farmer_confirmation)
                .setPositiveButton(R.string.delete, (dialog, which) -> deleteFarmer())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteFarmer() {
        showLoading(true);
        farmerRef.delete()
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Toast.makeText(this, R.string.farmer_deleted_success, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Error deleting farmer: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void loadFarmerData() {
        showLoading(true);
        farmerRef.get().addOnSuccessListener(documentSnapshot -> {
            showLoading(false);
            if (documentSnapshot.exists()) {
                etFullName.setText(documentSnapshot.getString("fullName"));
                etPhone.setText(documentSnapshot.getString("phone"));
                etFarmName.setText(documentSnapshot.getString("farmName"));
                etFarmLocation.setText(documentSnapshot.getString("farmLocation"));
            } else {
                Toast.makeText(this, "Farmer not found", Toast.LENGTH_SHORT).show();
                finish();
            }
        }).addOnFailureListener(e -> {
            showLoading(false);
            Toast.makeText(this, "Error loading data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void updateFarmerData() {
        if (etFullName.getText() == null || etPhone.getText() == null ||
            etFarmName.getText() == null || etFarmLocation.getText() == null) return;

        String name = etFullName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String farmName = etFarmName.getText().toString().trim();
        String farmLocation = etFarmLocation.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            etFullName.setError("Full name is required");
            return;
        }

        showLoading(true);

        Map<String, Object> updates = new HashMap<>();
        updates.put("fullName", name);
        updates.put("phone", phone);
        updates.put("farmName", farmName);
        updates.put("farmLocation", farmLocation);

        farmerRef.update(updates)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Toast.makeText(EditFarmerActivity.this, getString(R.string.farmer_updated_success), Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(EditFarmerActivity.this, "Error updating: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!isLoading);
        etFullName.setEnabled(!isLoading);
        etPhone.setEnabled(!isLoading);
        etFarmName.setEnabled(!isLoading);
        etFarmLocation.setEnabled(!isLoading);
    }
}
