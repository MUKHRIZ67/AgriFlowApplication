package com.example.agriflowapplication;

import com.google.firebase.Timestamp;

public class HistoryRecord {
    private String sensorType; // e.g., "moisture", "temperature", "humidity", "light"
    private double value;
    private String unit;
    private String zone;
    private String deviceId;
    private Timestamp timestamp;

    public HistoryRecord() {} // Required for Firestore

    public HistoryRecord(String sensorType, double value, String unit, String zone, String deviceId, Timestamp timestamp) {
        this.sensorType = sensorType;
        this.value = value;
        this.unit = unit;
        this.zone = zone;
        this.deviceId = deviceId;
        this.timestamp = timestamp;
    }

    // Support legacy field for migration/backward compatibility if needed
    public double getMoisture() { return value; }
    public void setMoisture(double moisture) { this.value = moisture; }

    public String getSensorType() { return sensorType; }
    public void setSensorType(String sensorType) { this.sensorType = sensorType; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }
}
