package com.example.agriflowapplication;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class AgriFlowAiService {

    private static final String TAG = "AgriFlow_AI";

    // Backend endpoint URL deployed on Render cloud
    private static final String BACKEND_CHAT_URL = "https://agriflow-backend-nu0a.onrender.com/api/chat";

    public interface AiCallback {
        void onSuccess(String response);
        void onError(String errorMessage);
    }

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final Handler mainHandler;

    public AgriFlowAiService() {
        this.db = FirebaseManager.getInstance().getDb();
        this.auth = FirebaseManager.getInstance().getAuth();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void generateResponse(Context context, String userQuery, AiCallback callback) {
        if (auth.getCurrentUser() == null) {
            callback.onError("User not authenticated.");
            return;
        }

        // Gather real farm data context to enrich Gemini prompt
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
                    Log.w(TAG, "Could not fetch live telemetry context, proceeding with query only.", e);
                    sendRequestToBackendServer(userQuery, "", callback);
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

                                String farmContext = buildFarmContextString(telemetryDoc, controlDoc, waterSchedDoc, fertSchedDoc, recentAlerts, historyItems);
                                sendRequestToBackendServer(userQuery, farmContext, callback);
                            })
                            .addOnFailureListener(e -> {
                                String farmContext = buildFarmContextString(telemetryDoc, controlDoc, waterSchedDoc, fertSchedDoc, recentAlerts, new ArrayList<>());
                                sendRequestToBackendServer(userQuery, farmContext, callback);
                            });
                })
                .addOnFailureListener(e -> {
                    String farmContext = buildFarmContextString(telemetryDoc, controlDoc, waterSchedDoc, fertSchedDoc, new ArrayList<>(), new ArrayList<>());
                    sendRequestToBackendServer(userQuery, farmContext, callback);
                });
    }

    private String buildFarmContextString(DocumentSnapshot telemetry,
                                          DocumentSnapshot control,
                                          DocumentSnapshot waterSched,
                                          DocumentSnapshot fertSched,
                                          List<String> alerts,
                                          List<String> history) {
        StringBuilder context = new StringBuilder();

        Double temp = getDouble(telemetry, "temperature", 25.0);
        Double hum = getDouble(telemetry, "humidity", 60.0);
        Double light = getDouble(telemetry, "lightIntensity");
        if (light == null) light = getDouble(telemetry, "lux");
        if (light == null) light = getDouble(telemetry, "light_density", 50.0);

        Double moisture = getDouble(telemetry, "soilMoisture", 45.0);
        Double tank = getDouble(telemetry, "waterTankLevel");
        if (tank == null) tank = getDouble(telemetry, "water_level", 50.0);

        String esp32Status = telemetry != null ? telemetry.getString("esp32_status") : "Online";

        String waterPumpState = control != null ? control.getString("waterPumpState") : "OFF";
        String fertPumpState = control != null ? control.getString("fertilizerPumpState") : "OFF";

        context.append("LIVE FARM TELEMETRY:\n");
        context.append("- Temperature: ").append(String.format(Locale.getDefault(), "%.1f°C", temp)).append("\n");
        context.append("- Humidity: ").append(String.format(Locale.getDefault(), "%.0f%%", hum)).append("\n");
        context.append("- Light Intensity: ").append(String.format(Locale.getDefault(), "%.0f%%", light)).append("\n");
        context.append("- Soil Moisture: ").append(String.format(Locale.getDefault(), "%.0f%%", moisture)).append(" (Dry threshold: 40%)\n");
        context.append("- Water Tank Level: ").append(String.format(Locale.getDefault(), "%.0f%%", tank)).append(" (Low threshold: 20%)\n");
        context.append("- ESP32 Connection: ").append(esp32Status != null ? esp32Status : "Online").append("\n");

        context.append("\nPUMP CONTROLS:\n");
        context.append("- Water Pump: ").append(waterPumpState != null ? waterPumpState : "OFF").append("\n");
        context.append("- Fertilizer Pump: ").append(fertPumpState != null ? fertPumpState : "OFF").append("\n");

        if (waterSched != null) {
            boolean enabled = Boolean.TRUE.equals(waterSched.getBoolean("enabled"));
            context.append("\nWATER SCHEDULE: ").append(enabled ? "Enabled" : "Disabled")
                   .append(", Start: ").append(waterSched.getString("startTime"))
                   .append(", Duration: ").append(waterSched.getLong("durationMinutes")).append(" min\n");
        }

        if (fertSched != null) {
            boolean enabled = Boolean.TRUE.equals(fertSched.getBoolean("enabled"));
            context.append("FERTILIZER SCHEDULE: ").append(enabled ? "Enabled" : "Disabled")
                   .append(", Start: ").append(fertSched.getString("startTime"))
                   .append(", Duration: ").append(fertSched.getLong("durationSeconds")).append(" sec\n");
        }

        if (!alerts.isEmpty()) {
            context.append("\nRECENT ALERTS:\n");
            for (String alert : alerts) {
                context.append("- ").append(alert).append("\n");
            }
        }

        return context.toString();
    }

    private void sendRequestToBackendServer(String userQuery, String farmContext, AiCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BACKEND_CHAT_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(20000);

                JSONObject jsonBody = new JSONObject();
                jsonParamPut(jsonBody, "message", userQuery);
                if (farmContext != null && !farmContext.trim().isEmpty()) {
                    jsonParamPut(jsonBody, "context", farmContext);
                }

                byte[] input = jsonBody.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                InputStream is = (responseCode >= 200 && responseCode < 300) 
                        ? conn.getInputStream() 
                        : conn.getErrorStream();

                StringBuilder responseStr = new StringBuilder();
                if (is != null) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            responseStr.append(line.trim());
                        }
                    }
                }

                JSONObject jsonResponse = new JSONObject(responseStr.toString());
                String reply = jsonResponse.optString("reply", "");
                String error = jsonResponse.optString("error", "");

                mainHandler.post(() -> {
                    if (!reply.isEmpty()) {
                        callback.onSuccess(reply);
                    } else if (!error.isEmpty()) {
                        callback.onError("AI Server Error: " + error);
                    } else {
                        callback.onError("Received invalid or empty response from AI backend server.");
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Failed to connect to AI backend server", e);
                mainHandler.post(() -> {
                    callback.onError("Unable to connect to AgriFlow AI backend server. Please ensure the server is running on " + BACKEND_CHAT_URL);
                });
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private void jsonParamPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (Exception e) {
            Log.e(TAG, "Error putting JSON key: " + key, e);
        }
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
