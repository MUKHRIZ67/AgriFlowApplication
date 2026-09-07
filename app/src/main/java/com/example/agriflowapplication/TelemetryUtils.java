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

    public static final double WATER_LOW_THRESHOLD = 40.0;
    private static final double WATER_CRITICAL_THRESHOLD = 20.0;
    public static final double SUNLIGHT_EXCESSIVE_THRESHOLD = 80.0;

    /**
     * Thresholds for Water Level Classification:
     * 0%–20%: Critical Low (Red)
     * 21%–40%: Low / Refill Recommended (Amber/Orange)
     * 41%–85%: Normal / Optimal (Green)
     * 86%–100%: High / Nearly Full (Blue)
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
        } else if (p <= 85) {
            return new StatusInfo("Normal / Optimal", Color.parseColor("#43A047")); // Green
        } else {
            return new StatusInfo("High / Nearly Full", Color.parseColor("#1E88E5")); // Blue
        }
    }
}
