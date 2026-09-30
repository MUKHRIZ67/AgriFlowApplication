package com.example.agriflowapplication;

import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AgriFlowAiService {

    private static final String TAG = "AgriFlow_AI";

    public interface AiCallback {
        void onSuccess(String response);
        void onError(String errorMessage);
    }

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public AgriFlowAiService() {
        this.db = FirebaseManager.getInstance().getDb();
        this.auth = FirebaseManager.getInstance().getAuth();
    }

    public void generateResponse(Context context, String userQuery, AiCallback callback) {
        if (auth.getCurrentUser() == null) {
            callback.onError("User not authenticated.");
            return;
        }

        // Gather real farm data context
        db.collection("system_data").document("live_telemetry").get()
                .addOnSuccessListener(telemetryDoc -> {
                    db.collection("system_data").document("device_control").get()
                            .addOnSuccessListener(controlDoc -> {
                                db.collection("schedules").document("water").get()
                                        .addOnSuccessListener(waterSchedDoc -> {
                                            db.collection("schedules").document("fertilizer").get()
                                                    .addOnSuccessListener(fertSchedDoc -> {
                                                        fetchHistoryAndNotifications(telemetryDoc, controlDoc, waterSchedDoc, fertSchedDoc, userQuery, callback);
                                                    })
                                                    .addOnFailureListener(e -> fetchHistoryAndNotifications(telemetryDoc, controlDoc, waterSchedDoc, null, userQuery, callback));
                                        })
                                        .addOnFailureListener(e -> fetchHistoryAndNotifications(telemetryDoc, controlDoc, null, null, userQuery, callback));
                            })
                            .addOnFailureListener(e -> fetchHistoryAndNotifications(telemetryDoc, null, null, null, userQuery, callback));
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching live telemetry", e);
                    callback.onError("AI Assistant is currently unavailable. Your sensor monitoring and irrigation controls are still working normally.");
                });
    }

    private void fetchHistoryAndNotifications(DocumentSnapshot telemetryDoc,
                                                DocumentSnapshot controlDoc,
                                                DocumentSnapshot waterSchedDoc,
                                                DocumentSnapshot fertSchedDoc,
                                                String userQuery,
                                                AiCallback callback) {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";

        db.collection("notifications")
                .whereEqualTo("userId", uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(5)
                .get()
                .addOnSuccessListener(notifSnapshots -> {
                    List<String> recentAlerts = new ArrayList<>();
                    for (DocumentSnapshot doc : notifSnapshots.getDocuments()) {
                        String title = doc.getString("title");
                        String msg = doc.getString("message");
                        if (title != null && msg != null) {
                            recentAlerts.add(title + ": " + msg);
                        }
                    }

                    db.collection("History")
                            .orderBy("timestamp", Query.Direction.DESCENDING)
                            .limit(10)
                            .get()
                            .addOnSuccessListener(historySnapshots -> {
                                List<String> historyItems = new ArrayList<>();
                                for (DocumentSnapshot doc : historySnapshots.getDocuments()) {
                                    String type = doc.getString("type");
                                    Double val = doc.getDouble("value");
                                    String unit = doc.getString("unit");
                                    if (type != null && val != null) {
                                        historyItems.add(type + " = " + String.format(Locale.getDefault(), "%.1f", val) + (unit != null ? unit : ""));
                                    }
                                }

                                processAiQuery(telemetryDoc, controlDoc, waterSchedDoc, fertSchedDoc, recentAlerts, historyItems, userQuery, callback);
                            })
                            .addOnFailureListener(e -> processAiQuery(telemetryDoc, controlDoc, waterSchedDoc, fertSchedDoc, recentAlerts, new ArrayList<>(), userQuery, callback));
                })
                .addOnFailureListener(e -> processAiQuery(telemetryDoc, controlDoc, waterSchedDoc, fertSchedDoc, new ArrayList<>(), new ArrayList<>(), userQuery, callback));
    }

    private void processAiQuery(DocumentSnapshot telemetry,
                                DocumentSnapshot control,
                                DocumentSnapshot waterSched,
                                DocumentSnapshot fertSched,
                                List<String> alerts,
                                List<String> history,
                                String userQuery,
                                AiCallback callback) {

        // Extract Telemetry
        Double temp = getDouble(telemetry, "temperature", 25.0);
        Double hum = getDouble(telemetry, "humidity", 60.0);
        Double light = getDouble(telemetry, "lightIntensity");
        if (light == null) light = getDouble(telemetry, "lux");
        if (light == null) light = getDouble(telemetry, "light_density", 50.0);

        Double moisture = getDouble(telemetry, "soilMoisture", 45.0);
        Double tank = getDouble(telemetry, "waterTankLevel");
        if (tank == null) tank = getDouble(telemetry, "water_level", 50.0);

        String esp32Status = telemetry != null ? telemetry.getString("esp32_status") : "Online";
        if (esp32Status == null) esp32Status = "Online";

        // Extract Control
        String waterPumpState = control != null ? control.getString("waterPumpState") : "OFF";
        if (waterPumpState == null) waterPumpState = "OFF";

        String fertPumpState = control != null ? control.getString("fertilizerPumpState") : "OFF";
        if (fertPumpState == null) fertPumpState = "OFF";

        // Extract Schedules
        boolean waterSchedEnabled = waterSched != null && Boolean.TRUE.equals(waterSched.getBoolean("enabled"));
        String waterTime = waterSched != null ? waterSched.getString("startTime") : "08:00 AM";
        Long waterDuration = waterSched != null ? waterSched.getLong("durationMinutes") : 5L;

        boolean fertSchedEnabled = fertSched != null && Boolean.TRUE.equals(fertSched.getBoolean("enabled"));
        String fertTime = fertSched != null ? fertSched.getString("startTime") : "08:30 AM";
        Long fertDuration = fertSched != null ? fertSched.getLong("durationSeconds") : 30L;
        String fertRepeat = fertSched != null ? fertSched.getString("repeatType") : "Every 7 Days";

        String queryLower = userQuery.toLowerCase().trim();

        // Response Generation
        StringBuilder reply = new StringBuilder();

        if (queryLower.contains("analyze my farm") || queryLower.contains("farm summary") || queryLower.contains("summarize today") || queryLower.contains("how is my farm")) {
            reply.append("🌱 **AgriFlow Farm Health Analysis**\n\n");
            
            // Temperature status
            String tempStatus = (temp < SensorAlertConstants.TEMP_WARNING_LOW) ? "Low" : (temp > SensorAlertConstants.TEMP_WARNING_HIGH) ? "High" : "Normal";
            reply.append("• **Temperature**: ").append(String.format(Locale.getDefault(), "%.1f°C (%s)\n", temp, tempStatus));

            // Humidity status
            String humStatus = (hum < SensorAlertConstants.HUMIDITY_WARNING_LOW) ? "Low" : (hum > SensorAlertConstants.HUMIDITY_WARNING_HIGH) ? "High" : "Normal";
            reply.append("• **Humidity**: ").append(String.format(Locale.getDefault(), "%.0f%% (%s)\n", hum, humStatus));

            // Soil Moisture status
            String soilStatus = (moisture < SensorAlertConstants.SOIL_CRITICAL_LOW) ? "Critical Dry" : (moisture < SensorAlertConstants.SOIL_WARNING_LOW) ? "Dry" : "Optimal";
            reply.append("• **Soil Moisture**: ").append(String.format(Locale.getDefault(), "%.0f%% (%s)\n", moisture, soilStatus));

            // Light status
            String lightStatus = (light > SensorAlertConstants.LIGHT_WARNING_HIGH) ? "Excessive" : "Normal";
            reply.append("• **Sunlight/Light**: ").append(String.format(Locale.getDefault(), "%.0f%% (%s)\n", light, lightStatus));

            // Water Tank status
            String tankStatus = (tank < SensorAlertConstants.WATER_CRITICAL_LOW) ? "Critical Low" : (tank < SensorAlertConstants.WATER_WARNING_LOW) ? "Low" : "Adequate";
            reply.append("• **Water Tank Level**: ").append(String.format(Locale.getDefault(), "%.0f%% (%s)\n\n", tank, tankStatus));

            reply.append("📌 **Overall Assessment**:\n");
            if (moisture < SensorAlertConstants.SOIL_WARNING_LOW) {
                if (tank >= SensorAlertConstants.WATER_WARNING_LOW) {
                    reply.append("Your soil moisture (").append(String.format(Locale.getDefault(), "%.0f%%", moisture))
                            .append(") is below your configured dry threshold (40%). Your water tank has sufficient water (")
                            .append(String.format(Locale.getDefault(), "%.0f%%", tank)).append("). Irrigation may be appropriate.");
                } else {
                    reply.append("Soil moisture is low (").append(String.format(Locale.getDefault(), "%.0f%%", moisture))
                            .append("), but water tank level is also low (").append(String.format(Locale.getDefault(), "%.0f%%", tank))
                            .append("). Refill the tank before starting irrigation.");
                }
            } else {
                reply.append("All environmental parameters are within safe ranges. Your farm conditions are stable.");
            }

        } else if (queryLower.contains("soil") || queryLower.contains("check soil") || queryLower.contains("dry")) {
            reply.append("🌾 **Soil Moisture Status**\n\n");
            reply.append("• **Current Reading**: ").append(String.format(Locale.getDefault(), "%.0f%%\n", moisture));
            reply.append("• **Configured Dry Threshold**: ").append(String.format(Locale.getDefault(), "%.0f%%\n", SensorAlertConstants.SOIL_WARNING_LOW));
            reply.append("• **Configured Critical Dry Threshold**: ").append(String.format(Locale.getDefault(), "%.0f%%\n\n", SensorAlertConstants.SOIL_CRITICAL_LOW));

            if (moisture < SensorAlertConstants.SOIL_CRITICAL_LOW) {
                reply.append("⚠️ **Critical Dry Soil**: Moisture is severely low. Water irrigation is recommended if water supply is adequate.");
            } else if (moisture < SensorAlertConstants.SOIL_WARNING_LOW) {
                reply.append("⚠️ **Dry Soil Alert**: Moisture is below the 40% threshold. You may want to start water irrigation soon.");
            } else {
                reply.append("✅ **Optimal**: Soil moisture is healthy and above configured threshold limits.");
            }

        } else if (queryLower.contains("water tank") || queryLower.contains("tank") || queryLower.contains("water level")) {
            reply.append("💧 **Water Tank Level Analysis**\n\n");
            reply.append("• **Current Tank Level**: ").append(String.format(Locale.getDefault(), "%.0f%%\n", tank));
            reply.append("• **Low Water Threshold**: ").append(String.format(Locale.getDefault(), "%.0f%%\n", SensorAlertConstants.WATER_WARNING_LOW));
            reply.append("• **Critical Tank Threshold**: ").append(String.format(Locale.getDefault(), "%.0f%%\n\n", SensorAlertConstants.WATER_CRITICAL_LOW));

            if (tank < SensorAlertConstants.WATER_CRITICAL_LOW) {
                reply.append("🔴 **Critical Low**: Water level is critically low. Refill tank immediately before running water or fertilizer pumps.");
            } else if (tank < SensorAlertConstants.WATER_WARNING_LOW) {
                reply.append("🟡 **Low Tank Level**: Tank is below 40%. Prepare to refill soon.");
            } else {
                reply.append("🟢 **Adequate Supply**: Tank level is sufficient for planned irrigation.");
            }

        } else if (queryLower.contains("irrigate") || queryLower.contains("water my crops") || queryLower.contains("check irrigation") || queryLower.contains("should i water")) {
            reply.append("🚿 **Irrigation Recommendation**\n\n");
            reply.append("• **Soil Moisture**: ").append(String.format(Locale.getDefault(), "%.0f%% (Threshold: 40%%)\n", moisture));
            reply.append("• **Water Tank**: ").append(String.format(Locale.getDefault(), "%.0f%% (Threshold: 20%%)\n", tank));
            reply.append("• **Water Pump State**: ").append(waterPumpState).append("\n\n");

            if ("ON".equalsIgnoreCase(waterPumpState) || "RUNNING".equalsIgnoreCase(waterPumpState)) {
                reply.append("Water irrigation is **currently RUNNING**. No further manual action is required.");
            } else if (moisture < SensorAlertConstants.SOIL_WARNING_LOW) {
                if (tank >= SensorAlertConstants.WATER_CRITICAL_LOW) {
                    reply.append("💡 **Recommendation**: Soil moisture is low (").append(String.format(Locale.getDefault(), "%.0f%%", moisture))
                            .append(") and water tank level is adequate (").append(String.format(Locale.getDefault(), "%.0f%%", tank))
                            .append("). Starting water irrigation may be appropriate.\n\n")
                            .append("*Note: To activate irrigation, use the START button in Irrigation Controls.*");
                } else {
                    reply.append("⚠️ **Attention**: Soil moisture is low, but water tank is also low (").append(String.format(Locale.getDefault(), "%.0f%%", tank))
                            .append("). Refill the tank before activating the water pump.");
                }
            } else {
                reply.append("✅ Soil moisture is currently adequate (").append(String.format(Locale.getDefault(), "%.0f%%", moisture))
                        .append("). Immediate irrigation is not needed at this time.");
            }

        } else if (queryLower.contains("fertilizer") || queryLower.contains("check fertilizer")) {
            reply.append("🧪 **Fertilizer Schedule & Status**\n\n");
            reply.append("• **Schedule Enabled**: ").append(fertSchedEnabled ? "Yes" : "No").append("\n");
            reply.append("• **Start Time**: ").append(fertTime).append("\n");
            reply.append("• **Duration**: ").append(fertDuration).append(" seconds\n");
            reply.append("• **Repeat Frequency**: ").append(fertRepeat).append("\n");
            reply.append("• **Current Fertilizer Pump State**: ").append(fertPumpState).append("\n\n");

            if (fertSchedEnabled) {
                reply.append("Your fertilizer schedule is active. Fertilizer distribution will run automatically at ").append(fertTime).append(" for ").append(fertDuration).append(" seconds (").append(fertRepeat).append(").");
            } else {
                reply.append("Your fertilizer schedule is currently disabled. You can configure and enable it in the Schedule screen.");
            }

        } else if (queryLower.contains("alert") || queryLower.contains("explain my alerts")) {
            reply.append("🔔 **Recent Sensor Alerts Explanation**\n\n");
            if (alerts.isEmpty()) {
                reply.append("No active or recent alerts found for your farm. All readings are currently within safe threshold ranges.");
            } else {
                for (String alert : alerts) {
                    reply.append("• ").append(alert).append("\n");
                }
                reply.append("\n*Alerts are triggered automatically when live telemetry drops below or exceeds your configured safety thresholds.*");
            }

        } else if (queryLower.contains("environment") || queryLower.contains("humidity") || queryLower.contains("check environment") || queryLower.contains("hot")) {
            reply.append("🌡️ **Environmental Conditions**\n\n");
            reply.append("• **Temperature**: ").append(String.format(Locale.getDefault(), "%.1f°C (Normal: 18°C - 32°C)\n", temp));
            reply.append("• **Humidity**: ").append(String.format(Locale.getDefault(), "%.0f%% (Normal: 40%% - 75%%)\n", hum));
            reply.append("• **Light Intensity**: ").append(String.format(Locale.getDefault(), "%.0f%% (Max Threshold: 85%%)\n\n", light));

            if (temp > SensorAlertConstants.TEMP_WARNING_HIGH) {
                reply.append("High temperature detected. Ensure shading or adequate crop hydration is maintained.");
            } else if (hum > SensorAlertConstants.HUMIDITY_WARNING_HIGH) {
                reply.append("Humidity is elevated. High humidity can slow down transpiration but reduces crop water loss.");
            } else {
                reply.append("Environmental temperature and humidity are currently balanced.");
            }

        } else {
            // General / Structured Response
            reply.append("📊 **AgriFlow Farm Data Brief**\n\n");
            reply.append("• **Soil Moisture**: ").append(String.format(Locale.getDefault(), "%.0f%%", moisture)).append(" (Dry threshold: 40%)\n");
            reply.append("• **Water Tank**: ").append(String.format(Locale.getDefault(), "%.0f%%", tank)).append(" (Low threshold: 40%)\n");
            reply.append("• **Temperature**: ").append(String.format(Locale.getDefault(), "%.1f°C", temp)).append("\n");
            reply.append("• **Humidity**: ").append(String.format(Locale.getDefault(), "%.0f%%", hum)).append("\n");
            reply.append("• **ESP32 Connection**: ").append(esp32Status).append("\n\n");

            if (moisture < SensorAlertConstants.SOIL_WARNING_LOW) {
                reply.append("Soil moisture is below threshold. Manual or scheduled irrigation may be appropriate if water tank level allows.");
            } else {
                reply.append("Farm parameters appear normal based on your live sensor telemetry.");
            }
        }

        callback.onSuccess(reply.toString());
    }

    private Double getDouble(DocumentSnapshot doc, String field, double defaultValue) {
        if (doc == null) return defaultValue;
        Double val = doc.getDouble(field);
        return val != null ? val : defaultValue;
    }

    private Double getDouble(DocumentSnapshot doc, String field) {
        if (doc == null) return null;
        return doc.getDouble(field);
    }
}
