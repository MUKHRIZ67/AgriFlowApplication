package com.example.agriflowapplication;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText etFullName, etEmail, etPassword, etPhone, etFarmName, etFarmLocation;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseManager.getInstance().getAuth();
        db = FirebaseManager.getInstance().getDb();

        etFullName = findViewById(R.id.etFullName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etPhone = findViewById(R.id.etPhone);
        etFarmName = findViewById(R.id.etFarmName);
        etFarmLocation = findViewById(R.id.etFarmLocation);
        Button btnRegister = findViewById(R.id.btnRegister);
        TextView btnLoginNow = findViewById(R.id.btnLoginNow);

        btnRegister.setOnClickListener(v -> registerUser());

        btnLoginNow.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, MainActivity.class));
            finish();
        });
    }

    private void registerUser() {
        String name = etFullName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String farmName = etFarmName.getText().toString().trim();
        String farmLocation = etFarmLocation.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            etFullName.setError("Full name is required");
            return;
        }
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                        String userId = mAuth.getCurrentUser().getUid();
                        saveUserProfile(userId, name, email, phone, farmName, farmLocation);
                    } else {
                        String error = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        Toast.makeText(RegisterActivity.this, "Registration Failed: " + error, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveUserProfile(String userId, String name, String email, String phone, String farmName, String farmLocation) {
        Map<String, Object> user = new HashMap<>();
        user.put("fullName", name);
        user.put("email", email);
        user.put("phone", phone);
        user.put("farmName", farmName);
        user.put("farmLocation", farmLocation);

        db.collection("users").document(userId)
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(RegisterActivity.this, "User Registered Successfully!", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(RegisterActivity.this, DashboardActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(RegisterActivity.this, "Error saving profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
