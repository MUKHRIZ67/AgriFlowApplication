package com.example.agriflowapplication;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import android.content.Intent;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;

import android.widget.TextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class DashboardActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private TextView tvUserName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseManager.getInstance().getAuth();
        db = FirebaseManager.getInstance().getDb();

        // Session Protection using Firebase
        if (mAuth.getCurrentUser() == null) {
            redirectToLogin();
            return;
        }

        setContentView(R.layout.activity_dashboard);
        tvUserName = findViewById(R.id.tvUserName);

        fetchUserName();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_dashboard), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.nav_home);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                return true;
            } else if (itemId == R.id.nav_profile) {
                startActivity(new Intent(DashboardActivity.this, ProfileActivity.class));
                return true;
            }
            return false;
        });

        // Logout Button Listener
        findViewById(R.id.btnLogout).setOnClickListener(v -> showLogoutConfirmation());
    }

    private void fetchUserName() {
        if (mAuth.getCurrentUser() != null) {
            String userId = mAuth.getCurrentUser().getUid();
            db.collection("users").document(userId).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String fullName = documentSnapshot.getString("fullName");
                            if (fullName != null && !fullName.isEmpty()) {
                                // Display only the first name
                                String firstName = fullName.split(" ")[0];
                                tvUserName.setText(firstName);
                            }
                        }
                    });
        }
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
        Intent intent = new Intent(DashboardActivity.this, MainActivity.class);
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
}