package com.example.agriflowapplication;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class AppNotificationManager {

    private static final String CHANNEL_ID = "water_level_alerts";
    private static final String CHANNEL_NAME = "Water Level Alerts";
    private static final String CHANNEL_DESC = "Notifications for low and critical water levels";

    private static AppNotificationManager instance;
    private final Context context;
    
    // Notification state to prevent spam
    private enum WaterState { NORMAL, LOW, CRITICAL }
    private enum LightState { NORMAL, EXCESSIVE }
    
    private WaterState currentWaterState = WaterState.NORMAL;
    private LightState currentLightState = LightState.NORMAL;

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(CHANNEL_DESC);
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public void evaluateWaterLevel(double percentage, String farmLocation) {
        if (percentage > TelemetryUtils.WATER_LOW_THRESHOLD) {
            currentWaterState = WaterState.NORMAL;
            return;
        }

        if (percentage <= 20 && currentWaterState != WaterState.CRITICAL) {
            // Trigger Critical Alert
            currentWaterState = WaterState.CRITICAL;
            sendNotification("Critical Water Level", 
                             String.format(java.util.Locale.getDefault(), "Critical water level: The tank is at %.1f%%. Please refill immediately.", percentage),
                             percentage, "critical", "water", 20.0, farmLocation);
        } else if (percentage <= 40 && currentWaterState == WaterState.NORMAL) {
            // Trigger Low Alert (only if coming from Normal)
            currentWaterState = WaterState.LOW;
            sendNotification("Low Water Level", 
                             String.format(java.util.Locale.getDefault(), "Low water level: The tank is at %.1f%%. Please prepare to refill the tank.", percentage),
                             percentage, "low", "water", 40.0, farmLocation);
        }
    }

    public void evaluateLightIntensity(double percentage, String farmLocation) {
        if (percentage <= TelemetryUtils.SUNLIGHT_EXCESSIVE_THRESHOLD) {
            currentLightState = LightState.NORMAL;
            return;
        }

        if (percentage > TelemetryUtils.SUNLIGHT_EXCESSIVE_THRESHOLD && currentLightState != LightState.EXCESSIVE) {
            currentLightState = LightState.EXCESSIVE;
            sendNotification("Excessive Sunlight Detected",
                             String.format(java.util.Locale.getDefault(), "Light intensity is %.1f%%, which exceeds the safe threshold of %.0f%%. Please check crop shading.", percentage, TelemetryUtils.SUNLIGHT_EXCESSIVE_THRESHOLD),
                             percentage, "excessive", "sunlight", TelemetryUtils.SUNLIGHT_EXCESSIVE_THRESHOLD, farmLocation);
        }
    }

    private void sendNotification(String title, String message, double percentage, String status, String alertType, double threshold, String farmLocation) {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) return;

        // 1. Persist to Firestore
        NotificationRecord record = new NotificationRecord(title, message, percentage, status, alertType, percentage, threshold, Timestamp.now(), userId, farmLocation);
        FirebaseFirestore.getInstance().collection("notifications").add(record);

        // 2. Show Push Notification
        Intent intent = new Intent(context, DashboardActivity.class);
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
}
