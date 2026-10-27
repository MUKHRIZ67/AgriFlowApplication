package com.example.agriflowapplication;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ChangePasswordActivity extends AppCompatActivity {

    private TextInputEditText etCurrentPassword, etNewPassword, etConfirmPassword;
    private Button btnChangePassword;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private boolean isAdmin = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        isAdmin = getIntent().getBooleanExtra("is_admin", false);
        if (!isAdmin) {
            String role = getSharedPreferences("AgriFlowPrefs", MODE_PRIVATE).getString("user_role", "farmer");
            if ("admin".equalsIgnoreCase(role)) {
                isAdmin = true;
            }
        }

        if (isAdmin) {
            setTheme(R.style.Theme_AgriFlow_Admin);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        mAuth = FirebaseManager.getInstance().getAuth();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        etCurrentPassword = findViewById(R.id.etCurrentPassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        progressBar = findViewById(R.id.progressBar);

        if (isAdmin) {
            applyAdminTheme();
        }

        btnChangePassword.setOnClickListener(v -> validateAndConfirm());
    }

    private void applyAdminTheme() {
        View mainLayout = findViewById(R.id.main_layout);
        if (mainLayout != null) {
            mainLayout.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.iot_bg));
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitleTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.iot_on_surface));
            toolbar.setNavigationIconTint(androidx.core.content.ContextCompat.getColor(this, R.color.iot_on_surface));
        }

        TextView tvTitle = findViewById(R.id.tvTitle);
        if (tvTitle != null) {
            tvTitle.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.iot_on_surface));
        }

        TextView tvSubtitle = findViewById(R.id.tvSubtitle);
        if (tvSubtitle != null) {
            tvSubtitle.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.iot_on_surface_variant));
        }

        TextInputLayout tilCurrent = findViewById(R.id.tilCurrentPassword);
        TextInputLayout tilNew = findViewById(R.id.tilNewPassword);
        TextInputLayout tilConfirm = findViewById(R.id.tilConfirmPassword);

        styleTextInputLayoutAdmin(tilCurrent);
        styleTextInputLayoutAdmin(tilNew);
        styleTextInputLayoutAdmin(tilConfirm);

        if (etCurrentPassword != null) etCurrentPassword.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.iot_on_surface));
        if (etNewPassword != null) etNewPassword.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.iot_on_surface));
        if (etConfirmPassword != null) etConfirmPassword.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.iot_on_surface));

        if (btnChangePassword != null) {
            btnChangePassword.setBackgroundTintList(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this, R.color.iot_primary)));
            btnChangePassword.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.white));
        }

        if (progressBar != null) {
            progressBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this, R.color.iot_primary)));
        }
    }

    private void styleTextInputLayoutAdmin(TextInputLayout til) {
        if (til == null) return;
        til.setBoxBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.iot_surface_high));
        til.setBoxStrokeColor(androidx.core.content.ContextCompat.getColor(this, R.color.iot_primary));
        til.setHintTextColor(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this, R.color.iot_primary)));
        til.setDefaultHintTextColor(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this, R.color.iot_on_surface_variant)));
        til.setPlaceholderTextColor(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this, R.color.iot_on_surface_variant)));
        til.setEndIconTintList(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this, R.color.iot_on_surface_variant)));
    }

    private void validateAndConfirm() {
        String currentPass = etCurrentPassword.getText().toString().trim();
        String newPass = etNewPassword.getText().toString().trim();
        String confirmPass = etConfirmPassword.getText().toString().trim();

        if (TextUtils.isEmpty(currentPass)) {
            etCurrentPassword.setError("Current password is required");
            return;
        }

        if (TextUtils.isEmpty(newPass)) {
            etNewPassword.setError("New password is required");
            return;
        }

        if (newPass.length() < 6) {
            etNewPassword.setError("Password must be at least 6 characters");
            return;
        }

        if (!newPass.equals(confirmPass)) {
            etConfirmPassword.setError("Passwords do not match");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Change Password")
                .setMessage("Are you sure you want to change your password?")
                .setPositiveButton("Yes", (dialog, which) -> performPasswordChange(currentPass, newPass))
                .setNegativeButton("No", null)
                .show();
    }

    private void performPasswordChange(String currentPass, String newPass) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null && user.getEmail() != null) {
            progressBar.setVisibility(View.VISIBLE);
            btnChangePassword.setEnabled(false);

            // Re-authenticate user
            AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPass);
            user.reauthenticate(credential).addOnCompleteListener(reAuthTask -> {
                if (reAuthTask.isSuccessful()) {
                    // Update password
                    user.updatePassword(newPass).addOnCompleteListener(updateTask -> {
                        progressBar.setVisibility(View.GONE);
                        btnChangePassword.setEnabled(true);
                        if (updateTask.isSuccessful()) {
                            Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            String error = updateTask.getException() != null ? updateTask.getException().getMessage() : "Update failed";
                            Toast.makeText(this, "Error: " + error, Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    progressBar.setVisibility(View.GONE);
                    btnChangePassword.setEnabled(true);
                    String error = reAuthTask.getException() != null ? reAuthTask.getException().getMessage() : "Authentication failed";
                    Toast.makeText(this, "Current password incorrect: " + error, Toast.LENGTH_LONG).show();
                    etCurrentPassword.setError("Incorrect password");
                }
            });
        }
    }
}
