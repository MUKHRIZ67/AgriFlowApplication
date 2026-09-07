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

public class ProfileActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private DocumentReference userRef;
    
    private TextView tvHeaderName, tvUserRole, tvFullName, tvEmail, tvPhone, tvFarmName, tvFarmLocation;
    private View farmContainer1, farmContainer2;
    private android.widget.ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseManager.getInstance().getAuth();
        db = FirebaseManager.getInstance().getDb();

        if (mAuth.getCurrentUser() == null) {
            redirectToLogin();
            return;
        }

        userRef = db.collection("users").document(mAuth.getCurrentUser().getUid());

        setContentView(R.layout.activity_profile);
        findViewById(R.id.main_profile).setVisibility(View.VISIBLE);

        progressBar = findViewById(R.id.progressBar);
        tvHeaderName = findViewById(R.id.tvHeaderName);
        tvUserRole = findViewById(R.id.tvUserRole);
        tvFullName = findViewById(R.id.tvFullName);
        tvEmail = findViewById(R.id.tvEmail);
        tvPhone = findViewById(R.id.tvPhone);
        tvFarmName = findViewById(R.id.tvFarmName);
        tvFarmLocation = findViewById(R.id.tvFarmLocation);
        farmContainer1 = findViewById(R.id.farmContainer1);
        farmContainer2 = findViewById(R.id.farmContainer2);

        if (farmContainer1 != null) farmContainer1.setVisibility(View.VISIBLE);
        if (farmContainer2 != null) farmContainer2.setVisibility(View.VISIBLE);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
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
                    tvFarmName.setText(profile.getFarmName());
                    tvFarmLocation.setText(profile.getFarmLocation());
                    tvUserRole.setText("Farmer");
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

    private void redirectToLogin() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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
            EditText etFarmName = view.findViewById(R.id.etFarmName);
            EditText etFarmLocation = view.findViewById(R.id.etFarmLocation);

            etName.setText(profile.getFullName());
            etPhone.setText(profile.getPhone());
            etFarmName.setText(profile.getFarmName());
            etFarmLocation.setText(profile.getFarmLocation());

            builder.setView(view);
            builder.setPositiveButton("Save", (dialog, which) -> {
                Map<String, Object> updates = new HashMap<>();
                updates.put("fullName", etName.getText().toString().trim());
                updates.put("phone", etPhone.getText().toString().trim());
                updates.put("farmName", etFarmName.getText().toString().trim());
                updates.put("farmLocation", etFarmLocation.getText().toString().trim());

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