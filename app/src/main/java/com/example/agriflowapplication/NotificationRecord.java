package com.example.agriflowapplication;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

public class NotificationRecord {
    private String id;
    private String title;
    private String message;
    private double percentage;
    private String status; // "low", "critical", or "excessive"
    private String alertType; // "water" or "sunlight"
    private double sensorValue;
    private double threshold;
    private Timestamp timestamp;
    private boolean isRead;
    private String userId;
    private String farmLocation;

    public NotificationRecord() {} // Required for Firestore

    public NotificationRecord(String title, String message, double percentage, String status, String alertType, double sensorValue, double threshold, Timestamp timestamp, String userId, String farmLocation) {
        this.title = title;
        this.message = message;
        this.percentage = percentage;
        this.status = status;
        this.alertType = alertType;
        this.sensorValue = sensorValue;
        this.threshold = threshold;
        this.timestamp = timestamp;
        this.userId = userId;
        this.farmLocation = farmLocation;
        this.isRead = false;
    }

    @Exclude
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public double getPercentage() { return percentage; }
    public void setPercentage(double percentage) { this.percentage = percentage; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }

    public double getSensorValue() { return sensorValue; }
    public void setSensorValue(double sensorValue) { this.sensorValue = sensorValue; }

    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }

    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }


    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getFarmLocation() { return farmLocation; }
    public void setFarmLocation(String farmLocation) { this.farmLocation = farmLocation; }
}
