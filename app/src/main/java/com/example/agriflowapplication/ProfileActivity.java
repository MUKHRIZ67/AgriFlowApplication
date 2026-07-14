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

public class ProfileActivity extends AppCompatActivity {

    private LocalDataManager dataManager;
    private TextView tvHeaderName, tvFullName, tvEmail, tvPhone, tvFarmName, tvFarmLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dataManager = LocalDataManager.getInstance(this);

        // Session Protection
        if (!dataManager.isLoggedIn()) {
            redirectToLogin();
            return;
        }

        setContentView(R.layout.activity_profile);

        // Initialize App Bar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // Initialize Views
        tvHeaderName = findViewById(R.id.tvHeaderName);
        tvFullName = findViewById(R.id.tvFullName);
        tvEmail = findViewById(R.id.tvEmail);
        tvPhone = findViewById(R.id.tvPhone);
        tvFarmName = findViewById(R.id.tvFarmName);
        tvFarmLocation = findViewById(R.id.tvFarmLocation);

        updateUI();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_profile), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        // Bottom Navigation
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
        dataManager.logout();
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

    private void updateUI() {
        tvHeaderName.setText(dataManager.getFullName());
        tvFullName.setText(dataManager.getFullName());
        tvEmail.setText(dataManager.getEmail());
        tvPhone.setText(dataManager.getPhone());
        tvFarmName.setText(dataManager.getFarmName());
        tvFarmLocation.setText(dataManager.getFarmLocation());
    }

    private void showEditProfileDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Profile");

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null);
        EditText etName = view.findViewById(R.id.etName);
        EditText etPhone = view.findViewById(R.id.etPhone);
        EditText etFarmName = view.findViewById(R.id.etFarmName);
        EditText etFarmLocation = view.findViewById(R.id.etFarmLocation);

        etName.setText(dataManager.getFullName());
        etPhone.setText(dataManager.getPhone());
        etFarmName.setText(dataManager.getFarmName());
        etFarmLocation.setText(dataManager.getFarmLocation());

        builder.setView(view);
        builder.setPositiveButton("Save", (dialog, which) -> {
            dataManager.updateProfile(
                    etName.getText().toString(),
                    etPhone.getText().toString(),
                    etFarmName.getText().toString(),
                    etFarmLocation.getText().toString()
            );
            updateUI();
            Toast.makeText(this, "Profile Updated", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showChangePasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Password");

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null);
        EditText etCurrentPassword = view.findViewById(R.id.etCurrentPassword);
        EditText etNewPassword = view.findViewById(R.id.etNewPassword);

        builder.setView(view);
        builder.setPositiveButton("Update", (dialog, which) -> {
            boolean success = dataManager.changePassword(
                    etCurrentPassword.getText().toString(),
                    etNewPassword.getText().toString()
            );
            if (success) {
                Toast.makeText(this, "Password Changed", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Current Password Incorrect", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}