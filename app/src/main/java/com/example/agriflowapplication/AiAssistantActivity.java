package com.example.agriflowapplication;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class AiAssistantActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private AgriFlowAiService aiService;

    private RecyclerView rvChatMessages;
    private AiMessageAdapter adapter;
    private List<AiMessage> messageList;

    private EditText etPromptInput;
    private View layoutLoadingState;
    private View btnSendPrompt;

    private boolean isQueryProcessing = false;
    private boolean isAdminRole = false;

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

        setContentView(R.layout.activity_ai_assistant);

        aiService = new AgriFlowAiService();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        rvChatMessages = findViewById(R.id.rvChatMessages);
        etPromptInput = findViewById(R.id.etPromptInput);
        layoutLoadingState = findViewById(R.id.layoutLoadingState);
        btnSendPrompt = findViewById(R.id.btnSendPrompt);

        // Check intent for role
        isAdminRole = getIntent().getBooleanExtra("is_admin", false);

        // Apply WindowInsets for status bar, navigation bar, and IME (Soft Keyboard)
        View rootLayout = findViewById(R.id.main_ai_layout);
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

                int bottomInset = Math.max(systemBars.bottom, ime.bottom);
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomInset);
                return WindowInsetsCompat.CONSUMED;
            });
        }

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvChatMessages.setLayoutManager(layoutManager);

        messageList = new ArrayList<>();
        adapter = new AiMessageAdapter(messageList);
        rvChatMessages.setAdapter(adapter);

        // Apply initial theme based on intent extra
        applyRoleTheme(isAdminRole);

        // Fetch user role from Firestore for verification
        fetchUserRole();

        // Auto-scroll chat to latest message when soft keyboard opens or layout resizes
        rvChatMessages.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom < oldBottom && adapter != null && adapter.getItemCount() > 0) {
                rvChatMessages.post(() -> rvChatMessages.smoothScrollToPosition(adapter.getItemCount() - 1));
            }
        });

        // Add Welcome Message
        addAiMessage("Hi! I'm your AgriFlow Farm Assistant. I can help you understand your farm's sensor data, irrigation conditions, fertilizer schedules and alerts.");

        // Setup Quick Action Chips
        setupQuickActionChips();

        // Send Button
        btnSendPrompt.setOnClickListener(v -> {
            String text = etPromptInput.getText().toString().trim();
            if (!text.isEmpty()) {
                submitPrompt(text);
                etPromptInput.setText("");
            }
        });
    }

    private void fetchUserRole() {
        if (mAuth.getCurrentUser() != null) {
            String uid = mAuth.getCurrentUser().getUid();
            db.collection("users").document(uid).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String role = documentSnapshot.getString("role");
                            boolean isAdmin = "admin".equalsIgnoreCase(role);
                            if (isAdmin != isAdminRole) {
                                isAdminRole = isAdmin;
                                applyRoleTheme(isAdminRole);
                            }
                        }
                    });
        }
    }

    private void applyRoleTheme(boolean isAdmin) {
        if (adapter != null) {
            adapter.setIsAdmin(isAdmin);
        }

        View rootLayout = findViewById(R.id.main_ai_layout);
        ImageButton btnBack = findViewById(R.id.btnBack);
        TextView tvAiTitle = findViewById(R.id.tvAiTitle);
        TextView tvAiSubtitle = findViewById(R.id.tvAiSubtitle);
        View dividerBar = findViewById(R.id.dividerBar);
        TextView tvLoadingText = findViewById(R.id.tvLoadingText);
        MaterialCardView inputCard = findViewById(R.id.inputCard);
        EditText etPromptInput = findViewById(R.id.etPromptInput);
        MaterialButton btnSendPrompt = findViewById(R.id.btnSendPrompt);

        if (isAdmin) {
            // Admin IoT Dark Theme (Blue Accent)
            if (rootLayout != null) rootLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.iot_bg));
            if (btnBack != null) btnBack.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.iot_on_surface)));
            if (tvAiTitle != null) tvAiTitle.setTextColor(ContextCompat.getColor(this, R.color.iot_on_surface));
            if (tvAiSubtitle != null) tvAiSubtitle.setTextColor(ContextCompat.getColor(this, R.color.iot_secondary));
            if (dividerBar != null) dividerBar.setBackgroundColor(ContextCompat.getColor(this, R.color.iot_border));
            if (tvLoadingText != null) tvLoadingText.setTextColor(ContextCompat.getColor(this, R.color.iot_on_surface_variant));
            if (inputCard != null) inputCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.iot_surface));
            if (etPromptInput != null) {
                etPromptInput.setTextColor(ContextCompat.getColor(this, R.color.iot_on_surface));
                etPromptInput.setHintTextColor(ContextCompat.getColor(this, R.color.iot_on_surface_variant));
            }
            if (btnSendPrompt != null) btnSendPrompt.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.iot_primary)));

            // Style Quick Action Chips for Admin
            int chipBg = ContextCompat.getColor(this, R.color.iot_surface_high);
            int chipText = ContextCompat.getColor(this, R.color.iot_on_surface);
            int chipStroke = ContextCompat.getColor(this, R.color.iot_border);
            int chipIconTint = ContextCompat.getColor(this, R.color.iot_secondary);

            int[] chipIds = {
                R.id.chipAnalyzeFarm, R.id.chipCheckSoil, R.id.chipCheckWater,
                R.id.chipCheckEnv, R.id.chipExplainAlerts, R.id.chipCheckIrrigation,
                R.id.chipCheckFertilizer, R.id.chipSummarizeToday
            };

            for (int id : chipIds) {
                Chip chip = findViewById(id);
                if (chip != null) {
                    chip.setChipBackgroundColor(ColorStateList.valueOf(chipBg));
                    chip.setTextColor(chipText);
                    chip.setChipStrokeColor(ColorStateList.valueOf(chipStroke));
                    chip.setChipIconTint(ColorStateList.valueOf(chipIconTint));
                }
            }

        } else {
            // Farmer Light Cream Theme (Green Accent)
            if (rootLayout != null) rootLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_light_cream));
            if (btnBack != null) btnBack.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.on_surface)));
            if (tvAiTitle != null) tvAiTitle.setTextColor(ContextCompat.getColor(this, R.color.on_surface));
            if (tvAiSubtitle != null) tvAiSubtitle.setTextColor(ContextCompat.getColor(this, R.color.primary_dark_green));
            if (dividerBar != null) dividerBar.setBackgroundColor(ContextCompat.getColor(this, R.color.surface_container_high));
            if (tvLoadingText != null) tvLoadingText.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant));
            if (inputCard != null) inputCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_container));
            if (etPromptInput != null) {
                etPromptInput.setTextColor(ContextCompat.getColor(this, R.color.on_surface));
                etPromptInput.setHintTextColor(ContextCompat.getColor(this, R.color.on_surface_variant));
            }
            if (btnSendPrompt != null) btnSendPrompt.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary_dark_green)));

            // Style Quick Action Chips for Farmer
            int chipBg = ContextCompat.getColor(this, R.color.surface_container);
            int chipText = ContextCompat.getColor(this, R.color.on_surface);
            int chipStroke = ContextCompat.getColor(this, R.color.surface_container_high);
            int chipIconTint = ContextCompat.getColor(this, R.color.primary_dark_green);

            int[] chipIds = {
                R.id.chipAnalyzeFarm, R.id.chipCheckSoil, R.id.chipCheckWater,
                R.id.chipCheckEnv, R.id.chipExplainAlerts, R.id.chipCheckIrrigation,
                R.id.chipCheckFertilizer, R.id.chipSummarizeToday
            };

            for (int id : chipIds) {
                Chip chip = findViewById(id);
                if (chip != null) {
                    chip.setChipBackgroundColor(ColorStateList.valueOf(chipBg));
                    chip.setTextColor(chipText);
                    chip.setChipStrokeColor(ColorStateList.valueOf(chipStroke));
                    chip.setChipIconTint(ColorStateList.valueOf(chipIconTint));
                }
            }
        }
    }

    private void setupQuickActionChips() {
        Chip chipAnalyzeFarm = findViewById(R.id.chipAnalyzeFarm);
        Chip chipCheckSoil = findViewById(R.id.chipCheckSoil);
        Chip chipCheckWater = findViewById(R.id.chipCheckWater);
        Chip chipCheckEnv = findViewById(R.id.chipCheckEnv);
        Chip chipExplainAlerts = findViewById(R.id.chipExplainAlerts);
        Chip chipCheckIrrigation = findViewById(R.id.chipCheckIrrigation);
        Chip chipCheckFertilizer = findViewById(R.id.chipCheckFertilizer);
        Chip chipSummarizeToday = findViewById(R.id.chipSummarizeToday);

        if (chipAnalyzeFarm != null) chipAnalyzeFarm.setOnClickListener(v -> submitPrompt("Analyze My Farm"));
        if (chipCheckSoil != null) chipCheckSoil.setOnClickListener(v -> submitPrompt("Check Soil"));
        if (chipCheckWater != null) chipCheckWater.setOnClickListener(v -> submitPrompt("Check Water Tank"));
        if (chipCheckEnv != null) chipCheckEnv.setOnClickListener(v -> submitPrompt("Check Environment"));
        if (chipExplainAlerts != null) chipExplainAlerts.setOnClickListener(v -> submitPrompt("Explain My Alerts"));
        if (chipCheckIrrigation != null) chipCheckIrrigation.setOnClickListener(v -> submitPrompt("Check Irrigation"));
        if (chipCheckFertilizer != null) chipCheckFertilizer.setOnClickListener(v -> submitPrompt("Check Fertilizer"));
        if (chipSummarizeToday != null) chipSummarizeToday.setOnClickListener(v -> submitPrompt("Summarize Today"));
    }

    private void submitPrompt(String prompt) {
        if (isQueryProcessing) {
            Toast.makeText(this, "AgriFlow AI is analyzing your previous query...", Toast.LENGTH_SHORT).show();
            return;
        }

        // Add user message to UI
        addUserMessage(prompt);

        // Set Loading State
        setLoadingState(true);

        aiService.generateResponse(this, prompt, new AgriFlowAiService.AiCallback() {
            @Override
            public void onSuccess(String response) {
                setLoadingState(false);
                addAiMessage(response);
            }

            @Override
            public void onError(String errorMessage) {
                setLoadingState(false);
                addAiMessage(errorMessage);
            }
        });
    }

    private void addUserMessage(String text) {
        messageList.add(new AiMessage(AiMessage.TYPE_USER, text));
        adapter.notifyItemInserted(messageList.size() - 1);
        rvChatMessages.smoothScrollToPosition(messageList.size() - 1);
    }

    private void addAiMessage(String text) {
        messageList.add(new AiMessage(AiMessage.TYPE_AI, text));
        adapter.notifyItemInserted(messageList.size() - 1);
        rvChatMessages.smoothScrollToPosition(messageList.size() - 1);
    }

    private void setLoadingState(boolean loading) {
        isQueryProcessing = loading;
        if (layoutLoadingState != null) {
            layoutLoadingState.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (btnSendPrompt != null) {
            btnSendPrompt.setEnabled(!loading);
        }
    }
}
