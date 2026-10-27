package com.example.agriflowapplication;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * AppNotificationManager provides notification dispatch and persistence for AgriFlow.
 */
public class AppNotificationManager {

    private static final String CHANNEL_ID = "agriflow_sensor_alerts";
    private static final String CHANNEL_NAME = "AgriFlow Sensor Alerts";
    private static final String CHANNEL_DESC = "Notifications for environmental sensor alerts and schedules";

    private static AppNotificationManager instance;
    private final Context context;

    private AppNotificationManager(Context context) {
        this.context = context.getApplicationContext();
        createNotificationChannel();
    }

    public static synchronized AppNotificationManager getInstance(Context context) {
        if (instance == null) {
            instance = new AppNotificationManager(context);
        }
        return instance;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(CHANNEL_DESC);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    public void triggerAlertNotification(String title, String message, String alertType, double sensorValue, double threshold, String status) {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) return;

        // 1. Persist to Firestore
        NotificationRecord record = new NotificationRecord(
                title,
                message,
                sensorValue,
                status,
                alertType,
                sensorValue,
                threshold,
                Timestamp.now(),
                userId,
                "Farm"
        );
        FirebaseFirestore.getInstance().collection("notifications").add(record);

        // 2. Show Push Notification
        String role = context.getSharedPreferences("AgriFlowPrefs", Context.MODE_PRIVATE).getString("user_role", "farmer");
        Class<?> targetActivity = "admin".equalsIgnoreCase(role) ? AdminDashboardActivity.class : DashboardActivity.class;

        Intent intent = new Intent(context, targetActivity);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nav_history)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        try {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        } catch (SecurityException e) {
            android.util.Log.e("AgriFlow", "Notification permission missing", e);
        }
    }

    public void evaluateWaterLevel(double percentage, String farmLocation) {
        SensorAlertManager.getInstance(context).evaluateWaterLevel(percentage, farmLocation);
    }

    public void evaluateLightIntensity(double percentage, String farmLocation) {
        SensorAlertManager.getInstance(context).evaluateLightIntensity(percentage, farmLocation);
    }
}
