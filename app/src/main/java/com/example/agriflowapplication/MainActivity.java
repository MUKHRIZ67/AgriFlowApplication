package com.example.agriflowapplication;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseManager.getInstance().getAuth();

        // Auto-login check using Firebase
        if (mAuth.getCurrentUser() != null) {
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Check for logout message
        if (getIntent().getBooleanExtra("logout_message", false)) {
            Toast.makeText(this, R.string.logged_out_success, Toast.LENGTH_SHORT).show();
        }

        // Initialize Views
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        Button btnLogin = findViewById(R.id.btnLogin);
        TextView btnForgotPassword = findViewById(R.id.btnForgotPassword);
        TextView btnRegister = findViewById(R.id.btnRegister);

        // Login Button Listener
        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (TextUtils.isEmpty(email)) {
                etEmail.setError("Email is required");
                return;
            }

            if (TextUtils.isEmpty(password)) {
                etPassword.setError("Password is required");
                return;
            }

            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(MainActivity.this, "Login Successful!", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
                            startActivity(intent);
                            finish();
                        } else {
                            String error = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                            Toast.makeText(MainActivity.this, "Authentication Failed: " + error, Toast.LENGTH_LONG).show();
                        }
                    });
        });

        // Forgot Password Listener
        btnForgotPassword.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ForgotPasswordActivity.class);
            startActivity(intent);
        });

        // Register Button Listener
        btnRegister.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }
}
