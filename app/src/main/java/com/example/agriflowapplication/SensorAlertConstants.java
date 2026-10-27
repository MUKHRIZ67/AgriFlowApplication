package com.example.agriflowapplication;

public class SensorAlertConstants {
    // TEMPERATURE
    public static final double TEMP_CRITICAL_LOW = 15.0;
    public static final double TEMP_WARNING_LOW = 18.0;
    public static final double TEMP_WARNING_HIGH = 32.0;
    public static final double TEMP_CRITICAL_HIGH = 38.0;

    // HUMIDITY
    public static final double HUMIDITY_CRITICAL_LOW = 30.0;
    public static final double HUMIDITY_WARNING_LOW = 40.0;
    public static final double HUMIDITY_WARNING_HIGH = 75.0;
    public static final double HUMIDITY_CRITICAL_HIGH = 85.0;

    // LIGHT INTENSITY
    public static final double LIGHT_WARNING_HIGH = 85.0;
    public static final double LIGHT_CRITICAL_HIGH = 95.0;
    public static final double DEFAULT_LIGHT_THRESHOLD = 70.0;

    // SOIL MOISTURE
    public static final double SOIL_CRITICAL_LOW = 25.0;
    public static final double SOIL_WARNING_LOW = 40.0;

    // WATER TANK LEVEL
    public static final double WATER_CRITICAL_LOW = 20.0;
    public static final double WATER_WARNING_LOW = 40.0;
    
    // NOTIFICATION COOLDOWN (in milliseconds)
    public static final long NOTIFICATION_COOLDOWN_MS = 30 * 60 * 1000; // 30 minutes
}
