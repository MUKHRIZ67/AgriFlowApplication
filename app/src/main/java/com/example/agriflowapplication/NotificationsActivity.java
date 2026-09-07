package com.example.agriflowapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity {

    private RecyclerView rvNotifications;
    private NotificationAdapter adapter;
    private List<NotificationRecord> notifications;
    private List<NotificationRecord> displayedNotifications;
    private View emptyState;
    private FirebaseFirestore db;
    private String userId;
    private boolean showOnlyUnread = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        db = FirebaseManager.getInstance().getDb();
        userId = FirebaseAuth.getInstance().getUid();

        if (userId == null) {
            finish();
            return;
        }

        rvNotifications = findViewById(R.id.rvNotifications);
        emptyState = findViewById(R.id.emptyState);
        ChipGroup chipGroupFilter = findViewById(R.id.chipGroupFilter);

        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        notifications = new ArrayList<>();
        displayedNotifications = new ArrayList<>();
        adapter = new NotificationAdapter(displayedNotifications, this::handleNotificationClick);
        rvNotifications.setAdapter(adapter);

        // Handle Toolbar Back
        ((com.google.android.material.appbar.MaterialToolbar)findViewById(R.id.toolbar))
                .setNavigationOnClickListener(v -> finish());

        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            showOnlyUnread = checkedIds.get(0) == R.id.chipUnread;
            applyFilter();
        });

        loadNotifications();
    }

    private void loadNotifications() {
        // Log for debugging
        android.util.Log.d("AgriFlow_Debug", "Loading notifications for user: " + userId);
        
        db.collection("notifications")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    android.util.Log.d("AgriFlow_Debug", "Query success. Records found: " + queryDocumentSnapshots.size());
                    notifications.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        try {
                            NotificationRecord record = doc.toObject(NotificationRecord.class);
                            record.setId(doc.getId());
                            notifications.add(record);
                        } catch (Exception e) {
                            android.util.Log.e("AgriFlow_Debug", "Error parsing record: " + doc.getId(), e);
                        }
                    }
                    
                    // Sort manually by timestamp to avoid index requirement for simple fetch
                    java.util.Collections.sort(notifications, (a, b) -> {
                        if (a.getTimestamp() == null || b.getTimestamp() == null) return 0;
                        return b.getTimestamp().compareTo(a.getTimestamp());
                    });
                    
                    applyFilter();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("AgriFlow_Debug", "Firestore Query Failed", e);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void applyFilter() {
        displayedNotifications.clear();
        if (showOnlyUnread) {
            for (NotificationRecord n : notifications) {
                if (!n.isRead()) displayedNotifications.add(n);
            }
        } else {
            displayedNotifications.addAll(notifications);
        }
        adapter.notifyDataSetChanged();
        emptyState.setVisibility(displayedNotifications.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void handleNotificationClick(NotificationRecord notification) {
        // Mark as read in Firestore
        if (!notification.isRead()) {
            db.collection("notifications").document(notification.getId())
                    .update("read", true)
                    .addOnSuccessListener(aVoid -> {
                        notification.setRead(true);
                        adapter.notifyDataSetChanged();
                    });
        }

        // Navigate to Dashboard/Telemetry
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }
}
