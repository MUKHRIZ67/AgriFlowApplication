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

        // Session Protection
        if (mAuth.getCurrentUser() == null) {
            redirectToLogin();
            return;
        }

        userRef = db.collection("users").document(mAuth.getCurrentUser().getUid());

        setContentView(R.layout.activity_profile);

        // Initialize Views
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

        // Initialize App Bar
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

        // Button Click Listeners
        findViewById(R.id.btnEditProfile).setOnClickListener(v -> showEditProfileDialog());
        findViewById(R.id.btnChangePassword).setOnClickListener(v -> showChangePasswordDialog());
        findViewById(R.id.btnLogout).setOnClickListener(v -> showLogoutConfirmation());
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
        Intent intent = new Intent(ProfileActivity.this, MainActivity.class);
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

    private void fetchUserProfile() {
        progressBar.setVisibility(View.VISIBLE);
        userRef.get().addOnSuccessListener(documentSnapshot -> {
            progressBar.setVisibility(View.GONE);
            if (documentSnapshot.exists()) {
                UserProfile profile = documentSnapshot.toObject(UserProfile.class);
                if (profile != null) {
                    displayProfile(profile);
                }
            } else {
                Toast.makeText(this, "Profile not found", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Error fetching profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void displayProfile(UserProfile profile) {
        tvHeaderName.setText(profile.getFullName());
        tvFullName.setText(profile.getFullName());
        tvEmail.setText(profile.getEmail());
        tvPhone.setText(profile.getPhone());
        
        boolean isAdmin = "admin".equalsIgnoreCase(profile.getRole());
        
        if (isAdmin) {
            tvUserRole.setText("Admin");
            tvUserRole.setBackgroundTintList(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this, R.color.warning_orange)));
            farmContainer1.setVisibility(View.GONE);
            farmContainer2.setVisibility(View.GONE);
            setupAdminNavigation();
        } else {
            tvUserRole.setText("Farmer");
            tvFarmName.setText(profile.getFarmName());
            tvFarmLocation.setText(profile.getFarmLocation());
            farmContainer1.setVisibility(View.VISIBLE);
            farmContainer2.setVisibility(View.VISIBLE);
            setupFarmerNavigation();
        }
    }

    private void setupAdminNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.getMenu().clear();
        bottomNavigationView.inflateMenu(R.menu.admin_bottom_nav_menu);
        bottomNavigationView.setSelectedItemId(R.id.nav_profile);
        
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                startActivity(new Intent(ProfileActivity.this, AdminDashboardActivity.class));
                return true;
            } else if (itemId == R.id.nav_manage) {
                startActivity(new Intent(ProfileActivity.this, FarmerListActivity.class));
                return true;
            } else if (itemId == R.id.nav_profile) {
                return true;
            }
            return false;
        });
    }

    private void setupFarmerNavigation() {
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.nav_profile);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                startActivity(new Intent(ProfileActivity.this, DashboardActivity.class));
                return true;
            } else if (itemId == R.id.nav_profile) {
                return true;
            }
            return false;
        });
    }

    private void showEditProfileDialog() {
        userRef.get().addOnSuccessListener(documentSnapshot -> {
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
            View farmFields = view.findViewById(R.id.farmFields); // Assumes we have this ID in dialog_edit_profile

            etName.setText(profile.getFullName());
            etPhone.setText(profile.getPhone());
            
            if ("admin".equalsIgnoreCase(profile.getRole())) {
                if (farmFields != null) farmFields.setVisibility(View.GONE);
            } else {
                etFarmName.setText(profile.getFarmName());
                etFarmLocation.setText(profile.getFarmLocation());
            }

            builder.setView(view);
            builder.setPositiveButton("Save", (dialog, which) -> {
                Map<String, Object> updates = new HashMap<>();
                updates.put("fullName", etName.getText().toString());
                updates.put("phone", etPhone.getText().toString());
                
                if (!"admin".equalsIgnoreCase(profile.getRole())) {
                    updates.put("farmName", etFarmName.getText().toString());
                    updates.put("farmLocation", etFarmLocation.getText().toString());
                }

                progressBar.setVisibility(View.VISIBLE);
                userRef.update(updates).addOnSuccessListener(aVoid -> {
                    fetchUserProfile();
                    Toast.makeText(this, "Profile Updated", Toast.LENGTH_SHORT).show();
                }).addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show();
                });
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        });
    }

    private void showChangePasswordDialog() {
        Intent intent = new Intent(ProfileActivity.this, ChangePasswordActivity.class);
        startActivity(intent);
    }
}
