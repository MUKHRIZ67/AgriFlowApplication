package com.example.agriflowapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
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

import java.util.HashMap;
import java.util.Map;

public class AdminProfileActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private DocumentReference userRef;
    
    private TextView tvHeaderName, tvFullName, tvEmail, tvPhone;
    private android.widget.ProgressBar progressBar;

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

        userRef = db.collection("users").document(mAuth.getCurrentUser().getUid());

        setContentView(R.layout.activity_admin_profile);

        progressBar = findViewById(R.id.progressBar);
        tvHeaderName = findViewById(R.id.tvHeaderName);
        tvFullName = findViewById(R.id.tvFullName);
        tvEmail = findViewById(R.id.tvEmail);
        tvPhone = findViewById(R.id.tvPhone);

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        fetchUserProfile();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_profile), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        setupBottomNavigation();

        findViewById(R.id.btnEditProfile).setOnClickListener(v -> showEditProfileDialog());
        findViewById(R.id.btnChangePassword).setOnClickListener(v -> startActivity(new Intent(this, ChangePasswordActivity.class)));
        findViewById(R.id.btnLogout).setOnClickListener(v -> showLogoutConfirmation());
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.nav_profile);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                startActivity(new Intent(this, AdminDashboardActivity.class));
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                finish();
                return true;
            } else if (itemId == R.id.nav_history) {
                Intent intent = new Intent(this, MoistureHistoryActivity.class);
                intent.putExtra("is_admin", true);
                startActivity(intent);
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                finish();
                return true;
            } else if (itemId == R.id.nav_manage) {
                startActivity(new Intent(this, FarmerListActivity.class));
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                finish();
                return true;
            } else if (itemId == R.id.nav_profile) {
                return true;
            }
            return false;
        });
    }

    private void fetchUserProfile() {
        progressBar.setVisibility(View.VISIBLE);
        userRef.get().addOnSuccessListener(documentSnapshot -> {
            progressBar.setVisibility(View.GONE);
            if (documentSnapshot.exists()) {
                UserProfile profile = documentSnapshot.toObject(UserProfile.class);
                if (profile != null) {
                    tvHeaderName.setText(profile.getFullName());
                    tvFullName.setText(profile.getFullName());
                    tvEmail.setText(profile.getEmail());
                    tvPhone.setText(profile.getPhone());
                }
            }
        });
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.logout)
                .setMessage(R.string.logout_confirmation)
                .setPositiveButton(R.string.logout, (dialog, which) -> {
                    mAuth.signOut();
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditProfileDialog() {
        progressBar.setVisibility(View.VISIBLE);
        userRef.get().addOnSuccessListener(documentSnapshot -> {
            progressBar.setVisibility(View.GONE);
            if (!documentSnapshot.exists()) return;
            UserProfile profile = documentSnapshot.toObject(UserProfile.class);
            if (profile == null) return;

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Edit Profile");

            View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null);
            EditText etName = view.findViewById(R.id.etName);
            EditText etPhone = view.findViewById(R.id.etPhone);
            View farmFields = view.findViewById(R.id.farmFields);
            if (farmFields != null) farmFields.setVisibility(View.GONE);

            etName.setText(profile.getFullName());
            etPhone.setText(profile.getPhone());

            builder.setView(view);
            builder.setPositiveButton("Save", (dialog, which) -> {
                Map<String, Object> updates = new HashMap<>();
                updates.put("fullName", etName.getText().toString().trim());
                updates.put("phone", etPhone.getText().toString().trim());

                progressBar.setVisibility(View.VISIBLE);
                userRef.update(updates).addOnSuccessListener(aVoid -> {
                    fetchUserProfile();
                    Toast.makeText(this, "Profile Updated", Toast.LENGTH_SHORT).show();
                });
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        });
    }
}