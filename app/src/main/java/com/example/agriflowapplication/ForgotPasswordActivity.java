package com.example.agriflowapplication;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText etResetEmail;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        mAuth = FirebaseManager.getInstance().getAuth();

        etResetEmail = findViewById(R.id.etResetEmail);
        Button btnSendResetLink = findViewById(R.id.btnSendResetLink);
        TextView btnBackToLogin = findViewById(R.id.btnBackToLogin);

        btnSendResetLink.setOnClickListener(v -> sendResetEmail());

        btnBackToLogin.setOnClickListener(v -> finish());
    }

    private void sendResetEmail() {
        String email = etResetEmail.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            etResetEmail.setError("Email is required");
            return;
        }

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(ForgotPasswordActivity.this, R.string.email_sent_success, Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        String error = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        Toast.makeText(ForgotPasswordActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
                    }
                });
    }
}
