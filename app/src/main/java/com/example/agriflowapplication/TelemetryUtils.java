package com.example.agriflowapplication;

import android.graphics.Color;

public class TelemetryUtils {

    public static class StatusInfo {
        public final String label;
        public final int color;

        public StatusInfo(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    public static final double WATER_LOW_THRESHOLD = SensorAlertConstants.WATER_WARNING_LOW;
    private static final double WATER_CRITICAL_THRESHOLD = SensorAlertConstants.WATER_CRITICAL_LOW;
    public static final double SUNLIGHT_EXCESSIVE_THRESHOLD = SensorAlertConstants.LIGHT_WARNING_HIGH;

    /**
     * Thresholds for Water Level Classification:
     * Critical Low: <= 20%
     * Low: <= 40%
     * Normal: > 40%
     */
    public static StatusInfo getWaterLevelClassification(double percentage) {
        if (percentage < 0) {
            return new StatusInfo("Live water-level data is unavailable", Color.GRAY);
        }
        
        // Clamp percentage for classification
        double p = Math.max(0, Math.min(100, percentage));

        if (p <= WATER_CRITICAL_THRESHOLD) {
            return new StatusInfo("Critical Low", Color.parseColor("#E53935")); // Red
        } else if (p <= WATER_LOW_THRESHOLD) {
            return new StatusInfo("Low / Refill Recommended", Color.parseColor("#FB8C00")); // Orange/Amber
        } else {
            return new StatusInfo("Normal / Optimal", Color.parseColor("#43A047")); // Green
        }
    }
}
