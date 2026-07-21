package com.example.agriflowapplication;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AddFarmerActivity extends AppCompatActivity {

    private TextInputEditText etFullName, etEmail, etPhone, etFarmName, etFarmLocation, etPassword;
    private Button btnAddFarmer;
    private ProgressBar progressBar;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_farmer);

        db = FirebaseManager.getInstance().getDb();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        etFullName = findViewById(R.id.etFullName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etFarmName = findViewById(R.id.etFarmName);
        etFarmLocation = findViewById(R.id.etFarmLocation);
        etPassword = findViewById(R.id.etPassword);
        btnAddFarmer = findViewById(R.id.btnAddFarmer);
        progressBar = findViewById(R.id.progressBar);

        btnAddFarmer.setOnClickListener(v -> registerFarmer());
    }

    private void registerFarmer() {
        if (etFullName.getText() == null || etEmail.getText() == null || etPassword.getText() == null ||
            etPhone.getText() == null || etFarmName.getText() == null || etFarmLocation.getText() == null) return;

        String name = etFullName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String farmName = etFarmName.getText().toString().trim();
        String farmLocation = etFarmLocation.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            etFullName.setError("Required");
            return;
        }
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Required");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Required");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnAddFarmer.setEnabled(false);

        // To create a user without signing out the current Admin, we use a secondary Firebase app instance
        FirebaseApp secondaryApp;
        try {
            secondaryApp = FirebaseApp.getInstance("SecondaryApp");
        } catch (IllegalStateException e) {
            FirebaseOptions options = FirebaseApp.getInstance().getOptions();
            secondaryApp = FirebaseApp.initializeApp(this, options, "SecondaryApp");
        }

        FirebaseAuth secondaryAuth = FirebaseAuth.getInstance(secondaryApp);

        secondaryAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().getUser() != null) {
                        String uid = task.getResult().getUser().getUid();
                        saveFarmerToFirestore(uid, name, email, phone, farmName, farmLocation);
                        secondaryAuth.signOut(); // Sign out from secondary instance
                    } else {
                        progressBar.setVisibility(View.GONE);
                        btnAddFarmer.setEnabled(true);
                        String error = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        Toast.makeText(this, "Failed to create account: " + error, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveFarmerToFirestore(String uid, String name, String email, String phone, String farmName, String farmLocation) {
        Map<String, Object> farmer = new HashMap<>();
        farmer.put("fullName", name);
        farmer.put("email", email);
        farmer.put("phone", phone);
        farmer.put("farmName", farmName);
        farmer.put("farmLocation", farmLocation);
        farmer.put("role", "farmer"); // Explicitly set role

        db.collection("users").document(uid)
                .set(farmer)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Farmer Account Created Successfully!", Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnAddFarmer.setEnabled(true);
                    Toast.makeText(this, "Error saving to database: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
