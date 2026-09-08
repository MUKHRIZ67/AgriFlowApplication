package com.example.agriflowapplication;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

public class SensorAlertManager {

    private static final String CHANNEL_ID = "agriflow_sensor_alerts";
    private static final String CHANNEL_NAME = "AgriFlow Sensor Alerts";
    private static final String CHANNEL_DESC = "Notifications for environmental sensor alerts";
    private static final String PREFS_NAME = "SensorAlertPrefs";

    public enum AlertSeverity { NORMAL, WARNING, CRITICAL }

    private static SensorAlertManager instance;
    private final Context context;
    private final SharedPreferences prefs;

    private SensorAlertManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        createNotificationChannel();
    }

    public static synchronized SensorAlertManager getInstance(Context context) {
        if (instance == null) {
            instance = new SensorAlertManager(context);
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

    public void evaluateTemperature(Double temp, String farmLocation) {
        if (temp == null || temp.isNaN()) return;
        
        AlertSeverity severity = AlertSeverity.NORMAL;
        String title = "";
        String message = "";

        if (temp < SensorAlertConstants.TEMP_CRITICAL_LOW || temp > SensorAlertConstants.TEMP_CRITICAL_HIGH) {
            severity = AlertSeverity.CRITICAL;
            title = temp < SensorAlertConstants.TEMP_CRITICAL_LOW ? "Critical Low Temperature" : "Critical High Temperature";
            message = String.format(Locale.getDefault(), "Temperature is at %.1f°C! Immediate action may be required.", temp);
        } else if (temp < SensorAlertConstants.TEMP_WARNING_LOW || temp > SensorAlertConstants.TEMP_WARNING_HIGH) {
            severity = AlertSeverity.WARNING;
            title = temp < SensorAlertConstants.TEMP_WARNING_LOW ? "Low Temperature Alert" : "High Temperature Alert";
            message = String.format(Locale.getDefault(), "Temperature has reached %.1f°C. Please check the environment.", temp);
        }

        processAlert("temperature", severity, title, message, temp, "°C", farmLocation);
    }

    public void evaluateHumidity(Double hum, String farmLocation) {
        if (hum == null || hum.isNaN() || hum < 0 || hum > 100) return;

        AlertSeverity severity = AlertSeverity.NORMAL;
        String title = "";
        String message = "";

        if (hum < SensorAlertConstants.HUMIDITY_CRITICAL_LOW || hum > SensorAlertConstants.HUMIDITY_CRITICAL_HIGH) {
            severity = AlertSeverity.CRITICAL;
            title = hum < SensorAlertConstants.HUMIDITY_CRITICAL_LOW ? "Critical Low Humidity" : "Critical High Humidity";
            message = String.format(Locale.getDefault(), "Humidity is at %.0f%%! This is outside safe limits.", hum);
        } else if (hum < SensorAlertConstants.HUMIDITY_WARNING_LOW || hum > SensorAlertConstants.HUMIDITY_WARNING_HIGH) {
            severity = AlertSeverity.WARNING;
            title = hum < SensorAlertConstants.HUMIDITY_WARNING_LOW ? "Low Humidity Alert" : "High Humidity Alert";
            message = String.format(Locale.getDefault(), "Humidity has reached %.0f%%.", hum);
        }

        processAlert("humidity", severity, title, message, hum, "%", farmLocation);
    }

    public void evaluateLightIntensity(Double light, String farmLocation) {
        if (light == null || light.isNaN() || light < 0 || light > 100) return;

        AlertSeverity severity = AlertSeverity.NORMAL;
        String title = "";
        String message = "";

        if (light > SensorAlertConstants.LIGHT_CRITICAL_HIGH) {
            severity = AlertSeverity.CRITICAL;
            title = "Extreme Light Intensity";
            message = String.format(Locale.getDefault(), "Light intensity has reached %.0f%%. Plants may be receiving excessive sunlight.", light);
        } else if (light > SensorAlertConstants.LIGHT_WARNING_HIGH) {
            severity = AlertSeverity.WARNING;
            title = "Excessive Sunlight Detected";
            message = String.format(Locale.getDefault(), "Light intensity is %.0f%%, which exceeds the safe threshold.", light);
        }

        processAlert("lightIntensity", severity, title, message, light, "%", farmLocation);
    }

    public void evaluateSoilMoisture(Double moisture, String farmLocation) {
        if (moisture == null || moisture.isNaN() || moisture < 0 || moisture > 100) return;

        AlertSeverity severity = AlertSeverity.NORMAL;
        String title = "";
        String message = "";

        if (moisture < SensorAlertConstants.SOIL_CRITICAL_LOW) {
            severity = AlertSeverity.CRITICAL;
            title = "Critical Soil Moisture";
            message = String.format(Locale.getDefault(), "Soil moisture is extremely low (%.0f%%). Urgent watering required.", moisture);
        } else if (moisture < SensorAlertConstants.SOIL_WARNING_LOW) {
            severity = AlertSeverity.WARNING;
            title = "Soil Too Dry";
            message = String.format(Locale.getDefault(), "Soil moisture is currently %.0f%%. Watering may be required soon.", moisture);
        }

        processAlert("soilMoisture", severity, title, message, moisture, "%", farmLocation);
    }

    public void evaluateWaterLevel(Double tank, String farmLocation) {
        if (tank == null || tank.isNaN() || tank < 0 || tank > 100) return;

        AlertSeverity severity = AlertSeverity.NORMAL;
        String title = "";
        String message = "";

        if (tank < SensorAlertConstants.WATER_CRITICAL_LOW) {
            severity = AlertSeverity.CRITICAL;
            title = "Critical Water Level";
            message = String.format(Locale.getDefault(), "Water tank level is currently %.0f%%. Please refill the tank immediately.", tank);
        } else if (tank < SensorAlertConstants.WATER_WARNING_LOW) {
            severity = AlertSeverity.WARNING;
            title = "Low Water Level";
            message = String.format(Locale.getDefault(), "Water tank level is at %.0f%%. Please prepare to refill.", tank);
        }

        processAlert("waterTankLevel", severity, title, message, tank, "%", farmLocation);
    }

    private void processAlert(String sensorKey, AlertSeverity severity, String title, String message, double value, String unit, String farmLocation) {
        String prevSeverityStr = prefs.getString(sensorKey + "_severity", AlertSeverity.NORMAL.name());
        AlertSeverity prevSeverity = AlertSeverity.valueOf(prevSeverityStr);
        long lastNotifyTime = prefs.getLong(sensorKey + "_last_notify", 0);
        long currentTime = System.currentTimeMillis();

        if (severity == AlertSeverity.NORMAL) {
            // Reset alert state if it returns to normal
            if (prevSeverity != AlertSeverity.NORMAL) {
                prefs.edit().putString(sensorKey + "_severity", AlertSeverity.NORMAL.name()).apply();
            }
            return;
        }

        boolean shouldNotify = false;

        // 1. Alert Escalation: NORMAL -> WARNING, NORMAL -> CRITICAL, or WARNING -> CRITICAL
        if (severity.ordinal() > prevSeverity.ordinal()) {
            shouldNotify = true;
        } 
        // 2. Cooldown check for same severity
        else if (severity == prevSeverity) {
            if (currentTime - lastNotifyTime >= SensorAlertConstants.NOTIFICATION_COOLDOWN_MS) {
                shouldNotify = true;
            }
        }

        if (shouldNotify) {
            sendNotification(title, message, value, severity, sensorKey, farmLocation);

            prefs.edit()
                    .putString(sensorKey + "_severity", severity.name())
                    .putLong(sensorKey + "_last_notify", currentTime)
                    .apply();
        }
    }

    private void sendNotification(String title, String message, double value, AlertSeverity severity, String sensorKey, String farmLocation) {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) return;

        // 1. Persist to Firestore
        NotificationRecord record = new NotificationRecord(
                title, 
                message, 
                value, 
                severity.name().toLowerCase(), 
                sensorKey, 
                value, 
                0.0, // Threshold not used here, we use constants
                Timestamp.now(), 
                userId, 
                farmLocation
        );
        FirebaseFirestore.getInstance().collection("notifications").add(record);

        // 2. Show Push Notification
        Intent intent = new Intent(context, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        int priority = (severity == AlertSeverity.CRITICAL) ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nav_history)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(priority)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        try {
            notificationManager.notify(sensorKey.hashCode(), builder.build());
        } catch (SecurityException e) {
            android.util.Log.e("AgriFlow", "Notification permission missing", e);
        }
    }
}
