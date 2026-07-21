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

import android.text.TextUtils;
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

        // Initialize App Bar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        // Initialize Views
        tvHeaderName = findViewById(R.id.tvHeaderName);
        tvUserRole = findViewById(R.id.tvUserRole);
        tvFullName = findViewById(R.id.tvFullName);
        tvEmail = findViewById(R.id.tvEmail);
        tvPhone = findViewById(R.id.tvPhone);
        tvFarmName = findViewById(R.id.tvFarmName);
        tvFarmLocation = findViewById(R.id.tvFarmLocation);

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
        userRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                tvHeaderName.setText(documentSnapshot.getString("fullName"));
                tvFullName.setText(documentSnapshot.getString("fullName"));
                tvEmail.setText(documentSnapshot.getString("email"));
                tvPhone.setText(documentSnapshot.getString("phone"));
                tvFarmName.setText(documentSnapshot.getString("farmName"));
                tvFarmLocation.setText(documentSnapshot.getString("farmLocation"));

                // Handle Role and Navigation
                String role = documentSnapshot.getString("role");
                boolean isAdmin = "admin".equalsIgnoreCase(role);
                
                if (isAdmin) {
                    tvUserRole.setText("Admin");
                    setupAdminNavigation();
                } else {
                    tvUserRole.setText("Farmer");
                    setupFarmerNavigation();
                }
            }
        }).addOnFailureListener(e -> Toast.makeText(this, "Error fetching profile", Toast.LENGTH_SHORT).show());
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
            } else if (itemId == R.id.nav_history) {
                // Future implementation
                return true;
            } else if (itemId == R.id.nav_schedule) {
                // Future implementation
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
        // Default menu is already bottom_nav_menu, but we set listener
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

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Edit Profile");

            View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null);
            EditText etName = view.findViewById(R.id.etName);
            EditText etPhone = view.findViewById(R.id.etPhone);
            EditText etFarmName = view.findViewById(R.id.etFarmName);
            EditText etFarmLocation = view.findViewById(R.id.etFarmLocation);

            etName.setText(documentSnapshot.getString("fullName"));
            etPhone.setText(documentSnapshot.getString("phone"));
            etFarmName.setText(documentSnapshot.getString("farmName"));
            etFarmLocation.setText(documentSnapshot.getString("farmLocation"));

            builder.setView(view);
            builder.setPositiveButton("Save", (dialog, which) -> {
                Map<String, Object> updates = new HashMap<>();
                updates.put("fullName", etName.getText().toString());
                updates.put("phone", etPhone.getText().toString());
                updates.put("farmName", etFarmName.getText().toString());
                updates.put("farmLocation", etFarmLocation.getText().toString());

                userRef.update(updates).addOnSuccessListener(aVoid -> {
                    fetchUserProfile();
                    Toast.makeText(this, "Profile Updated", Toast.LENGTH_SHORT).show();
                });
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        });
    }

    private void showChangePasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Password");

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null);
        EditText etNewPassword = view.findViewById(R.id.etNewPassword);

        builder.setView(view);
        builder.setPositiveButton("Update", (dialog, which) -> {
            String newPass = etNewPassword.getText().toString();
            if (TextUtils.isEmpty(newPass)) return;

            if (mAuth.getCurrentUser() != null) {
                mAuth.getCurrentUser().updatePassword(newPass)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(this, "Password Changed", Toast.LENGTH_SHORT).show();
                            } else {
                                String error = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                                Toast.makeText(this, "Error: " + error, Toast.LENGTH_LONG).show();
                            }
                        });
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}
